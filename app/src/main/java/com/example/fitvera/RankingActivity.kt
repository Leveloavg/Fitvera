package com.example.fitvera

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RankingActivity : AppCompatActivity() {

    // Instancias de Firebase para autenticación y base de datos
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Elementos de la interfaz de usuario
    private lateinit var rankingRecyclerView: RecyclerView
    private lateinit var rankingAdapter: RankingAdapter
    private lateinit var metricToggleGroup: MaterialButtonToggleGroup // Botones para: Km, Ritmo, Actividades, Territorios
    private lateinit var timeRadioGroup: RadioGroup // Filtros de: Semana, Mes, Año, Total

    // Variables de estado para controlar qué ranking se está visualizando
    private var currentMetricType: String = "kilometers"
    private var currentMetricTime: String = "week"
    private var currentMetric: String = "kilometers_week" // Combinación de tipo y tiempo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ranking)

        // Inicializar las vistas y configurar los listeners (clics)
        setupViews()
        setupListeners()

        // 1. Calcular los datos del usuario actual y subirlos a la nube
        updateCurrentUserRanking()
        // 2. Cargar los datos globales para mostrar la tabla de posiciones
        loadRankingData(currentMetric)
    }

    private fun setupViews() {
        rankingRecyclerView = findViewById(R.id.ranking_recycler_view)
        metricToggleGroup = findViewById(R.id.metric_toggle_group)
        timeRadioGroup = findViewById(R.id.time_radio_group)

        // Configurar el adaptador de la lista (RecyclerView)
        rankingAdapter = RankingAdapter(emptyList())
        rankingRecyclerView.layoutManager = LinearLayoutManager(this)
        rankingRecyclerView.adapter = rankingAdapter
    }

    private fun setupListeners() {
        // Listener para el cambio de métrica (Kilómetros, Ritmo, Actividades, Territorios)
        metricToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val timeFiltersCard = findViewById<View>(R.id.card_time_filters)
                // Si se elige Territorios, ocultamos el filtro de tiempo (porque es acumulado total)
                if (checkedId == R.id.btn_territorios) {
                    timeFiltersCard.visibility = View.GONE
                    currentMetricType = "territories"
                } else {
                    timeFiltersCard.visibility = View.VISIBLE
                    currentMetricType = when (checkedId) {
                        R.id.btn_ritmo_promedio -> "pace"
                        R.id.btn_kilometros -> "kilometers"
                        R.id.btn_actividades -> "activities"
                        else -> "kilometers"
                    }
                }
                updateAndLoadRanking()
            }
        }

        // Listener para el cambio de periodo de tiempo (Semana, Mes, Año, Total)
        timeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMetricTime = when (checkedId) {
                R.id.radio_semana -> "week"
                R.id.radio_mes -> "month"
                R.id.radio_year -> "year"
                R.id.radio_total -> "total"
                else -> "week"
            }
            updateAndLoadRanking()
        }

        // Botón de retroceso
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    // Actualiza el string de la métrica actual (ej: "kilometers_month") y refresca la lista
    private fun updateAndLoadRanking() {
        currentMetric = if (currentMetricType == "territories") {
            "territories_total"
        } else {
            "${currentMetricType}_${currentMetricTime}"
        }
        loadRankingData(currentMetric)
    }

    /**
     * Calcula las estadísticas personales del usuario recorriendo sus entrenamientos
     * y las sube a una colección central llamada "ranking_users".
     */
    private fun updateCurrentUserRanking() = CoroutineScope(Dispatchers.IO).launch {
        val userId = auth.currentUser?.uid ?: return@launch
        try {
            // Obtener datos del perfil del usuario y zonas conquistadas
            val userDoc = firestore.collection("usuarios").document(userId).get().await()
            val zones = firestore.collection("zonasGlobales").whereEqualTo("propietarioUID", userId).get().await()

            // Obtener fechas actuales (Año, Mes, Semana) para filtrar
            val cal = Calendar.getInstance()
            val curYear = cal.get(Calendar.YEAR)
            val curMonth = cal.get(Calendar.MONTH)
            val curWeek = cal.get(Calendar.WEEK_OF_YEAR)

            // Formato de mes para guardado (Ej: "2024-3")
            val curYearMonth = String.format(Locale.US, "%d-%d", curYear, curMonth + 1)

            // Obtener todos los entrenamientos del usuario actual
            val trainingDocs = firestore.collection("usuarios").document(userId)
                .collection("entrenamientos").get().await()

            // Acumuladores de datos
            var tW = 0; var tD = 0.0; var tT = 0L // Totales
            var yW = 0; var yD = 0.0; var yT = 0L // Año
            var mW = 0; var mD = 0.0; var mT = 0L // Mes
            var wW = 0; var wD = 0.0; var wT = 0L // Semana

            val df = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

            // Procesar cada entrenamiento guardado en Firestore
            for (doc in trainingDocs.documents) {
                val dist = doc.getDouble("distancia") ?: 0.0
                val time = doc.getLong("tiempo") ?: 0L
                val fechaStr = doc.getString("fecha") ?: ""

                tW++; tD += dist; tT += time // Sumar siempre al acumulador total

                try {
                    val date = df.parse(fechaStr)
                    if (date != null) {
                        cal.time = date
                        // Clasificar el entrenamiento según la fecha actual
                        if (cal.get(Calendar.YEAR) == curYear) {
                            yW++; yD += dist; yT += time
                            if (cal.get(Calendar.MONTH) == curMonth) {
                                mW++; mD += dist; mT += time
                            }
                            if (cal.get(Calendar.WEEK_OF_YEAR) == curWeek) {
                                wW++; wD += dist; wT += time
                            }
                        }
                    }
                } catch (e: Exception) { }
            }

            // Crear el objeto con todos los resultados calculados
            val data = RankingEntry(
                userId = userId,
                name = userDoc.getString("nombre") ?: "Atleta",
                photoUrl = userDoc.getString("fotoUrl"),
                kilometers_total = tD / 1000.0,
                kilometers_year = yD / 1000.0,
                kilometers_month = mD / 1000.0,
                kilometers_week = wD / 1000.0,
                activities_total = tW,
                activities_year = yW,
                activities_month = mW,
                activities_week = wW,
                pace_total = calculatePace(tT, tD / 1000.0),
                pace_year = calculatePace(yT, yD / 1000.0),
                pace_month = calculatePace(mT, mD / 1000.0),
                pace_week = calculatePace(wT, wD / 1000.0),
                territories_total = zones.size(),
                last_update_month = curYearMonth,
                last_update_week = curWeek,
                last_update_year = curYear
            )

            // Guardar o actualizar los datos del usuario en la tabla de ranking global
            firestore.collection("ranking_users").document(userId).set(data, SetOptions.merge()).await()

            // Refrescar la vista en el hilo principal
            launch(Dispatchers.Main) { loadRankingData(currentMetric) }

        } catch (e: Exception) { Log.e("Ranking", "Error Update: ${e.message}") }
    }

    /**
     * Calcula el ritmo (segundos por kilómetro)
     */
    private fun calculatePace(timeMillis: Long, distanceKm: Double): Long {
        return if (distanceKm > 0.1) ((timeMillis / 1000.0) / distanceKm).toLong() else 0L
    }

    /**
     * Consulta Firestore para obtener el Top 100 de usuarios según la métrica elegida.
     */
    private fun loadRankingData(metric: String) {
        rankingAdapter.setMetric(metric)
        val cal = Calendar.getInstance()
        val curYear = cal.get(Calendar.YEAR)

        // Formatos posibles de mes para asegurar compatibilidad
        val monthShort = String.format(Locale.US, "%d-%d", curYear, cal.get(Calendar.MONTH) + 1)
        val monthLong = String.format(Locale.US, "%d-%02d", curYear, cal.get(Calendar.MONTH) + 1)

        var query: Query = firestore.collection("ranking_users")

        // Filtros de base de datos para que el ranking sea relevante (semana actual / año actual)
        if (metric.contains("week")) {
            query = query.whereEqualTo("last_update_year", curYear)
                .whereEqualTo("last_update_week", cal.get(Calendar.WEEK_OF_YEAR))
        } else if (metric.contains("year")) {
            query = query.whereEqualTo("last_update_year", curYear)
        }

        // Orden: Ascendente para Ritmo (menos es mejor) y Descendente para el resto (más es mejor)
        val direction = if (metric.startsWith("pace")) Query.Direction.ASCENDING else Query.Direction.DESCENDING

        query.orderBy(metric, direction)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val fullList = mutableListOf<RankingEntry>()
                for (doc in snapshot.documents) {
                    try {
                        val entry = doc.toObject(RankingEntry::class.java)
                        if (entry != null) {
                            val value = getMetricValue(entry, metric)

                            // Filtro manual de mes para manejar formatos "2024-3" y "2024-03"
                            if (metric.contains("month")) {
                                val userMonth = entry.last_update_month ?: ""
                                if (userMonth != monthShort && userMonth != monthLong) continue
                            }

                            // Solo mostrar usuarios con algún valor (evitar ceros)
                            if (value > 0) {
                                fullList.add(entry)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Ranking", "Error parse: ${e.message}")
                    }
                }
                // Actualizar la lista en el adaptador
                rankingAdapter.updateList(fullList)
                Log.d("Ranking", "Consulta para $metric trajo ${fullList.size} usuarios")
            }
            .addOnFailureListener { e ->
                Log.e("Ranking", "Error: ${e.message}")
            }
    }

    /**
     * Función auxiliar para extraer el valor correcto del objeto RankingEntry según el filtro
     */
    private fun getMetricValue(entry: RankingEntry, metric: String): Double {
        return when (metric) {
            "kilometers_month" -> entry.kilometers_month
            "kilometers_week" -> entry.kilometers_week
            "kilometers_year" -> entry.kilometers_year
            "kilometers_total" -> entry.kilometers_total
            "activities_month" -> entry.activities_month.toDouble()
            "activities_week" -> entry.activities_week.toDouble()
            "activities_year" -> entry.activities_year.toDouble()
            "activities_total" -> entry.activities_total.toDouble()
            "pace_month" -> entry.pace_month.toDouble()
            "pace_week" -> entry.pace_week.toDouble()
            "pace_year" -> entry.pace_year.toDouble()
            "pace_total" -> entry.pace_total.toDouble()
            "territories_total" -> entry.territories_total.toDouble()
            else -> 0.0
        }
    }
}