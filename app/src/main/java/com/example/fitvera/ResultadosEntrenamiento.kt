package com.example.fitvera

// Importaciones necesarias para UI, Firebase, Mapas, Gráficos y Networking
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.roundToInt
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class ResultadosEntrenamiento : AppCompatActivity() {

    // Referencias a componentes de la interfaz de usuario (XML)
    private lateinit var map: MapView
    private lateinit var tvDistancia: TextView
    private lateinit var tvDesnivel: TextView
    private lateinit var tvTiempo: TextView
    private lateinit var tvRitmo: TextView
    private lateinit var tvCalorias: TextView
    private lateinit var backButton: ImageView
    private lateinit var addPhotoButton: FloatingActionButton
    private lateinit var trainingPhotoView: ImageView
    private lateinit var trainingChart: LineChart
    private lateinit var tvChartTitle: TextView
    private lateinit var btnSaveTraining: Button
    private lateinit var etTrainingName: EditText
    private lateinit var difficultySeekBar: SeekBar

    // Variables para almacenar los datos recibidos del entrenamiento realizado
    private var distanciaFinal: Double = 0.0
    private var desnivelFinal: Double = 0.0
    private var tiempoFinal: Long = 0L
    private var ritmoFinal: Double = 0.0
    private var caloriasFinal: Double = 0.0
    private var puntosRecorridoFinal: ArrayList<GeoPoint> = ArrayList()
    private var ritmosNumericosFinal: ArrayList<Double> = ArrayList()
    private var selectedImageUri: Uri? = null // Almacena la ruta de la imagen seleccionada

    // Constantes e instancias de servicios
    private val PICK_IMAGE_REQUEST_CODE = 101
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // ------------------------------------------------------------------------------------------
    // CICLO DE VIDA Y SETUP INICIAL
    // ------------------------------------------------------------------------------------------

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configuración necesaria para cargar los mapas de OpenStreetMap
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.guardarentrenamiento)

        // Inicialización de Vistas vinculándolas con el layout XML
        map = findViewById(R.id.mapa)
        tvDistancia = findViewById(R.id.training_distance)
        tvDesnivel = findViewById(R.id.training_desnivel)
        tvTiempo = findViewById(R.id.training_time)
        tvRitmo = findViewById(R.id.training_pace)
        tvCalorias = findViewById(R.id.training_calories)
        backButton = findViewById(R.id.back_button)
        trainingChart = findViewById(R.id.training_chart)
        tvChartTitle = findViewById(R.id.chart_title)
        addPhotoButton = findViewById(R.id.add_photo_button)
        trainingPhotoView = findViewById(R.id.training_photo_view)
        btnSaveTraining = findViewById(R.id.add_training_button)
        etTrainingName = findViewById(R.id.training_name_input)
        difficultySeekBar = findViewById(R.id.difficulty_seekbar)

        // Recuperación de datos enviados a través del Intent desde la actividad anterior
        distanciaFinal = intent.getDoubleExtra("distancia", 0.0)
        desnivelFinal = intent.getDoubleExtra("desnivel", 0.0)
        tiempoFinal = intent.getLongExtra("tiempo", 0L)
        caloriasFinal = intent.getDoubleExtra("calorias", 0.0)

        // Si el entrenamiento viene de una fuente externa (Google Fit), se marca automáticamente
        if (intent.getBooleanExtra("esImportado", false)) {
            etTrainingName.setText("Importado de Google Fit")
        }

        // Obtención de la ruta GPS y la lista de ritmos por segmento
        puntosRecorridoFinal = intent.getSerializableExtra("puntosRecorrido") as? ArrayList<GeoPoint> ?: ArrayList()
        ritmosNumericosFinal = intent.getSerializableExtra("ritmosNumericos") as? ArrayList<Double> ?: ArrayList()

        // Cálculo del ritmo promedio final (segundos por kilómetro)
        ritmoFinal = if (distanciaFinal > 0) {
            (tiempoFinal.toDouble() / 1000.0) / (distanciaFinal / 1000.0)
        } else {
            0.0
        }

        // Muestra los valores numéricos en los TextViews formateados
        displayTrainingData()

        // Configuración de clics (Listeners)
        backButton.setOnClickListener { finish() }

        // Abrir la galería para añadir una foto al entrenamiento
        addPhotoButton.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST_CODE)
        }

        // Quitar la foto seleccionada al tocar la vista previa
        trainingPhotoView.setOnClickListener {
            trainingPhotoView.visibility = View.GONE
            addPhotoButton.visibility = View.VISIBLE
            selectedImageUri = null
        }

        // Botón para validar y guardar el entrenamiento en la base de datos
        btnSaveTraining.setOnClickListener {
            val trainingName = etTrainingName.text.toString()
            val difficulty = mapSeekBarProgressToDifficulty(difficultySeekBar.progress)
            if (trainingName.isBlank()) {
                Toast.makeText(this, "Por favor, ingresa un nombre", Toast.LENGTH_SHORT).show()
            } else {
                saveTrainingToFirebase(trainingName, difficulty)
            }
        }

        // Inicializar los elementos visuales complejos
        setupMap()
        setupChart()
    }

    // ------------------------------------------------------------------------------------------
    // FUNCIONES DE UTILIDAD Y CÁLCULO
    // ------------------------------------------------------------------------------------------

    // Mapea la posición de la SeekBar (0-4) a un nombre de zona de esfuerzo
    private fun mapSeekBarProgressToDifficulty(progress: Int): String {
        return when (progress) {
            0 -> "Zona 1"
            1 -> "Zona 2"
            2 -> "Zona 3"
            3 -> "Zona 4"
            4 -> "Zona 5"
            else -> "Zona 1"
        }
    }

    // Formatea los datos de distancia, tiempo y ritmo para mostrarlos en la UI
    private fun displayTrainingData() {
        tvDistancia.text = "%.2f km".format(distanciaFinal / 1000)
        tvDesnivel.text = "%.0f m".format(desnivelFinal)
        val horas = TimeUnit.MILLISECONDS.toHours(tiempoFinal)
        val minutos = TimeUnit.MILLISECONDS.toMinutes(tiempoFinal) % 60
        val segundos = TimeUnit.MILLISECONDS.toSeconds(tiempoFinal) % 60
        tvTiempo.text = String.format("%02d:%02d:%02d", horas, minutos, segundos)
        val min = (ritmoFinal / 60).toInt()
        val seg = (ritmoFinal % 60).roundToInt()
        tvRitmo.text = "$min:${String.format("%02d", seg)} min/km"
        tvCalorias.text = "%.0f kcal".format(caloriasFinal)
    }

    // Algoritmo para calcular los puntos de experiencia/liga basados en rendimiento
    private fun calcularPuntuacionEsfuerzo(distanciaMetros: Double, ritmoSegundosPorKm: Double): Int {
        if (distanciaMetros <= 0 || ritmoSegundosPorKm <= 0) return 0
        val distanciaKm = distanciaMetros / 1000.0
        val ritmoMinutos = ritmoSegundosPorKm / 60.0

        // Fórmula de puntos: (KM * 100) ajustado por intensidad (más rápido = más puntos)
        val puntosBase = distanciaKm * 100
        val multiplicadorIntensidad = 10.0 / ritmoMinutos
        return (puntosBase * multiplicadorIntensidad).roundToInt()
    }

    // ------------------------------------------------------------------------------------------
    // CONFIGURACIÓN DE MAPA Y GRÁFICO
    // ------------------------------------------------------------------------------------------

    // Configura OSMDroid para dibujar la polilínea del recorrido en el mapa
    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        if (puntosRecorridoFinal.isNotEmpty()) {
            map.visibility = View.VISIBLE
            val recorrido = Polyline()
            recorrido.setPoints(puntosRecorridoFinal)
            recorrido.outlinePaint.color = android.graphics.Color.BLUE
            recorrido.outlinePaint.strokeWidth = 7f
            map.overlays.add(recorrido)
            map.controller.setZoom(16.0)
            map.controller.setCenter(puntosRecorridoFinal[puntosRecorridoFinal.size / 2])
            map.invalidate()
        } else {
            map.visibility = View.GONE
        }
    }

    // Configura MPAndroidChart para mostrar la evolución del ritmo por cada 100m
    private fun setupChart() {
        if (ritmosNumericosFinal.isNotEmpty()) {
            tvChartTitle.visibility = View.VISIBLE
            tvChartTitle.text = "Ritmo por Segmento (Toca para ver detalle)"
            trainingChart.visibility = View.VISIBLE

            // Ajuste dinámico de colores para temas claro/oscuro
            val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val primaryColor = if (isNightMode) Color.WHITE else Color.parseColor("#1976D2")
            val axisTextColor = if (isNightMode) Color.WHITE else Color.parseColor("#424242")

            // Creación de las entradas del gráfico (X = distancia acumulada, Y = ritmo)
            val entries = ArrayList<Entry>()
            for (i in ritmosNumericosFinal.indices) {
                entries.add(Entry((i * 0.1f) + 0.1f, ritmosNumericosFinal[i].toFloat()))
            }

            val dataSet = LineDataSet(entries, "Ritmo")

            // Estilización de la línea del gráfico
            dataSet.color = primaryColor
            dataSet.setCircleColor(primaryColor)
            dataSet.highLightColor = primaryColor
            dataSet.valueTextColor = axisTextColor
            dataSet.lineWidth = 2.5f
            dataSet.setDrawCircles(false)
            dataSet.valueTextSize = 0f
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // Suavizado de curva
            dataSet.setDrawFilled(true)
            dataSet.fillColor = primaryColor
            dataSet.fillAlpha = 40

            dataSet.isHighlightEnabled = true
            dataSet.setDrawHighlightIndicators(true)

            val lineData = LineData(dataSet)
            trainingChart.data = lineData

            // Añadir marcador personalizado al tocar un punto del gráfico
            val marker = CustomMarkerView(this, R.layout.chart_marker_view)
            marker.chartView = trainingChart
            trainingChart.marker = marker

            // Configuración estética general del gráfico
            trainingChart.description.isEnabled = false
            trainingChart.legend.isEnabled = false
            trainingChart.setBackgroundColor(Color.TRANSPARENT)
            trainingChart.setDrawGridBackground(false)
            trainingChart.setDrawBorders(false)

            // Configuración del eje X (Distancia)
            val xAxis = trainingChart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = axisTextColor
            xAxis.axisLineColor = axisTextColor
            xAxis.setDrawGridLines(false)
            xAxis.valueFormatter = object : ValueFormatter() {
                private val format = DecimalFormat("##0.0")
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return "${format.format(value)} km"
                }
            }

            // Configuración del eje Y (Ritmo - Invertido porque menos tiempo es mejor ritmo)
            val yAxisLeft = trainingChart.axisLeft
            yAxisLeft.textColor = axisTextColor
            yAxisLeft.axisLineColor = axisTextColor
            yAxisLeft.gridColor = if (isNightMode) Color.parseColor("#33FFFFFF") else Color.parseColor("#22000000")
            yAxisLeft.isInverted = true
            yAxisLeft.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val minutes = (value / 60).toInt()
                    val seconds = (value % 60).roundToInt()
                    return "${minutes}:${String.format("%02d", seconds)}"
                }
            }

            trainingChart.axisRight.isEnabled = false
            trainingChart.animateX(1500)
            trainingChart.invalidate()
        } else {
            trainingChart.visibility = View.GONE
            tvChartTitle.text = "No hay datos de ritmo disponibles"
        }
    }

    // ------------------------------------------------------------------------------------------
    // GESTIÓN DE FIREBASE Y GUARDADO
    // ------------------------------------------------------------------------------------------

    // Resultado de la selección de imagen en la galería
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            trainingPhotoView.visibility = View.VISIBLE
            addPhotoButton.visibility = View.GONE
            trainingPhotoView.setImageURI(selectedImageUri)
        }
    }

    // Orquestador del guardado: Sube imagen a Cloudinary (si existe) y luego datos a Firestore
    private fun saveTrainingToFirebase(trainingName: String, difficulty: String) {
        val userId = auth.currentUser?.uid ?: return
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Guardando entrenamiento y puntos...")
            setCancelable(false)
            show()
        }

        // Conversión de lista de GeoPoints a lista de Mapas para compatibilidad con Firestore
        val puntosMapa = puntosRecorridoFinal.map {
            hashMapOf<String, Any>("latitude" to it.latitude, "longitude" to it.longitude)
        }

        if (selectedImageUri != null) {
            // Si hay foto, se procesa la subida mediante OkHttp a Cloudinary
            val inputStream = contentResolver.openInputStream(selectedImageUri!!)
            val requestBody = inputStream?.readBytes()?.let { bytes ->
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "training_${UUID.randomUUID()}.jpg", RequestBody.create("image/*".toMediaTypeOrNull(), bytes))
                    .addFormDataPart("upload_preset", "android_uploads")
                    .build()
            }

            val request = Request.Builder().url("https://api.cloudinary.com/v1_1/dcryq1boy/image/upload").post(requestBody!!).build()
            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    runOnUiThread { progressDialog.dismiss(); Toast.makeText(this@ResultadosEntrenamiento, "Error al subir foto", Toast.LENGTH_SHORT).show() }
                }
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        // Obtención de la URL segura de la imagen subida
                        val photoUrl = JSONObject(response.body?.string() ?: "").getString("secure_url")
                        runOnUiThread { guardarDatosFirestore(userId, trainingName, difficulty, puntosMapa, photoUrl, progressDialog) }
                    } else {
                        runOnUiThread { progressDialog.dismiss(); Toast.makeText(this@ResultadosEntrenamiento, "Error Cloudinary", Toast.LENGTH_SHORT).show() }
                    }
                }
            })
        } else {
            // Si no hay foto, se guarda directamente en Firestore
            guardarDatosFirestore(userId, trainingName, difficulty, puntosMapa, "", progressDialog)
        }
    }

    // Inserta el documento de entrenamiento y dispara la lógica de gamificación
    private fun guardarDatosFirestore(userId: String, name: String, diff: String, puntos: List<HashMap<String, Any>>, url: String, pd: ProgressDialog) {
        val fechaReg = SimpleDateFormat("dd-MM-yyyy", Locale("es", "ES")).format(Date())
        val fechaRacha = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Cálculo de puntos de esfuerzo para la tabla de clasificación
        val puntuacionLograda = calcularPuntuacionEsfuerzo(distanciaFinal, ritmoFinal)

        val nuevo = Entrenamiento(
            userId = userId,
            nombre = name,
            fecha = fechaReg,
            distancia = distanciaFinal,
            desnivel = desnivelFinal,
            tiempo = tiempoFinal,
            ritmoPromedio = ritmoFinal,
            calorias = caloriasFinal,
            dificultad = diff,
            fotoUrl = url,
            puntosRecorrido = puntos,
            ritmosNumericos = ritmosNumericosFinal,
            puntuacion = puntuacionLograda
        )

        // Escritura en la subcolección de entrenamientos del usuario
        db.collection("usuarios").document(userId).collection("entrenamientos").add(nuevo)
            .addOnSuccessListener {

                // Actualización de la colección de ranking para las ligas mensuales
                actualizarPuntuacionMensualRanking(userId, puntuacionLograda)

                // Lógica para registrar las zonas del mapa conquistadas
                if (puntosRecorridoFinal.isNotEmpty()) {
                    conquerRouteZones(userId, fechaRacha)
                    db.collection("usuarios").document(userId).collection("mapaConquista")
                        .document(calculateGeographicZoneKey(puntosRecorridoFinal.first()))
                        .set(mapOf("ultimaFecha" to fechaRacha))
                }

                // Recálculo asíncrono de las estadísticas de perfil (kilómetros totales, etc)
                CoroutineScope(Dispatchers.IO).launch {
                    RankingCalculator(db).recalculateUserStats(userId)
                }

                pd.dismiss()
                Toast.makeText(this, "¡Guardado! Puntos obtenidos: $puntuacionLograda", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                pd.dismiss()
                Toast.makeText(this, "Error al guardar en Firebase", Toast.LENGTH_SHORT).show()
            }
    }

    // Actualiza los campos acumulativos en el perfil de ranking del usuario
    private fun actualizarPuntuacionMensualRanking(userId: String, puntosNuevos: Int) {
        val rankingRef = db.collection("ranking_users").document(userId)
        val cal = Calendar.getInstance()
        val curYear = cal.get(Calendar.YEAR)
        val curMonth = cal.get(Calendar.MONTH) + 1 // Enero es 0 en Java, sumamos 1

        // Formato unificado de fecha para control de ranking
        val monthStr = String.format(Locale.US, "%d-%d", curYear, curMonth)

        // Incrementos atómicos en Firestore
        val updates = hashMapOf(
            "score_total_month" to FieldValue.increment(puntosNuevos.toLong()),
            "activities_month" to FieldValue.increment(1),
            "activities_year" to FieldValue.increment(1),
            "activities_week" to FieldValue.increment(1),
            "kilometers_month" to FieldValue.increment(distanciaFinal / 1000.0),
            "kilometers_year" to FieldValue.increment(distanciaFinal / 1000.0),
            "kilometers_week" to FieldValue.increment(distanciaFinal / 1000.0),
            "last_update_month" to monthStr,
            "last_update_year" to curYear,
            "last_update_week" to cal.get(Calendar.WEEK_OF_YEAR)
        )

        rankingRef.update(updates as Map<String, Any>)
            .addOnFailureListener {
                // Si el usuario no tiene documento en ranking_users, se crea uno nuevo
                rankingRef.set(updates, SetOptions.merge())
            }
    }

    // ------------------------------------------------------------------------------------------
    // LÓGICA DE CONQUISTA DE ZONAS
    // ------------------------------------------------------------------------------------------

    // Crea una clave única para cada cuadrante de 100m aproximadamente basada en coordenadas
    private fun calculateGeographicZoneKey(point: GeoPoint): String {
        val latStr = String.format(Locale.US, "%.3f", point.latitude).replace(".", "_").replace("-", "NEG")
        val lonStr = String.format(Locale.US, "%.3f", point.longitude).replace(".", "_").replace("-", "NEG")
        return "ZONE_${latStr}_${lonStr}"
    }

    // Identifica todas las zonas distintas que el usuario ha atravesado en su ruta
    private fun conquerRouteZones(currentUserId: String, fechaRacha: String) {
        if (puntosRecorridoFinal.isEmpty()) return
        val zonesToConquer = mutableSetOf<String>()
        zonesToConquer.add(calculateGeographicZoneKey(puntosRecorridoFinal.first()))
        var distanceTraversed = 0.0
        // Se recorren los puntos y se marca una nueva zona cada 100 metros recorridos
        for (i in 1 until puntosRecorridoFinal.size) {
            distanceTraversed += puntosRecorridoFinal[i].distanceToAsDouble(puntosRecorridoFinal[i - 1])
            if (distanceTraversed >= 100.0) {
                zonesToConquer.add(calculateGeographicZoneKey(puntosRecorridoFinal[i]))
                distanceTraversed = 0.0
            }
        }
        // Registra la conquista en la base de datos global
        for (zoneKey in zonesToConquer) {
            claimGlobalZone(currentUserId, zoneKey, fechaRacha)
        }
    }

    // Realiza una transacción para actualizar el dueño de una zona en el mapa global
    private fun claimGlobalZone(currentUserId: String, mapZoneKey: String, fechaRacha: String) {
        val zonaGlobalRef = db.collection("zonasGlobales").document(mapZoneKey)
        db.runTransaction { transaction ->
            val doc = transaction.get(zonaGlobalRef)
            val currentScore = doc.getLong("puntuacionConquista")?.toInt() ?: 0
            val update = hashMapOf(
                "propietarioUID" to currentUserId,
                "puntuacionConquista" to currentScore + 1,
                "fechaUltimaRacha" to fechaRacha
            )
            transaction.set(zonaGlobalRef, update, SetOptions.merge())
            null
        }.addOnFailureListener { e ->
            Log.e("Conquista", "Error en zona $mapZoneKey: ${e.message}")
        }
    }
}

// Vista personalizada para mostrar información detallada al tocar puntos del gráfico
class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
    private val tvContent: TextView = findViewById(R.id.tvContent)

    // Actualiza el texto del marcador con la distancia y el ritmo exacto del punto seleccionado
    override fun refreshContent(e: Entry, highlight: Highlight) {
        val totalSeconds = e.y.toDouble()
        val minutes = (totalSeconds / 60).toInt()
        val seconds = (totalSeconds % 60).roundToInt()
        tvContent.text = "Km ${String.format("%.1f", e.x)}: ${minutes}:${String.format("%02d", seconds)} min/km"
        super.refreshContent(e, highlight)
    }

    // Posicionamiento del marcador sobre el gráfico
    override fun getOffset(): MPPointF {
        // Centra horizontalmente el marcador y lo coloca encima del punto
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}