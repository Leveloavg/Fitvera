package com.example.fitvera

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitvera.databinding.PlanificacionentrenamientoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.OnMonthChangedListener
import com.prolificinteractive.materialcalendarview.spans.DotSpan
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashSet
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.abs



data class MonthlyChallenge(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val goal: Double = 0.0,
    val type: String = ""
)

data class MonthlyChallengesBundle(
    val destacado: MonthlyChallenge = MonthlyChallenge(),
    val distancia: MonthlyChallenge = MonthlyChallenge(),
    val desnivel: MonthlyChallenge = MonthlyChallenge(),
    val entrenamientos: MonthlyChallenge = MonthlyChallenge()
)
// Fin de Clases de datos

class PlanificarEntrenamiento : AppCompatActivity(), OnMonthChangedListener {

    private lateinit var binding: PlanificacionentrenamientoBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val tasksList = mutableListOf<Task>()
    private lateinit var taskAdapter: TaskAdapter

    private val trainingList = mutableListOf<Entrenamiento>()
    private lateinit var trainingAdapter: EntrenamientoAdapter

    private val taskDates = mutableSetOf<CalendarDay>()
    private val cardioDates = mutableSetOf<CalendarDay>()
    private val fuerzaDates = mutableSetOf<CalendarDay>()


    private var monthlyDistanceGoal = 100000.0 // En metros
    private var monthlyTrainingGoal = 20.0

    // Formato de fecha para Entrenamientos (dd-MM-yyyy)
    // Formato REAL en Firebase
    private val dateFormatDB = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Formato de fecha para Tareas (yyyy-MM-dd)
    private val dateFormatTask = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PlanificacionentrenamientoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 🚀 NUEVO: Llama a la función de limpieza al iniciar la actividad
        cleanupPastTasks()

        setupRecyclerView()
        setupCalendar()
        setupListeners()

