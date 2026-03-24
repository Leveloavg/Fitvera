package com.example.fitvera


import User
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.hbb20.CountryCodePicker
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fragmento que representa el perfil del usuario.
 * Implementa 'EditProfileDialogListener' para reaccionar cuando el usuario guarda cambios en su perfil.
 */
class perfil : Fragment(), EditProfileDialogFragment.EditProfileDialogListener {

    // --- Definición de variables de la Interfaz (UI) ---
    private lateinit var tvUserName: TextView      // Nombre del usuario
    private lateinit var tvUserInfo: TextView      // Edad y sexo
    private lateinit var tvUserCoins: TextView     // Monedas virtuales (FitCoins)
    private lateinit var profilePicImageView: CircleImageView // Imagen circular de perfil
    private lateinit var ivCountryFlag: ImageView  // Bandera del país
    private lateinit var btnAchievements: ImageButton // Botón para ir a trofeos
    private lateinit var btnEditProfile: ImageButton  // Botón para editar datos

    // Vistas para estadísticas (Valores totales y del mes actual)
    private lateinit var tvWorkoutsTotalValue: TextView
    private lateinit var tvWorkoutsMonthValue: TextView
    private lateinit var tvDistanceTotalValue: TextView
    private lateinit var tvDistanceMonthValue: TextView
    private lateinit var tvTimeTotalValue: TextView
    private lateinit var tvTimeMonthValue: TextView
    private lateinit var tvElevationTotalValue: TextView
    private lateinit var tvElevationMonthValue: TextView

    // Red Social: Amigos, Seguidores y Racha
    private lateinit var tvFollowersValue: TextView
    private lateinit var tvFriendsLabel: TextView
    private lateinit var followersSection: LinearLayout
    private lateinit var ivStreakFire: ImageView // Icono de fuego 🔥
    private lateinit var tvStreakDays: TextView  // Número de días de racha
    private lateinit var tvMaxStreakDays: TextView // Récord histórico de racha

    private lateinit var activityChart: BarChart // Gráfico de barras de actividad

    // Instancias de Firebase
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Objetos para detener la escucha de datos cuando el fragmento se destruya
    private var requestsListener: ListenerRegistration? = null
    private var friendsListener: ListenerRegistration? = null

    // Infla el diseño XML del fragmento
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Vinculación de los componentes XML con las variables de Kotlin (findViewById)
        tvUserName = view.findViewById(R.id.tv_user_name)
        tvUserInfo = view.findViewById(R.id.tv_user_info)
        tvUserCoins = view.findViewById(R.id.tv_user_coins)
        profilePicImageView = view.findViewById(R.id.iv_profile_pic)
        ivCountryFlag = view.findViewById(R.id.iv_country_flag)
        btnEditProfile = view.findViewById(R.id.btn_edit_profile)
        btnAchievements = view.findViewById(R.id.btn_achievements)

        tvWorkoutsTotalValue = view.findViewById(R.id.tv_workouts_total_value)
        tvWorkoutsMonthValue = view.findViewById(R.id.tv_workouts_month_value)
        tvDistanceTotalValue = view.findViewById(R.id.tv_distance_total_value)
        tvDistanceMonthValue = view.findViewById(R.id.tv_distance_month_value)
        tvTimeTotalValue = view.findViewById(R.id.tv_time_total_value)
        tvTimeMonthValue = view.findViewById(R.id.tv_time_month_value)
        tvElevationTotalValue = view.findViewById(R.id.tv_elevation_total_value)
        tvElevationMonthValue = view.findViewById(R.id.tv_elevation_month_value)

        tvFollowersValue = view.findViewById(R.id.tv_followers_value)
        tvFriendsLabel = view.findViewById(R.id.tv_followers_label)
        followersSection = view.findViewById(R.id.followers_section)
        ivStreakFire = view.findViewById(R.id.iv_streak_fire)
        tvStreakDays = view.findViewById(R.id.tv_streak_days)
        tvMaxStreakDays = view.findViewById(R.id.tv_max_streak_days)

        activityChart = view.findViewById(R.id.activity_chart)

        // --- Configuración de Clics (Listeners) ---

        // Ir a la pantalla de Desafíos y Trofeos
        btnAchievements.setOnClickListener {
            val intent = Intent(requireContext(), DesafiosActivity::class.java)
            startActivity(intent)
        }

