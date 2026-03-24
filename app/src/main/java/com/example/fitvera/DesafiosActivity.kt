package com.example.fitvera


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

// Clase principal que gestiona la lógica de los retos mensuales y la vitrina de trofeos
class DesafiosActivity : AppCompatActivity() {

    // --- Variables de Interfaz (Vistas) ---
    private lateinit var btnBack: ImageButton
    private lateinit var desafiosContent: LinearLayout

    // Vistas dedicadas al "Reto Destacado" (el principal de la parte superior)
    private lateinit var tvDestacadoTitle: TextView
    private lateinit var tvDestacadoDescription: TextView
    private lateinit var pbDestacadoProgress: ProgressBar
    private lateinit var tvDestacadoProgressText: TextView

    // Vistas para los tres retos fijos mensuales
    private lateinit var tvDistanciaTitle: TextView
    private lateinit var tvDistanciaDescription: TextView
    private lateinit var pbDistanciaProgress: ProgressBar
    private lateinit var tvDistanciaProgressText: TextView

    private lateinit var tvDesnivelTitle: TextView
    private lateinit var tvDesnivelDescription: TextView
    private lateinit var pbDesnivelProgress: ProgressBar
    private lateinit var tvDesnivelProgressText: TextView

    private lateinit var tvEntrenamientosTitle: TextView
    private lateinit var tvEntrenamientosDescription: TextView
    private lateinit var pbEntrenamientosProgress: ProgressBar
    private lateinit var tvEntrenamientosProgressText: TextView

    // Contenedor dinámico donde se insertarán los trofeos ganados
    private lateinit var trophiesLinearLayout: LinearLayout

    // --- Firebase ---
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // --- Modelos de Datos (Data Classes) para mapear Firestore ---
    data class Entrenamiento(val distancia: Double = 0.0, val desnivel: Double = 0.0, val fecha: String = "")
    data class MonthlyChallenge(val id: String = "", val title: String = "", val description: String = "", val goal: Double = 0.0, val type: String = "")
    data class Trophy(val id: String = "", val title: String = "", val description: String = "", val type: String = "")

    // Contenedor que agrupa los 4 retos del mes para guardarlos en un solo documento
    data class MonthlyChallengesBundle(
        val destacado: MonthlyChallenge = MonthlyChallenge(),
        val distancia: MonthlyChallenge = MonthlyChallenge(),
        val desnivel: MonthlyChallenge = MonthlyChallenge(),
        val entrenamientos: MonthlyChallenge = MonthlyChallenge()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalladesafios) // Infla el layout XML correspondiente

        // 1. Vinculación de variables con los componentes del XML mediante ID
        btnBack = findViewById(R.id.btn_back)
        desafiosContent = findViewById(R.id.desafios_content)
        trophiesLinearLayout = findViewById(R.id.trophies_linear_layout)

        tvDestacadoTitle = findViewById(R.id.tv_destacado_title)
        tvDestacadoDescription = findViewById(R.id.tv_destacado_description)
        pbDestacadoProgress = findViewById(R.id.pb_destacado_progress)
        tvDestacadoProgressText = findViewById(R.id.tv_destacado_progress_text)

        tvDistanciaTitle = findViewById(R.id.tv_distancia_title)
        tvDistanciaDescription = findViewById(R.id.tv_distancia_description)
        pbDistanciaProgress = findViewById(R.id.pb_distancia_progress)
        tvDistanciaProgressText = findViewById(R.id.tv_distancia_progress_text)

        tvDesnivelTitle = findViewById(R.id.tv_desnivel_title)
        tvDesnivelDescription = findViewById(R.id.tv_desnivel_description)
        pbDesnivelProgress = findViewById(R.id.pb_desnivel_progress)
        tvDesnivelProgressText = findViewById(R.id.tv_desnivel_progress_text)

        tvEntrenamientosTitle = findViewById(R.id.tv_entrenamientos_title)
        tvEntrenamientosDescription = findViewById(R.id.tv_entrenamientos_description)
        pbEntrenamientosProgress = findViewById(R.id.pb_entrenamientos_progress)
        tvEntrenamientosProgressText = findViewById(R.id.tv_entrenamientos_progress_text)

        // Configuración del botón de retroceso
        btnBack.setOnClickListener { finish() }

        // Ocultamos el contenido inicialmente hasta que los datos de Firebase estén listos
        desafiosContent.visibility = View.GONE