        val today = CalendarDay.today()
        loadMonthlyGoalsFromFirebase(today.year, today.month)
        loadDatesForMonth(today)
    }

    override fun onResume() {
        super.onResume()
        showWeeklyPerformanceSummary()
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            tasksList,
            onEditClick = { task -> showEditTaskDialog(task) },
            onDeleteClick = { task -> deleteTask(task) }
        )
        binding.recyclerViewTasks.apply {
            layoutManager = LinearLayoutManager(this@PlanificarEntrenamiento)
            adapter = taskAdapter
            isNestedScrollingEnabled = false
        }

        trainingAdapter = EntrenamientoAdapter(trainingList) { entrenamiento ->
            navigateToEntrenamientoDetalles(entrenamiento)
        }
        binding.recyclerViewTrainings.apply {
            layoutManager = LinearLayoutManager(this@PlanificarEntrenamiento)
            adapter = trainingAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDateChangedListener { _, date, selected ->
            if (selected) {
                val selectedDate = String.format(Locale.US, "%d-%02d-%02d", date.year, date.month, date.day)
                loadItemsForDate(selectedDate)
            }
        }
        binding.calendarView.setOnMonthChangedListener(this)

        val today = CalendarDay.today()
        val todayString = String.format(Locale.US, "%d-%02d-%02d", today.year, today.month, today.day)

        binding.calendarView.setDateSelected(today, true)
        loadItemsForDate(todayString)
    }

    private fun setupListeners() {
        binding.floatingActionButtonAdd.setOnClickListener {
            val selectedDate = binding.calendarView.selectedDate
            if (selectedDate != null) {
                showAddTaskDialog(selectedDate)
            } else {
                Toast.makeText(this, "Selecciona una fecha primero", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    // ==========================================
    // NUEVA FUNCIÓN DE LIMPIEZA
    // ==========================================

    /**
     * Elimina permanentemente de Firestore todas las tareas cuya fecha es anterior
     * a la fecha actual. Utiliza una escritura por lotes (batch) para eficiencia.
     */
    private fun cleanupPastTasks() {
        val userId = auth.currentUser?.uid ?: return

        // Obtenemos la fecha de hoy en formato "yyyy-MM-dd".
        // Firestore puede comparar estas cadenas lexicográficamente para ordenar fechas.
        val todayDateString = dateFormatTask.format(Calendar.getInstance().time)

        db.collection("usuarios").document(userId).collection("tasks")
            // Consulta: busca todas las tareas cuya fecha es MENOR que la de hoy.
            .whereLessThan("date", todayDateString)
            .get()
            .addOnSuccessListener { querySnapshot ->

                if (querySnapshot.isEmpty) {
                    Log.d("FITVERA_CLEANUP", "No hay tareas pasadas para eliminar.")
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d("FITVERA_CLEANUP", "Tareas pasadas eliminadas: ${querySnapshot.size()}")
                        // Después de la eliminación, recargamos los datos del calendario para que desaparezcan los puntos
                        loadDatesForMonth(binding.calendarView.currentDate)

                        // Si la fecha seleccionada era una de las eliminadas, recargamos los ítems
                        val selectedDate = binding.calendarView.selectedDate
                        if (selectedDate != null) {
                            val selectedDateString = String.format(Locale.US, "%d-%02d-%02d", selectedDate.year, selectedDate.month, selectedDate.day)
                            if (selectedDateString < todayDateString) {
                                // Si el usuario estaba viendo una fecha pasada, vaciamos la lista localmente
                                tasksList.clear()
                                taskAdapter.notifyDataSetChanged()
                                updatePlaceholderVisibility()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("FITVERA_CLEANUP", "Error al eliminar lote de tareas pasadas.", e)
                        Toast.makeText(this, "Error al limpiar tareas antiguas.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("FITVERA_CLEANUP", "Error al buscar tareas pasadas.", e)
            }
    }


    // ==========================================
    // LÓGICA DE RESUMEN DE RENDIMIENTO SEMANAL (TRADUCIBLE)
    // ==========================================

    private fun showWeeklyPerformanceSummary() {
        val userId = auth.currentUser?.uid ?: return

        val now = Calendar.getInstance()
        val currentWeekStart = now.clone() as Calendar

        currentWeekStart.add(Calendar.DAY_OF_YEAR, -6)
        val currentWeekStartMillis = currentWeekStart.timeInMillis
        val currentWeekEndMillis = now.timeInMillis

        val previousWeekStart = currentWeekStart.clone() as Calendar
        previousWeekStart.add(Calendar.DAY_OF_YEAR, -7)
        val previousWeekStartMillis = previousWeekStart.timeInMillis
        val previousWeekEndMillis = currentWeekStart.timeInMillis


        var currentWeekDistance = 0.0
        var previousWeekDistance = 0.0
        var pendingTasksCount = 0

        val entrenamientosRef = db.collection("usuarios").document(userId).collection("entrenamientos")
        val tasksRef = db.collection("usuarios").document(userId).collection("tasks")

        // 1. Carga de Entrenamientos
        entrenamientosRef.get().addOnSuccessListener { trainingResult ->
            for (document in trainingResult) {
                val entrenamiento = document.toObject(Entrenamiento::class.java)
                try {
                    val calendarDay = parseTrainingDateSafe(entrenamiento.fecha) ?: continue
                    val cal = Calendar.getInstance().apply {
                        set(calendarDay.year, calendarDay.month - 1, calendarDay.day)
                    }
                    val dateMillis = cal.timeInMillis


                    when {
                        dateMillis >= currentWeekStartMillis && dateMillis <= currentWeekEndMillis -> {
                            currentWeekDistance += entrenamiento.distancia
                        }
                        dateMillis >= previousWeekStartMillis && dateMillis < previousWeekEndMillis -> {
                            previousWeekDistance += entrenamiento.distancia
                        }
                    }
                } catch (e: Exception) { Log.e("FITVERA_SUMMARY", "Error en fecha entrenamiento.", e) }
            }

            // 2. Carga de Tareas
            tasksRef.get().addOnSuccessListener { taskResult ->

                for (document in taskResult) {
                    val task = document.toObject(Task::class.java)
                    try {
                        val taskDate = dateFormatTask.parse(task.date) ?: continue
                        val taskDateMillis = taskDate.time

                        if (taskDateMillis >= currentWeekStartMillis && taskDateMillis <= currentWeekEndMillis && !task.isCompleted) {
                            pendingTasksCount++
                        }
                    } catch (e: Exception) { Log.e("FITVERA_SUMMARY", "Error en fecha tarea.", e) }
                }

                // 3. Generar el mensaje usando strings traducibles
                val message = generateSummaryMessage(currentWeekDistance, previousWeekDistance, pendingTasksCount)

                // 4. Mostrar el diálogo
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.summary_title_weekly))
                    .setMessage(message)
                    .setPositiveButton("¡Genial!") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                    .show()

            }.addOnFailureListener {
                Log.e("FITVERA_SUMMARY", "Error al cargar tareas para el resumen.", it)
            }
        }.addOnFailureListener {
            Log.e("FITVERA_SUMMARY", "Error al cargar entrenamientos para el resumen.", it)
        }
    }

    /**
     * FUNCIÓN TRADUCIBLE: Genera el mensaje de resumen utilizando recursos de string y plurales.
     */
    private fun generateSummaryMessage(currentDistance: Double, previousDistance: Double, pendingTasks: Int): String {
        val currentKm = currentDistance / 1000.0
        val previousKm = previousDistance / 1000.0

        val distanceMessage: String

        // A. Mensaje de Distancia
        distanceMessage = if (previousKm > 0.1) {
            val change = currentKm - previousKm
            val percentChange = (abs(change / previousKm) * 100).roundToInt().coerceIn(0, 999)

            when {
                change > 0.5 -> getString(R.string.summary_distance_increase, percentChange)
                change < -0.5 -> getString(R.string.summary_distance_decrease, percentChange)
                else -> getString(R.string.summary_distance_constant)
            }
        } else if (currentKm > 0.5) {
            getString(R.string.summary_distance_first_week, currentKm)
        } else {
            getString(R.string.summary_distance_zero)
        }

        // B. Mensaje de Tareas
        val taskMessage: String = when (pendingTasks) {
            0 -> getString(R.string.summary_tasks_zero_all_done)
            in 1..5 -> resources.getQuantityString(R.plurals.summary_tasks_pending_plural, pendingTasks, pendingTasks)
            else -> getString(R.string.summary_tasks_too_many, pendingTasks)
        }

        return distanceMessage + getString(R.string.summary_separator) + taskMessage
    }

    // ==========================================
    // RESTO DE LA LÓGICA (sin cambios mayores)
    // ==========================================

    private fun loadItemsForDate(dateYYYYMMDD: String) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(userId).collection("tasks")
            .whereEqualTo("date", dateYYYYMMDD)
            .get()
            .addOnSuccessListener { result ->
                tasksList.clear()

                for (document in result) {
                    val task = document.toObject(Task::class.java).copy(id = document.id)
                    tasksList.add(task)
                }

                tasksList.sortBy { it.time }
                taskAdapter.notifyDataSetChanged()

                binding.recyclerViewTasks.visibility =
                    if (tasksList.isNotEmpty()) View.VISIBLE else View.GONE

                binding.textViewTasksTitle.visibility =
                    if (tasksList.isNotEmpty()) View.VISIBLE else View.GONE

                // 🔥 Entrenamientos deciden el estado final
                loadTrainingsForDate(dateYYYYMMDD)
            }
    }


    private fun loadTrainingsForDate(dateYYYYMMDD: String) {
        val userId = auth.currentUser?.uid ?: return

        // Fecha seleccionada como Calendar
        val selectedCal = Calendar.getInstance().apply {
            val parts = dateYYYYMMDD.split("-")
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        db.collection("usuarios")
            .document(userId)
            .collection("entrenamientos")
            .get()
            .addOnSuccessListener { result ->

                trainingList.clear()

                for (document in result) {
                    val entrenamiento = document
                        .toObject(Entrenamiento::class.java)
                        .copy(id = document.id)

                    val trainingDay = parseTrainingDateSafe(entrenamiento.fecha) ?: continue

                    // 🔥 COMPARACIÓN REAL DE FECHAS
                    if (
                        trainingDay.year == selectedCal.get(Calendar.YEAR) &&
                        trainingDay.month == selectedCal.get(Calendar.MONTH) + 1 &&
                        trainingDay.day == selectedCal.get(Calendar.DAY_OF_MONTH)
                    ) {
                        trainingList.add(entrenamiento)
                    }
                }

                trainingAdapter.notifyDataSetChanged()

                binding.recyclerViewTrainings.visibility =
                    if (trainingList.isNotEmpty()) View.VISIBLE else View.GONE

                binding.textViewTrainingsTitle.visibility =
                    if (trainingList.isNotEmpty()) View.VISIBLE else View.GONE

                updatePlaceholderVisibility()
            }
    }




    private fun updatePlaceholderVisibility() {
        if (tasksList.isEmpty() && trainingList.isEmpty()) {
            binding.layoutPlaceholder.visibility = View.VISIBLE
            binding.textViewTasksTitle.visibility = View.GONE
            binding.textViewTrainingsTitle.visibility = View.GONE
        } else {
            binding.layoutPlaceholder.visibility = View.GONE
            binding.textViewTasksTitle.visibility = if (tasksList.isNotEmpty()) View.VISIBLE else View.GONE
            binding.textViewTrainingsTitle.visibility = if (trainingList.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun navigateToEntrenamientoDetalles(entrenamiento: Entrenamiento) {
        val intent = Intent(this, EntrenamientoDetalles::class.java).apply {
            putExtra("entrenamiento", entrenamiento)
        }
        startActivity(intent)
    }

    override fun onMonthChanged(widget: MaterialCalendarView, date: CalendarDay) {
        loadDatesForMonth(date)
        loadMonthlyGoalsFromFirebase(date.year, date.month)
    }

    private fun loadMonthlyGoalsFromFirebase(year: Int, month: Int) {
        val userId = auth.currentUser?.uid ?: return
        val monthYear = String.format(Locale.US, "%02d-%d", month, year)

        val challengesRef = db.collection("usuarios").document(userId)
            .collection("monthly_challenges").document(monthYear)

        challengesRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val bundle = document.toObject(MonthlyChallengesBundle::class.java)

                    monthlyDistanceGoal = bundle?.distancia?.goal ?: 100000.0
                    monthlyTrainingGoal = bundle?.entrenamientos?.goal ?: 20.0

                } else {
                    monthlyDistanceGoal = 100000.0
                    monthlyTrainingGoal = 20.0
                    Log.w("FITVERA_PLANNER", "No se encontraron objetivos mensuales para $monthYear.")
                }
                loadMonthlySummary(year, month)
            }
            .addOnFailureListener {
                Log.e("FITVERA_PLANNER", "Error al cargar objetivos de Firebase.", it)
                monthlyDistanceGoal = 100000.0
                monthlyTrainingGoal = 20.0
                loadMonthlySummary(year, month)
            }
    }


    private fun loadMonthlySummary(year: Int, month: Int) {
        val userId = auth.currentUser?.uid ?: return

        var totalDistanceKm = 0.0
        val completedDaysSet = mutableSetOf<String>()

        db.collection("usuarios").document(userId).collection("entrenamientos")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val dateString = document.getString("fecha") // Formato dd-MM-yyyy
                    val distancia = document.getDouble("distancia") ?: 0.0 // Distancia en metros

                    if (dateString != null && distancia > 0) {
                        val trainingDay = parseTrainingDateSafe(dateString) ?: continue

                        if (trainingDay.month == month && trainingDay.year == year) {
                            totalDistanceKm += distancia / 1000.0
                            completedDaysSet.add(dateString)
                        }

                    }
                }
                val completedDays = completedDaysSet.size.toDouble()

                val distanceGoalKm = monthlyDistanceGoal / 1000.0

                val progressDistance = ((totalDistanceKm / distanceGoalKm) * 100).roundToInt().coerceIn(0, 100)

                binding.tvMonthlyDistance.text = String.format(
                    Locale.US, "%.1f / %.0f km", totalDistanceKm, distanceGoalKm
                )
                binding.tvMonthlyDays.text = String.format(
                    Locale.US, "Días: %d / %d", completedDays.roundToInt(), monthlyTrainingGoal.roundToInt()
                )
                binding.progressBarDistance.progress = progressDistance
            }
            .addOnFailureListener {
                Log.e("FITVERA_SUMMARY", "Error al cargar el resumen mensual.", it)
            }
    }

    private fun loadDatesForMonth(dayInMonth: CalendarDay) {
        val userId = auth.currentUser?.uid ?: return

        taskDates.clear()
        cardioDates.clear()
        fuerzaDates.clear()

        db.collection("usuarios").document(userId).collection("tasks").get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val dateString = document.getString("date")
                    dateString?.let {
                        val taskDay = parseDateString(it, "yyyy-MM-dd")
                        if (taskDay != null && taskDay.year == dayInMonth.year && taskDay.month == dayInMonth.month) {
                            taskDates.add(taskDay)
                        }
                    }
                }
                loadTrainingsDates(userId, dayInMonth)
            }
    }




    private fun loadTrainingsDates(userId: String, dayInMonth: CalendarDay) {
        db.collection("usuarios").document(userId)
            .collection("entrenamientos")
            .get()
            .addOnSuccessListener { result ->

                for (document in result) {
                    val dateString = document.getString("fecha") ?: continue
                    val trainingDay = parseTrainingDateSafe(dateString) ?: continue

                    if (trainingDay.year != dayInMonth.year ||
                        trainingDay.month != dayInMonth.month
                    ) continue

                    val tipo = document.getString("tipo")?.trim()?.lowercase() ?: ""

                    if (tipo == "fuerza") {
                        fuerzaDates.add(trainingDay)
                    } else {
                        cardioDates.add(trainingDay)
                    }
                }

                updateCalendarDecorators()
            }
    }


    private fun parseTrainingDateSafe(dateString: String): CalendarDay? {
        val formats = listOf(
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd"
        )

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                val date = sdf.parse(dateString) ?: continue
                val cal = Calendar.getInstance()
                cal.time = date
                return CalendarDay.from(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
            } catch (_: Exception) {}
        }
        return null
    }


    private fun parseDateString(dateString: String, format: String = "yyyy-MM-dd"): CalendarDay? {
        val parts = dateString.split("-")

        return try {
            if (parts.size == 3) {
                val year: Int
                val monthFirestore: Int
                val day: Int

                if (format == "dd-MM-yyyy") {
                    day = parts[0].toInt()
                    monthFirestore = parts[1].toInt()
                    year = parts[2].toInt()
                    return CalendarDay.from(year, monthFirestore, day)
                } else { // "yyyy-MM-dd"
                    year = parts[0].toInt()
                    monthFirestore = parts[1].toInt()
                    day = parts[2].toInt()
                    return CalendarDay.from(year, monthFirestore, day)
                }
            }
            null
        } catch (e: Exception) {
            Log.e("FITVERA_DEBUG", "Error al parsear la fecha: $dateString", e)
            null
        }
    }

    private fun updateCalendarDecorators() {
        binding.calendarView.removeDecorators()
        binding.calendarView.addDecorator(EventDecorator(taskDates, Color.BLUE)) // Tareas
        binding.calendarView.addDecorator(EventDecorator(cardioDates, Color.GREEN)) // Cardio
        binding.calendarView.addDecorator(EventDecorator(fuerzaDates, 0xFFFFA500.toInt())) // Fuerza = naranja
        binding.calendarView.invalidateDecorators()
    }


    private inner class EventDecorator(dates: Collection<CalendarDay>, private val color: Int) : DayViewDecorator {
        private val dates = HashSet(dates)
        override fun shouldDecorate(day: CalendarDay) = dates.contains(day)
        override fun decorate(view: DayViewFacade) {
            view.addSpan(DotSpan(10f, color))
        }
    }

    // Funciones showAddTaskDialog, showEditTaskDialog, addTaskToFirebase, updateTaskInFirebase, deleteTask (sin cambios)

    private fun showAddTaskDialog(selectedDate: CalendarDay) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null)
        val taskTitleEditText = dialogView.findViewById<EditText>(R.id.editTextTaskTitle)
        val taskDescriptionEditText = dialogView.findViewById<EditText>(R.id.editTextTaskDescription)
        val textViewSelectedTime = dialogView.findViewById<TextView>(R.id.textViewSelectedTime)

        var selectedTime = ""

        textViewSelectedTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                selectedTime = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                textViewSelectedTime.text = selectedTime
            }, hour, minute, true)

            timePickerDialog.show()
        }

        AlertDialog.Builder(this)
            .setTitle("Añadir nueva tarea")
            .setView(dialogView)
            .setPositiveButton("Añadir") { _, _ ->
                val title = taskTitleEditText.text.toString().trim()
                val description = taskDescriptionEditText.text.toString().trim()

                if (title.isNotEmpty()) {
                    val dateString = String.format(Locale.US, "%d-%02d-%02d", selectedDate.year, selectedDate.month, selectedDate.day)
                    val taskTime = if (selectedTime.isNotEmpty()) selectedTime else ""

                    val newTask = Task(
                        title = title,
                        description = description,
                        date = dateString,
                        time = taskTime
                    )
                    addTaskToFirebase(newTask)
                } else {
                    Toast.makeText(this, "El título no puede estar vacío.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
            .show()
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null)
        val taskTitleEditText = dialogView.findViewById<EditText>(R.id.editTextTaskTitle)
        val taskDescriptionEditText = dialogView.findViewById<EditText>(R.id.editTextTaskDescription)
        val textViewSelectedTime = dialogView.findViewById<TextView>(R.id.textViewSelectedTime)

        taskTitleEditText.setText(task.title)
        taskDescriptionEditText.setText(task.description)

        var selectedTime = task.time
        if (selectedTime.isNotEmpty()) {
            textViewSelectedTime.text = selectedTime
        } else {
            textViewSelectedTime.text = "--:--"
        }

        textViewSelectedTime.setOnClickListener {
            var hour = 12
            var minute = 0

            if (selectedTime.contains(":")) {
                try {
                    val parts = selectedTime.split(":")
                    hour = parts[0].toInt()
                    minute = parts[1].toInt()
                } catch (e: Exception) { /* Ignorar error de parseo */ }
            } else {
                val c = Calendar.getInstance()
                hour = c.get(Calendar.HOUR_OF_DAY)
                minute = c.get(Calendar.MINUTE)
            }

            val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                selectedTime = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                textViewSelectedTime.text = selectedTime
            }, hour, minute, true)

            timePickerDialog.show()
        }

        AlertDialog.Builder(this)
            .setTitle("Editar tarea")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val newTitle = taskTitleEditText.text.toString().trim()
                val newDescription = taskDescriptionEditText.text.toString().trim()

                if (newTitle.isNotEmpty()) {
                    val updatedTask = task.copy(
                        title = newTitle,
                        description = newDescription,
                        time = selectedTime
                    )
                    updateTaskInFirebase(updatedTask)
                } else {
                    Toast.makeText(this, "El título no puede estar vacío.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
            .show()
    }

    private fun addTaskToFirebase(task: Task) {
        val userId = auth.currentUser?.uid
        if (userId == null) return

        db.collection("usuarios").document(userId).collection("tasks")
            .add(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Tarea agregada con éxito.", Toast.LENGTH_SHORT).show()
                loadItemsForDate(task.date)
                loadDatesForMonth(binding.calendarView.currentDate)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTaskInFirebase(task: Task) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(userId).collection("tasks").document(task.id)
            .set(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Tarea actualizada.", Toast.LENGTH_SHORT).show()
                loadItemsForDate(task.date)
                loadDatesForMonth(binding.calendarView.currentDate)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteTask(task: Task) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(userId).collection("tasks").document(task.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Tarea eliminada.", Toast.LENGTH_SHORT).show()
                val selectedDate = binding.calendarView.selectedDate
                if (selectedDate != null) {
                    val dateString = String.format(Locale.US, "%d-%02d-%02d", selectedDate.year, selectedDate.month, selectedDate.day)
                    loadItemsForDate(dateString)
                    loadDatesForMonth(binding.calendarView.currentDate)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}