        // Abrir ventana emergente para editar nombre, edad, sexo, país y foto
        btnEditProfile.setOnClickListener {
            val dialog = EditProfileDialogFragment()
            dialog.listener = this // Suscribirse para recibir los cambios
            dialog.show(parentFragmentManager, "EditProfileDialog")
        }

        // Ir a la lista de amigos
        followersSection.setOnClickListener {
            val intent = Intent(requireContext(), FriendsActivity::class.java)
            startActivity(intent)
        }

        // Si hay solicitudes pendientes, al pulsar muestra el diálogo de solicitudes
        tvFollowersValue.setOnClickListener {
            val dialog = FollowRequestsDialogFragment()
            dialog.show(parentFragmentManager, "FollowRequestsDialog")
        }

        // --- Carga Inicial de Datos desde Firebase ---
        loadUserProfile()     // Carga nombre, edad, país y foto
        loadFollowRequests()  // Escucha solicitudes de amistad pendientes
        loadFriendsCount()    // Escucha cuántos amigos tiene actualmente
        loadTrainingStreak()  // Calcula la racha de días entrenando
        listenToUserCoins()   // Escucha cambios en las monedas (tiempo real)
    }

    /**
     * Se ejecuta cuando el diálogo de edición guarda los datos.
     * Actualiza la interfaz inmediatamente.
     */
    override fun onProfileSaved(nombre: String, sexo: String, edad: String, pais: String, fotoUrl: String?) {
        tvUserName.text = nombre
        tvUserInfo.text = "$sexo, $edad años"
        updateFlagUI(pais)
        saveUserProfile(nombre, sexo, edad, pais, fotoUrl) // Guarda los datos en la nube
    }

    /**
     * Sube los datos actualizados del perfil a Firestore.
     */
    private fun saveUserProfile(nombre: String, sexo: String, edad: String, pais: String, fotoUrl: String?) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = firestore.collection("usuarios").document(userId)

        val userData = mutableMapOf<String, Any>(
            "nombre" to nombre,
            "sexo" to sexo,
            "edad" to edad,
            "pais" to pais
        )

        if (fotoUrl != null) { userData["fotoUrl"] = fotoUrl }

        // merge() asegura que no borre otros campos (como las monedas o racha)
        userRef.set(userData, SetOptions.merge())
            .addOnSuccessListener {
                if (fotoUrl != null && isAdded) {
                    Glide.with(this).load(fotoUrl).placeholder(R.drawable.profile_placeholder).into(profilePicImageView)
                }
            }
    }

    /**
     * Obtiene los datos básicos del usuario desde la colección 'usuarios' al iniciar.
     */
    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = firestore.collection("usuarios").document(userId)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val nombre = document.getString("nombre")
                    val sexo = document.getString("sexo")
                    val edad = document.getString("edad")
                    val pais = document.getString("pais") ?: ""
                    val fotoUrl = document.getString("fotoUrl")

                    nombre?.let { tvUserName.text = it }
                    if (sexo != null && edad != null) {
                        tvUserInfo.text = "$sexo, $edad años"
                    }
                    if (fotoUrl != null && isAdded) {
                        Glide.with(this).load(fotoUrl).placeholder(R.drawable.profile_placeholder).into(profilePicImageView)
                    }

                    updateFlagUI(pais) // Muestra la bandera del país seleccionado
                }
                // Después de cargar el perfil, carga las estadísticas y el gráfico
                loadTrainingStats()
                loadTrainingChart()
            }
    }

    /**
     * Usa la librería CountryCodePicker para obtener el icono de la bandera según el código (ES, MX, etc).
     */
    private fun updateFlagUI(paisCodigo: String) {
        if (paisCodigo.isNotEmpty() && isAdded) {
            val ccp = CountryCodePicker(requireContext())
            ccp.setCountryForNameCode(paisCodigo) // Busca el país por código
            ivCountryFlag.setImageResource(ccp.selectedCountryFlagResourceId)
            ivCountryFlag.visibility = View.VISIBLE
        } else {
            ivCountryFlag.visibility = View.GONE
        }
    }

    /**
     * Escucha en tiempo real cuántos amigos hay en la subcolección 'friends'.
     */
    private fun loadFriendsCount() {
        val currentUserId = auth.currentUser?.uid ?: return
        friendsListener = firestore.collection("usuarios").document(currentUserId)
            .collection("friends")
            .addSnapshotListener { snapshots, e ->
                if (e == null && snapshots != null) {
                    val count = snapshots.size()
                    tvFriendsLabel.text = "$count Seguidores"
                }
            }
    }

    /**
     * Escucha en tiempo real si hay nuevas solicitudes de amistad.
     */
    private fun loadFollowRequests() {
        val currentUserId = auth.currentUser?.uid ?: return
        requestsListener = firestore.collection("usuarios").document(currentUserId)
            .collection("follow_requests")
            .addSnapshotListener { snapshots, e ->
                if (e == null && snapshots != null) {
                    val count = snapshots.size()
                    if (count > 0) {
                        tvFollowersValue.text = "$count pendiente${if (count > 1) "s" else ""}"
                        tvFollowersValue.visibility = View.VISIBLE
                    } else {
                        tvFollowersValue.visibility = View.GONE
                    }
                }
            }
    }

    /**
     * Escucha el campo 'coins' en el documento del usuario para actualizar el saldo de monedas.
     */
    private fun listenToUserCoins() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("usuarios").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val coins = snapshot.getLong("coins") ?: 0
                    tvUserCoins.text = coins.toString()
                } else {
                    tvUserCoins.text = "0"
                }
            }
    }

    /**
     * Obtiene todos los entrenamientos para calcular la racha actual y máxima.
     */
    private fun loadTrainingStreak() {
        val userId = auth.currentUser?.uid ?: return
        val entrenamientosRef = firestore.collection("usuarios").document(userId).collection("entrenamientos")

        entrenamientosRef.get().addOnSuccessListener { documents ->
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

            // Convierte las fechas de los entrenamientos a milisegundos (limpiando horas/minutos)
            val uniqueTrainingDaysMillis = documents.mapNotNull { document ->
                val fechaString = document.getString("fecha")
                try {
                    dateFormat.parse(fechaString ?: "")?.let { date ->
                        Calendar.getInstance().apply {
                            time = date
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                } catch (e: Exception) { null }
            }.distinct().sortedDescending() // Elimina duplicados y ordena (hoy primero)

            val (currentStreak, maxStreak) = calculateStreaks(uniqueTrainingDaysMillis)
            updateStreakUI(currentStreak)
            tvMaxStreakDays.text = "Máx: $maxStreak"
        }
    }

    /**
     * Lógica matemática para contar cuántos días consecutivos hay en la lista.
     */
    private fun calculateStreaks(days: List<Long>): Pair<Int, Int> {
        if (days.isEmpty()) return Pair(0, 0)

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = today - TimeUnit.DAYS.toMillis(1)

        var current = 0
        // Si el último entreno fue hoy o ayer, la racha puede seguir activa
        if (days.first() == today || days.first() == yesterday) {
            var expected = days.first()
            for (day in days) {
                if (day == expected) {
                    current++
                    expected -= TimeUnit.DAYS.toMillis(1) // Restamos un día para buscar el siguiente
                } else break
            }
        }

        // Cálculo de racha máxima (récord)
        var max = 0
        var temp = 0
        val sortedAsc = days.sorted()
        for (i in sortedAsc.indices) {
            if (i > 0 && sortedAsc[i] == sortedAsc[i-1] + TimeUnit.DAYS.toMillis(1)) temp++
            else temp = 1
            if (temp > max) max = temp
        }
        return Pair(current, max)
    }

    /**
     * Actualiza el icono del fuego 🔥 y su color según la intensidad de la racha.
     */
    private fun updateStreakUI(days: Int) {
        tvStreakDays.text = days.toString()
        if (days > 0) {
            ivStreakFire.visibility = View.VISIBLE
            val colorRes = when {
                days in 1..3 -> android.R.color.holo_orange_light // Naranja claro
                days in 4..7 -> android.R.color.holo_orange_dark  // Naranja oscuro
                else -> android.R.color.holo_red_dark           // Rojo intenso (+ de una semana)
            }
            ivStreakFire.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))
        } else {
            ivStreakFire.visibility = View.GONE
        }
    }

    /**
     * Suma todos los kilómetros, tiempos y desniveles de los entrenamientos.
     * Separa el total global del total del mes actual.
     */
    private fun loadTrainingStats() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("usuarios").document(userId).collection("entrenamientos").get()
            .addOnSuccessListener { documents ->
                var totalW = 0; var totalD = 0.0; var totalT = 0L; var totalE = 0.0
                var monthW = 0; var monthD = 0.0; var monthT = 0L; var monthE = 0.0

                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentYear = calendar.get(Calendar.YEAR)
                val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

                for (doc in documents) {
                    val en = try { doc.toObject(Entrenamiento::class.java) } catch (e: Exception) { continue }

                    // Sumas globales
                    totalW++; totalD += en.distancia; totalT += en.tiempo; totalE += en.desnivel

                    // Sumas mensuales
                    try {
                        val d = sdf.parse(en.fecha)
                        d?.let {
                            calendar.time = it
                            if (calendar.get(Calendar.MONTH) == currentMonth && calendar.get(Calendar.YEAR) == currentYear) {
                                monthW++; monthD += en.distancia; monthT += en.tiempo; monthE += en.desnivel
                            }
                        }
                    } catch (e: Exception) { }
                }

                // Mostrar datos formateados (km, m, horas/minutos)
                tvWorkoutsTotalValue.text = totalW.toString()
                tvWorkoutsMonthValue.text = "$monthW este mes"
                tvDistanceTotalValue.text = "%.0f km".format(totalD / 1000)
                tvDistanceMonthValue.text = "%.1f km este mes".format(monthD / 1000)
                tvTimeTotalValue.text = formatTime(totalT)
                tvTimeMonthValue.text = "${formatTime(monthT)} este mes"
                tvElevationTotalValue.text = "%.0f m".format(totalE)
                tvElevationMonthValue.text = "%.0f m este mes".format(monthE)
            }
    }

    /**
     * Genera un gráfico de barras comparando los kilómetros de las últimas 7 semanas.
     */
    private fun loadTrainingChart() {
        val userId = auth.currentUser?.uid ?: return

        // Ajustar colores según si el sistema está en modo oscuro o claro
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val fitveraBlue = ContextCompat.getColor(requireContext(), R.color.fitvera_blue)
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        firestore.collection("usuarios").document(userId).collection("entrenamientos").get()
            .addOnSuccessListener { documents ->
                val allTrainings = documents.map { it.toObject(Entrenamiento::class.java) }
                val weeklyData = mutableMapOf<Int, Pair<Int, Double>>() // Indice de semana -> (Contador, Distancia)
                val labels = mutableListOf<String>()
                val today = Calendar.getInstance().timeInMillis
                val oneWeekMillis = TimeUnit.DAYS.toMillis(7)

                // Preparar las etiquetas de las últimas 7 semanas (S1, S2...)
                for (i in 0 until 7) {
                    weeklyData[i] = Pair(0, 0.0)
                    labels.add(0, "S${7 - i}")
                }

                // Agrupar entrenamientos por semana de antigüedad
                for (training in allTrainings) {
                    try {
                        val trainingDate = dateFormat.parse(training.fecha)
                        if (trainingDate != null) {
                            val weekIndex = ((today - trainingDate.time) / oneWeekMillis).toInt()
                            if (weekIndex in 0..6) {
                                val current = weeklyData[weekIndex]!!
                                weeklyData[weekIndex] = Pair(current.first + 1, current.second + training.distancia)
                            }
                        }
                    } catch (e: Exception) { }
                }

                val entries = ArrayList<BarEntry>()
                weeklyData.toSortedMap().forEach { (index, data) ->
                    entries.add(BarEntry(index.toFloat(), (data.second / 1000).toFloat())) // Kilómetros
                }

                // Configuración visual del gráfico
                val dataSet = BarDataSet(entries, "Kilómetros")
                dataSet.color = fitveraBlue
                dataSet.valueTextColor = textColor

                activityChart.data = BarData(dataSet)
                activityChart.description.isEnabled = false
                activityChart.legend.textColor = textColor
                activityChart.xAxis.apply {
                    valueFormatter = IndexAxisValueFormatter(labels)
                    position = XAxis.XAxisPosition.BOTTOM
                    this.textColor = textColor
                    setDrawGridLines(false)
                    granularity = 1f
                }
                activityChart.axisLeft.textColor = textColor
                activityChart.axisRight.isEnabled = false
                activityChart.animateY(1000)
                activityChart.invalidate()
            }
    }

    // Convierte milisegundos en formato legible de Horas y Minutos
    private fun formatTime(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    // Al destruir la vista, desconectamos los escuchas de Firebase para ahorrar batería/datos
    override fun onDestroyView() {
        super.onDestroyView()
        requestsListener?.remove()
        friendsListener?.remove()
    }
}