package com.example.fitvera

import User
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.components.XAxis
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hbb20.CountryCodePicker
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration as OsmdroidConfiguration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class OtroUsuarioPerfilActivity : AppCompatActivity() {

    // Instancias de Firebase para base de datos y autenticación
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var userProfile: User // Objeto que guarda los datos del usuario que estamos viendo

    // Referencias a las vistas (UI)
    private lateinit var loadingView: LinearLayout       // Pantalla de carga
    private lateinit var scrollViewContent: LinearLayout // Contenedor principal del perfil
    private lateinit var profilePicImageView: CircleImageView // Foto circular
    private lateinit var flagImageView: ImageView        // Bandera del país
    private lateinit var userNameTextView: TextView      // Nombre
    private lateinit var userInfoTextView: TextView      // Sexo y edad
    private lateinit var tvStreakDays: TextView          // Días de racha actual
    private lateinit var ivStreakFire: ImageView         // Icono de fuego de la racha
    private lateinit var tvMaxStreakDays: TextView       // Racha histórica máxima
    private lateinit var lastActivityCard: CardView      // Tarjeta de la última actividad
    private lateinit var lastActivityDateTextView: TextView
    private lateinit var lastActivityDistanceTextView: TextView
    private lateinit var lastActivityTimeTextView: TextView
    private lateinit var mapPreview: MapView             // Mini mapa de la ruta
    private lateinit var trophiesGridLayout: GridLayout  // Rejilla de trofeos
    private lateinit var activityChart: BarChart         // Gráfico de barras de actividad

    // Vistas de estadísticas acumuladas
    private lateinit var tvWorkoutsTotal: TextView; private lateinit var tvWorkoutsMonth: TextView
    private lateinit var tvDistanceTotal: TextView; private lateinit var tvDistanceMonth: TextView
    private lateinit var tvTimeTotal: TextView;     private lateinit var tvTimeMonth: TextView
    private lateinit var tvElevationTotal: TextView; private lateinit var tvElevationMonth: TextView

    // Control de carga asíncrona (espera a que terminen 4 procesos antes de quitar el "Loading")
    private var loadedCount = 0
    private val requiredLoads = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Carga la configuración del mapa (OpenStreetMap)
        OsmdroidConfiguration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.perfilusuario)

        // Recuperamos el objeto 'User' que se pasó desde la pantalla anterior (Lista de amigos/Buscador)
        val user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("user_data", User::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("user_data")
        }

        // Si por algún motivo no llegan datos, cerramos para evitar errores
        if (user == null) {
            Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        this.userProfile = user

        inicializarVistas()

        // Rellenamos los datos básicos que ya tenemos (sin necesidad de ir a Firebase todavía)
        userNameTextView.text = user.nombre
        userInfoTextView.text = "${user.sexo}, ${user.edad} años"

        // Carga la foto de perfil con Glide
        if (!user.fotoUrl.isNullOrEmpty()) {
            Glide.with(this).load(user.fotoUrl).placeholder(R.drawable.profile_placeholder).into(profilePicImageView)
        }

        // Lógica para mostrar la bandera según el código de país (ej: "ES", "MX")
        val codigoPais = user.pais ?: ""
        if (codigoPais.isNotEmpty()) {
            val ccp = CountryCodePicker(this)
            ccp.setCountryForNameCode(codigoPais)
            flagImageView.setImageResource(ccp.selectedCountryFlagResourceId)
            flagImageView.visibility = View.VISIBLE
        } else {
            flagImageView.visibility = View.GONE
        }

        // Comprobamos si somos amigos antes de mostrar sus datos privados (entrenamientos, etc)
        checkFriendshipAndLoadProfile(user.uid)
    }

    private fun inicializarVistas() {
        // Vinculación de todos los IDs del XML
        loadingView = findViewById(R.id.loading_indicator_container)
        scrollViewContent = findViewById(R.id.content_container_scroll)
        profilePicImageView = findViewById(R.id.iv_profile_pic)
        flagImageView = findViewById(R.id.iv_country_flag)
        userNameTextView = findViewById(R.id.tv_user_name)
        userInfoTextView = findViewById(R.id.tv_user_info)
        tvStreakDays = findViewById(R.id.tv_streak_days)
        ivStreakFire = findViewById(R.id.iv_streak_fire)
        tvMaxStreakDays = findViewById(R.id.tv_max_streak_days)
        lastActivityCard = findViewById(R.id.card_recent_activities)
        lastActivityDateTextView = findViewById(R.id.tv_last_activity_date)
        lastActivityDistanceTextView = findViewById(R.id.tv_last_activity_distance)
        lastActivityTimeTextView = findViewById(R.id.tv_last_activity_time)
        mapPreview = findViewById(R.id.map_last_activity_preview)
        trophiesGridLayout = findViewById(R.id.trophies_grid_layout_perfil)
        activityChart = findViewById(R.id.activity_chart)
        tvWorkoutsTotal = findViewById(R.id.other_user_tv_workouts_total_value)
        tvWorkoutsMonth = findViewById(R.id.other_user_tv_workouts_month_value)
        tvDistanceTotal = findViewById(R.id.other_user_tv_distance_total_value)
        tvDistanceMonth = findViewById(R.id.other_user_tv_distance_month_value)
        tvTimeTotal = findViewById(R.id.other_user_tv_time_total_value)
        tvTimeMonth = findViewById(R.id.other_user_tv_time_month_value)
        tvElevationTotal = findViewById(R.id.other_user_tv_elevation_total_value)
        tvElevationMonth = findViewById(R.id.other_user_tv_elevation_month_value)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // Navegación a la lista completa de entrenamientos de este usuario
        lastActivityCard.setOnClickListener {
            val intent = Intent(this, MostrarEntrenamientos::class.java)
            intent.putExtra("user_id", userProfile.uid)
            intent.putExtra("user_name", userProfile.nombre)
            startActivity(intent)
        }

        // Navegación a la pantalla completa de trofeos
        trophiesGridLayout.setOnClickListener { navegarAMostrarTrofeos() }
    }

    private fun navegarAMostrarTrofeos() {
        if (auth.currentUser != null) {
            val intent = Intent(this, MostrarTrofeos::class.java)
            intent.putExtra("user_id", userProfile.uid)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Sesión no válida", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Verifica en Firestore si el usuario actual tiene a este perfil en su lista de amigos.
     */
    private fun checkFriendshipAndLoadProfile(otherUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val friendDoc = db.collection("usuarios").document(currentUserId)
                    .collection("friends").document(otherUserId).get().await()
                withContext(Dispatchers.Main) {
                    // Si existe el documento de amistad, cargamos el perfil completo
                    if (friendDoc.exists() || currentUserId == otherUserId) {
                        showFullProfile(otherUserId)
                    } else {
                        // Si no son amigos, quitamos el cargando y mostramos lo básico
                        loadingView.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { loadingView.visibility = View.GONE }
            }
        }
    }

    /**
     * Lanza las 4 peticiones simultáneas a Firebase para rellenar el perfil.
     */
    private fun showFullProfile(userId: String) {
        loadedCount = 0
        loadTrainingStreak(userId)      // 1. Rachas
        loadLastTraining(userId)        // 2. Último entreno
        loadOtherUserTrainingStats(userId) // 3. Números totales
        loadTrainingChart(userId)       // 4. Gráfico
    }

    /**
     * Calcula cuántos días seguidos lleva entrenando el usuario.
     */
    private fun loadTrainingStreak(userId: String) {
        val entrenamientosRef = db.collection("usuarios").document(userId).collection("entrenamientos")
        entrenamientosRef.get().addOnSuccessListener { documents ->
            val df1 = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val df2 = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            // Extrae fechas únicas de los documentos y las ordena
            val uniqueDays = documents.mapNotNull { doc ->
                val fechaStr = doc.getString("fecha") ?: ""
                try {
                    val date = try { df1.parse(fechaStr) } catch(e:Exception) { df2.parse(fechaStr) }
                    date?.let {
                        Calendar.getInstance().apply {
                            time = it
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                } catch (e: Exception) { null }
            }.distinct().sortedDescending()

            val (current, max) = calculateStreaks(uniqueDays)
            tvStreakDays.text = current.toString()
            tvMaxStreakDays.text = "Máx: $max"

            // Cambia el color del fuego según la intensidad de la racha
            if (current > 0) {
                ivStreakFire.visibility = View.VISIBLE
                val colorRes = when {
                    current in 1..3 -> android.R.color.holo_orange_light
                    current in 4..7 -> android.R.color.holo_orange_dark
                    else -> android.R.color.holo_red_dark
                }
                ivStreakFire.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
            } else {
                ivStreakFire.visibility = View.GONE
            }
            checkAllLoaded()
        }.addOnFailureListener { checkAllLoaded() }
    }

    // Lógica matemática para contar los días consecutivos en la lista de fechas
    private fun calculateStreaks(days: List<Long>): Pair<Int, Int> {
        if (days.isEmpty()) return Pair(0, 0)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = today - TimeUnit.DAYS.toMillis(1)

        var current = 0
        // Si el último entreno fue hoy o ayer, la racha está activa
        if (days.first() == today || days.first() == yesterday) {
            var expected = days.first()
            for (day in days) {
                if (day == expected) {
                    current++
                    expected -= TimeUnit.DAYS.toMillis(1)
                } else break
            }
        }

        // Calcula el récord histórico buscando la serie más larga de días seguidos
        var max = 0; var temp = 0
        val sortedAsc = days.sorted()
        for (i in sortedAsc.indices) {
            if (i > 0 && sortedAsc[i] == sortedAsc[i-1] + TimeUnit.DAYS.toMillis(1)) temp++
            else temp = 1
            if (temp > max) max = temp
        }
        return Pair(current, max)
    }

    /**
     * Genera el gráfico de barras comparando los kilómetros de las últimas 7 semanas.
     */
    private fun loadTrainingChart(userId: String) {
        val entrenamientosRef = db.collection("usuarios").document(userId).collection("entrenamientos")
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        entrenamientosRef.get().addOnSuccessListener { docs ->
            val entries = ArrayList<BarEntry>()
            val labels = mutableListOf<String>()
            val weeklyData = mutableMapOf<Int, Double>()
            val df1 = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val df2 = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            // Inicializa las 7 semanas anteriores a 0.0 km
            for (i in 0 until 7) { weeklyData[i] = 0.0; labels.add(0, "S${7-i}") }

            for (doc in docs) {
                val e = doc.toObject(Entrenamiento::class.java)
                try {
                    val date = try { df1.parse(e.fecha) } catch(err: Exception) { df2.parse(e.fecha) }
                    date?.let { d ->
                        val diff = System.currentTimeMillis() - d.time
                        val weekIndex = (diff / (7 * 24 * 60 * 60 * 1000L)).toInt()
                        if (weekIndex in 0..6) {
                            weeklyData[weekIndex] = weeklyData[weekIndex]!! + (e.distancia / 1000.0)
                        }
                    }
                } catch (ex: Exception) { }
            }

            weeklyData.toSortedMap().forEach { (idx, dist) -> entries.add(BarEntry(idx.toFloat(), dist.toFloat())) }

            val dataSet = BarDataSet(entries, "Km").apply {
                color = ContextCompat.getColor(this@OtroUsuarioPerfilActivity, R.color.fitvera_blue)
                valueTextColor = textColor
            }

            // Configuración visual del gráfico (Colores, ejes, animaciones)
            activityChart.apply {
                data = BarData(dataSet)
                xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.textColor = textColor
                axisLeft.textColor = textColor
                axisRight.isEnabled = false
                legend.textColor = textColor
                description.isEnabled = false
                animateY(1000)
                invalidate()
            }
            checkAllLoaded()
        }.addOnFailureListener { checkAllLoaded() }
    }

    /**
     * Obtiene el entrenamiento más reciente para mostrarlo en la cabecera.
     */
    private fun loadLastTraining(userId: String) {
        db.collection("usuarios").document(userId).collection("entrenamientos").get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val df1 = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    val df2 = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val trainings = docs.toObjects(Entrenamiento::class.java)
                    // Busca el de fecha más reciente
                    val last = trainings.maxByOrNull {
                        try { df1.parse(it.fecha)?.time ?: 0L } catch(e:Exception) {
                            try { df2.parse(it.fecha)?.time ?: 0L } catch(e2:Exception) { 0L }
                        }
                    }
                    last?.let {
                        lastActivityDateTextView.text = it.fecha
                        lastActivityDistanceTextView.text = "%.2f km".format(it.distancia / 1000.0)
                        val min = it.tiempo / 60000
                        val seg = (it.tiempo % 60000) / 1000
                        lastActivityTimeTextView.text = "Tiempo: ${min}min ${String.format("%02d", seg)}s"
                        // Si tiene ruta GPS, dibuja el mapa
                        if (it.puntosRecorrido.isNotEmpty()) {
                            mapPreview.visibility = View.VISIBLE
                            setupMiniMap(it.puntosRecorrido)
                        }
                    }
                }
                loadTrophies(userId) // Encadenamos la carga de trofeos
            }
    }

    // Configura el mapa estático con la línea del recorrido (Polyline)
    private fun setupMiniMap(puntos: List<HashMap<String, Any>>) {
        try {
            mapPreview.setTileSource(TileSourceFactory.MAPNIK)
            mapPreview.setMultiTouchControls(false)
            val geoPoints = puntos.map { GeoPoint((it["latitude"] as Number).toDouble(), (it["longitude"] as Number).toDouble()) }
            val line = Polyline().apply { setPoints(geoPoints) }
            mapPreview.overlays.clear()
            mapPreview.overlays.add(line)
            mapPreview.controller.setZoom(15.0)
            mapPreview.controller.setCenter(geoPoints.first())
        } catch (e: Exception) { }
    }

    /**
     * Carga los trofeos del usuario (limitado a los 4 primeros para el resumen).
     */
    private fun loadTrophies(userId: String) {
        db.collection("usuarios").document(userId).collection("trofeos").get()
            .addOnSuccessListener { docs ->
                trophiesGridLayout.removeAllViews()
                docs.toObjects(Trophy::class.java).take(4).forEach { trophy ->
                    addTrophyToGrid(trophy)
                }
                checkAllLoaded()
            }.addOnFailureListener { checkAllLoaded() }
    }

    // Infla el diseño de la tarjeta de trofeo y la añade a la rejilla (GridLayout)
    private fun addTrophyToGrid(trophy: Trophy) {
        val inflater = LayoutInflater.from(this)
        val trophyCard = inflater.inflate(R.layout.trophy_card, trophiesGridLayout, false)

        val img: ImageView = trophyCard.findViewById(R.id.trophy_image_view)
        val txtTitle: TextView = trophyCard.findViewById(R.id.trophy_title)
        val txtDesc: TextView = trophyCard.findViewById(R.id.trophy_description)

        // Asigna el icono según el tipo de logro
        img.setImageResource(when(trophy.type) {
            "distancia" -> R.drawable.trofeodistancia
            "desnivel" -> R.drawable.trofeodesnivel
            else -> R.drawable.trofeonumerodenetrenamientos
        })

        txtTitle.text = trophy.title.ifEmpty { "Logro" }
        txtDesc.text = trophy.description.ifEmpty { "¡Buen trabajo!" }

        // Configuración de márgenes y pesos para que queden alineados en la rejilla
        val params = GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            width = 0
            val marginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
            setMargins(marginPx, marginPx, marginPx, marginPx)
        }
        trophyCard.layoutParams = params
        trophyCard.setOnClickListener { navegarAMostrarTrofeos() }
        trophiesGridLayout.addView(trophyCard)
    }

    /**
     * Calcula estadísticas acumuladas: Totales históricos vs Totales del mes actual.
     */
    private fun loadOtherUserTrainingStats(userId: String) {
        db.collection("usuarios").document(userId).collection("entrenamientos").get()
            .addOnSuccessListener { documents ->
                var tW = 0; var tD = 0.0; var tT = 0L; var tE = 0.0 // Totales
                var mW = 0; var mD = 0.0; var mT = 0L; var mE = 0.0 // Mensuales
                val cal = Calendar.getInstance()
                val curM = cal.get(Calendar.MONTH); val curY = cal.get(Calendar.YEAR)
                val df1 = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val df2 = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                for (doc in documents) {
                    val e = doc.toObject(Entrenamiento::class.java)
                    tW++; tD += e.distancia; tT += e.tiempo; tE += e.desnivel
                    try {
                        val date = try { df1.parse(e.fecha) } catch(err:Exception) { df2.parse(e.fecha) }
                        date?.let {
                            cal.time = it
                            // Si el entreno es del mes y año actual, sumamos a las estadísticas mensuales
                            if (cal.get(Calendar.YEAR) == curY && cal.get(Calendar.MONTH) == curM) {
                                mW++; mD += e.distancia; mT += e.tiempo; mE += e.desnivel
                            }
                        }
                    } catch (ex: Exception) { }
                }
                // Mostramos los resultados formateados en la UI
                tvWorkoutsTotal.text = tW.toString()
                tvWorkoutsMonth.text = "$mW este mes"
                tvDistanceTotal.text = "%.0f km".format(tD / 1000.0)
                tvDistanceMonth.text = "%.0f km este mes".format(mD / 1000.0)
                tvTimeTotal.text = formatTime(tT)
                tvTimeMonth.text = "${formatTime(mT)} este mes"
                tvElevationTotal.text = "%.0f m".format(tE)
                tvElevationMonth.text = "%.0f m este mes".format(mE)
                checkAllLoaded()
            }.addOnFailureListener { checkAllLoaded() }
    }

    // Formatea milisegundos en un texto legible de horas y minutos
    private fun formatTime(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /**
     * Sistema de control: Cuando los 4 hilos de carga terminan, oculta el indicador de carga.
     */
    private fun checkAllLoaded() {
        loadedCount++
        if (loadedCount >= requiredLoads) {
            loadingView.visibility = View.GONE
            scrollViewContent.visibility = View.VISIBLE
        }
    }

    // Gestión obligatoria del ciclo de vida para el MapView de OSMDroid
    override fun onResume() { super.onResume(); if (::mapPreview.isInitialized) mapPreview.onResume() }
    override fun onPause() { super.onPause(); if (::mapPreview.isInitialized) mapPreview.onPause() }
}