        // Iniciamos la carga de retos
        loadAllChallenges()
    }

    /**
     * Recupera los retos del mes actual para el usuario logueado.
     */
    private fun loadAllChallenges() {
        val userId = auth.currentUser?.uid ?: return
        // Formato de fecha para el ID del documento: "MM-yyyy" (ej: 03-2026)
        val currentMonthYear = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        val monthlyChallengesRef = firestore.collection("usuarios").document(userId)
            .collection("monthly_challenges").document(currentMonthYear)

        monthlyChallengesRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // Si ya existen retos para este mes, los cargamos y actualizamos la UI
                val challenges = document.toObject(MonthlyChallengesBundle::class.java)
                challenges?.let {
                    updateChallengeUI(it.destacado, tvDestacadoTitle, tvDestacadoDescription, pbDestacadoProgress, tvDestacadoProgressText, currentMonthYear)
                    updateChallengeUI(it.distancia, tvDistanciaTitle, tvDistanciaDescription, pbDistanciaProgress, tvDistanciaProgressText, currentMonthYear)
                    updateChallengeUI(it.desnivel, tvDesnivelTitle, tvDesnivelDescription, pbDesnivelProgress, tvDesnivelProgressText, currentMonthYear)
                    updateChallengeUI(it.entrenamientos, tvEntrenamientosTitle, tvEntrenamientosDescription, pbEntrenamientosProgress, tvEntrenamientosProgressText, currentMonthYear)
                }
            } else {
                // Si es la primera vez que entra en el mes, generamos retos nuevos
                generateAndSaveChallengesForMonth(userId, currentMonthYear)
            }
            // Cargamos la sección de trofeos histórica
            loadTrophies()
        }.addOnFailureListener {
            desafiosContent.visibility = View.VISIBLE
            Toast.makeText(this, "Error al cargar los desafíos.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Genera retos de forma aleatoria basados en el mes y año actual.
     */
    private fun generateAndSaveChallengesForMonth(userId: String, monthYear: String) {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        // Usamos una semilla para Random basada en el tiempo para que el reto sea consistente todo el mes
        val random = Random(year * 100 + month)

        // Selección aleatoria del tipo de reto destacado
        val destacadoChallenge = when (random.nextInt(3)) {
            0 -> MonthlyChallenge("destacado_distancia_$monthYear", "Maratón de ${getMonthName(month)}", "Corre 80 km este mes.", 80000.0, "distancia")
            1 -> MonthlyChallenge("destacado_desnivel_$monthYear", "Escalador del ${getMonthName(month)}", "Sube 500m de desnivel.", 500.0, "desnivel")
            else -> MonthlyChallenge("destacado_entrenamientos_$monthYear", "Constancia del ${getMonthName(month)}", "Registra 15 entrenos.", 15.0, "entrenamientos")
        }

        // Definición de las metas fijas mensuales
        val bundle = MonthlyChallengesBundle(
            destacadoChallenge,
            MonthlyChallenge("distancia_mes_$monthYear", "Desafío de Distancia", "Meta mensual de distancia.", 40000.0, "distancia"),
            MonthlyChallenge("desnivel_mes_$monthYear", "Desafío de Desnivel", "Meta mensual de desnivel.", 400.0, "desnivel"),
            MonthlyChallenge("entrenamientos_mes_$monthYear", "Desafío de Entrenamientos", "Meta mensual de sesiones.", 12.0, "entrenamientos")
        )

        // Guardamos el paquete en la subcolección del usuario
        firestore.collection("usuarios").document(userId)
            .collection("monthly_challenges").document(monthYear).set(bundle)
            .addOnSuccessListener { loadAllChallenges() }
    }

    /**
     * Calcula el progreso del usuario consultando todos sus entrenamientos y filtrando por el mes actual.
     */
    private fun updateChallengeUI(challenge: MonthlyChallenge, titleView: TextView, descView: TextView, pb: ProgressBar, tvProg: TextView, monthYear: String) {
        val userId = auth.currentUser?.uid ?: return
        titleView.text = challenge.title
        descView.text = challenge.description

        // Consultamos la colección de entrenamientos del usuario
        firestore.collection("usuarios").document(userId).collection("entrenamientos").get().addOnSuccessListener { docs ->
            var current = 0.0
            val parts = monthYear.split("-")
            val targetMonth = parts[0].toInt() - 1
            val targetYear = parts[1].toInt()

            // Iteramos sobre cada entrenamiento guardado
            for (doc in docs) {
                val e = doc.toObject(Entrenamiento::class.java)
                val cal = Calendar.getInstance()
                try {
                    // Parseamos la fecha del entrenamiento (dd-MM-yyyy)
                    val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(e.fecha)
                    date?.let {
                        cal.time = it
                        // Si el entrenamiento pertenece al mes y año que estamos visualizando:
                        if (cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear) {
                            when (challenge.type) {
                                "distancia" -> current += e.distancia
                                "desnivel" -> current += e.desnivel
                                "entrenamientos" -> current += 1.0
                            }
                        }
                    }
                } catch (ex: Exception) { /* Salta entrenamientos con fechas corruptas */ }
            }

            // Calculamos el porcentaje de progreso
            val percent = if (challenge.goal > 0) ((current / challenge.goal) * 100).roundToInt() else 0
            pb.progress = percent.coerceAtMost(100) // Aseguramos que la barra no pase del 100%
            tvProg.text = "$percent%"

            // Si el usuario alcanzó la meta, intentamos guardar el trofeo correspondiente
            if (current >= challenge.goal && challenge.goal > 0) saveTrophy(challenge, current, monthYear)
        }
    }

    /**
     * Registra un trofeo en la base de datos si el usuario no lo tiene ya.
     */
    private fun saveTrophy(challenge: MonthlyChallenge, progress: Double, monthYear: String) {
        val userId = auth.currentUser?.uid ?: return
        val trophyRef = firestore.collection("usuarios").document(userId).collection("trofeos").document(challenge.id)

        trophyRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                // Generamos un título que incluya el mes y el año del logro
                val parts = monthYear.split("-")
                val titleWithDate = "${challenge.title} - ${getMonthName(parts[0].toInt() - 1)} ${parts[1]}"

                val trophyData = hashMapOf(
                    "id" to challenge.id,
                    "title" to titleWithDate,
                    "description" to challenge.description,
                    "type" to challenge.type
                )
                // Al guardar el trofeo, refrescamos la vitrina visual
                trophyRef.set(trophyData).addOnSuccessListener { loadTrophies() }
            }
        }
    }

    /**
     * Carga todos los trofeos ganados históricamente y los agrupa por tipo para mostrarlos.
     */
    private fun loadTrophies() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("usuarios").document(userId).collection("trofeos").get()
            .addOnSuccessListener { documents ->
                trophiesLinearLayout.removeAllViews() // Limpiamos la vista antes de añadir elementos nuevos
                val allTrophies = documents.toObjects(Trophy::class.java)
                val tvNoTrophies = findViewById<TextView>(R.id.tv_no_trophies)

                if (allTrophies.isEmpty()) {
                    tvNoTrophies?.visibility = View.VISIBLE
                } else {
                    tvNoTrophies?.visibility = View.GONE
                    // Agrupamos los trofeos por tipo (distancia, desnivel, etc.) y contamos cuántos hay de cada uno
                    val counts = allTrophies.groupingBy { it.type }.eachCount()

                    // Creamos un ítem visual por cada grupo (ej: Icono de Distancia con un "x3")
                    counts.forEach { (type, count) ->
                        renderSummaryTrophy(type, count)
                    }
                }
                // Una vez cargado todo, hacemos visible el contenedor principal
                desafiosContent.visibility = View.VISIBLE
            }
    }

    /**
     * Infla el diseño de un trofeo individual y lo añade al scroll horizontal.
     */
    private fun renderSummaryTrophy(type: String, count: Int) {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.trophy_summary_item, trophiesLinearLayout, false)

        val icon: ImageView = view.findViewById(R.id.iv_summary_icon)
        val counter: TextView = view.findViewById(R.id.tv_trophy_counter)
        val label: TextView = view.findViewById(R.id.tv_summary_type)

        // Asigna la imagen correcta según el tipo de trofeo
        icon.setImageResource(getTrophyImageResource(type))
        counter.text = "x$count"
        label.text = type.replaceFirstChar { it.uppercase() }

        // Al hacer clic, navega a la actividad de detalle pasando el tipo de filtro
        view.setOnClickListener {
            val intent = Intent(this, MostrarTrofeos::class.java)
            intent.putExtra("FILTER_TYPE", type)
            intent.putExtra("USER_ID", auth.currentUser?.uid)
            startActivity(intent)
        }
        trophiesLinearLayout.addView(view)
    }

    /**
     * Mapea los tipos de reto con sus respectivos iconos de recursos.
     */
    private fun getTrophyImageResource(type: String): Int {
        return when (type) {
            "distancia" -> R.drawable.trofeodistancia
            "desnivel" -> R.drawable.trofeodesnivel
            "entrenamientos" -> R.drawable.trofeonumerodenetrenamientos
            else -> R.drawable.trofeodesafio
        }
    }

    /**
     * Convierte el número del mes en su nombre en español (ej: 0 -> Enero).
     */
    private fun getMonthName(month: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, month)
        return SimpleDateFormat("MMMM", Locale("es", "ES")).format(cal.time)
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}