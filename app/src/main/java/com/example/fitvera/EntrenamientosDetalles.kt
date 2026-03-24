package com.example.fitvera


import Ejercicio
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
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
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Actividad que muestra los detalles de un entrenamiento específico.
 * Soporta visualización de mapas, fotos, gráficas de ritmo y listas de ejercicios de fuerza.
 */
class EntrenamientoDetalles : AppCompatActivity() {

    // Declaración de variables de la interfaz (UI)
    private lateinit var btnBack: ImageView
    private lateinit var tvTrainingName: TextView
    private lateinit var tvTrainingDate: TextView
    private lateinit var tvTrainingDifficulty: TextView
    private lateinit var tvDetailTime: TextView
    private lateinit var tvDetailDistance: TextView
    private lateinit var tvDetailPace: TextView
    private lateinit var tvDetailDesnivel: TextView
    private lateinit var tvDetailCalories: TextView
    private lateinit var viewPager: ViewPager2       // Para deslizar entre foto y mapa
    private lateinit var trainingChart: LineChart     // Gráfica de rendimiento
    private lateinit var tvChartTitle: TextView
    private lateinit var layoutFuerza: View          // Contenedor específico para pesas
    private lateinit var rvEjercicios: RecyclerView   // Lista de ejercicios realizados

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.entrenamientosdetalles)

        // 1. Vinculación de objetos con los IDs del XML (Layout)
        btnBack = findViewById(R.id.btn_back)
        tvTrainingName = findViewById(R.id.training_name_details)
        tvTrainingDate = findViewById(R.id.training_date_details)
        tvTrainingDifficulty = findViewById(R.id.tv_detail_difficulty)
        tvDetailTime = findViewById(R.id.tv_detail_time)
        tvDetailDistance = findViewById(R.id.tv_detail_distance)
        tvDetailPace = findViewById(R.id.tv_detail_pace)
        tvDetailDesnivel = findViewById(R.id.tv_detail_desnivel)
        tvDetailCalories = findViewById(R.id.tv_detail_calories)
        viewPager = findViewById(R.id.media_view_pager)
        trainingChart = findViewById(R.id.training_chart)
        tvChartTitle = findViewById(R.id.chart_title)
        layoutFuerza = findViewById(R.id.layout_fuerza_detalles)
        rvEjercicios = findViewById(R.id.rv_ejercicios_detalles)

        // Inicialización: Ocultamos la gráfica por defecto
        trainingChart.visibility = View.GONE
        tvChartTitle.visibility = View.GONE

        // 2. Recuperar el objeto "Entrenamiento" enviado desde la actividad anterior
        val entrenamiento = intent.getSerializableExtra("entrenamiento") as? Entrenamiento

        // Si el objeto existe, mostramos los datos; si no, cerramos la pantalla
        entrenamiento?.let { displayEntrenamientoDetails(it) } ?: finish()

        // Configurar botón de regreso
        btnBack.setOnClickListener { finish() }
    }

    /**
     * Llena la interfaz con los datos del objeto Entrenamiento.
     * Configura la UI de forma distinta si es "Fuerza" o "Carrera".
     */
    @SuppressLint("SetTextI18n")
    private fun displayEntrenamientoDetails(entrenamiento: Entrenamiento) {
        // Datos comunes a todos los tipos
        tvTrainingName.text = entrenamiento.nombre
        tvTrainingDate.text = entrenamiento.fecha
        tvTrainingDifficulty.text = entrenamiento.dificultad

        // Conversión de milisegundos a formato HH:mm:ss
        val horas = TimeUnit.MILLISECONDS.toHours(entrenamiento.tiempo)
        val minutos = TimeUnit.MILLISECONDS.toMinutes(entrenamiento.tiempo) % 60
        val segundos = TimeUnit.MILLISECONDS.toSeconds(entrenamiento.tiempo) % 60
        tvDetailTime.text = String.format("%02d:%02d:%02d", horas, minutos, segundos)

        val gridMetrics = findViewById<GridLayout>(R.id.grid_metrics)
        val cardTime = findViewById<View>(R.id.card_time_container)
        val cardDiff = findViewById<View>(R.id.card_difficulty_container)

        // --- CASO A: ENTRENAMIENTO DE FUERZA (Pesas/Gimnasio) ---
        if (entrenamiento.tipo == "Fuerza") {
            layoutFuerza.visibility = View.VISIBLE
            tvChartTitle.visibility = View.GONE
            trainingChart.visibility = View.GONE

            // Ocultamos métricas que no aplican a fuerza (distancia, ritmo, etc.)
            findViewById<View>(R.id.card_distance_container).visibility = View.GONE
            findViewById<View>(R.id.card_pace_container).visibility = View.GONE
            findViewById<View>(R.id.card_desnivel_container).visibility = View.GONE
            findViewById<View>(R.id.card_calories_container).visibility = View.GONE

            // Reorganizamos el Grid para que el tiempo y dificultad ocupen todo el ancho
            gridMetrics.removeView(cardTime)
            gridMetrics.removeView(cardDiff)
            val newParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(10, 10, 10, 10)
            }
            gridMetrics.addView(cardTime, newParams)
            gridMetrics.addView(cardDiff, newParams)

            // Configuramos la lista (RecyclerView) de ejercicios (Press banca, sentadilla...)
            rvEjercicios.layoutManager = LinearLayoutManager(this)
            rvEjercicios.adapter = EjerciciosDetalleAdapter(entrenamiento.ejercicios ?: emptyList())

        }
        // --- CASO B: ENTRENAMIENTO DE CARRERA/GPS ---
        else {
            tvDetailCalories.text = "${entrenamiento.calorias.toInt()} kcal"
            tvDetailDistance.text = String.format("%.2f km", entrenamiento.distancia / 1000)
            tvDetailPace.text = formatRitmo(entrenamiento.ritmoPromedio)
            tvDetailDesnivel.text = "${entrenamiento.desnivel.toInt()} m"

            // Configuramos la gráfica de ritmos por kilómetro
            setupChart(entrenamiento.ritmosNumericos)
        }

        // Carga la foto y/o el mapa en el ViewPager
        setupMedia(entrenamiento)
    }

    /**
     * Configura la gráfica de líneas (MPAndroidChart) para mostrar el rendimiento.
     */
    private fun setupChart(ritmos: List<Double>?) {
        if (!ritmos.isNullOrEmpty()) {
            tvChartTitle.visibility = View.VISIBLE
            tvChartTitle.text = "Ritmo por Segmento (Toca para detalle)"
            trainingChart.visibility = View.VISIBLE

            // Creamos los puntos de la gráfica (X = distancia en km, Y = ritmo en seg)
            val entries = ArrayList<Entry>()
            for (i in ritmos.indices) {
                entries.add(Entry((i * 0.1f) + 0.1f, ritmos[i].toFloat()))
            }

            // Configuración visual de la línea de la gráfica
            val dataSet = LineDataSet(entries, "Ritmo")
            dataSet.color = Color.WHITE
            dataSet.lineWidth = 2.5f
            dataSet.setDrawCircles(false)
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // Línea curva suave
            dataSet.setDrawFilled(true) // Relleno bajo la línea
            dataSet.fillAlpha = 30
            dataSet.highLightColor = Color.WHITE // Color de la línea de selección

            val lineData = LineData(dataSet)
            trainingChart.data = lineData

            // Configuramos el "MarkerView" (la burbuja que sale al tocar la gráfica)
            val marker = CustomMarkerView(this, R.layout.chart_marker_view)
            marker.chartView = trainingChart
            trainingChart.marker = marker

            trainingChart.description.isEnabled = false
            trainingChart.legend.isEnabled = false

            // Configuración del Eje X (Distancia)
            val xAxis = trainingChart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.WHITE
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return String.format("%.1f km", value)
                }
            }

            // Configuración del Eje Y (Ritmo)
            val yAxisLeft = trainingChart.axisLeft
            yAxisLeft.textColor = Color.WHITE
            yAxisLeft.isInverted = true // Invertido porque en running, menos tiempo es "más arriba"
            yAxisLeft.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val minutes = (value / 60).toInt()
                    val seconds = (value % 60).roundToInt()
                    return "${minutes}:${String.format("%02d", seconds)}"
                }
            }

            trainingChart.axisRight.isEnabled = false
            trainingChart.animateX(1200) // Animación de entrada
            trainingChart.invalidate() // Refrescar
        }
    }

    /**
     * Configura el carrusel de medios (ViewPager2).
     * Puede contener un fragmento con la foto y otro con el mapa OSMDroid.
     */
    private fun setupMedia(entrenamiento: Entrenamiento) {
        val fragments = mutableListOf<Fragment>()

        // Si hay foto, añadimos el PhotoFragment
        if (!entrenamiento.fotoUrl.isNullOrEmpty()) {
            fragments.add(PhotoFragment.newInstance(entrenamiento.fotoUrl!!))
        }

        // Si hay coordenadas de GPS, añadimos el MapFragment
        if (!entrenamiento.puntosRecorrido.isNullOrEmpty()) {
            fragments.add(MapFragment.newInstance(ArrayList(entrenamiento.puntosRecorrido!!)))
        }

        if (fragments.isNotEmpty()) {
            viewPager.visibility = View.VISIBLE
            viewPager.adapter = MediaPagerAdapter(this, fragments)
        } else {
            viewPager.visibility = View.GONE
        }
    }

    /**
     * Formatea segundos a formato mm:ss min/km
     */
    private fun formatRitmo(ritmoSegundos: Double): String {
        if (ritmoSegundos <= 0) return "--:--"
        val min = (ritmoSegundos / 60).toInt()
        val seg = (ritmoSegundos % 60).roundToInt()
        return "$min:${String.format("%02d", seg)} min/km"
    }

    /**
     * Clase personalizada para el marcador flotante que aparece al tocar la gráfica.
     */
    inner class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
        private val tvContent: TextView = findViewById(R.id.tvContent)

        // Se ejecuta cada vez que tocas un punto nuevo en la gráfica
        override fun refreshContent(e: Entry, highlight: Highlight) {
            val totalSeconds = e.y.toDouble()
            val minutes = (totalSeconds / 60).toInt()
            val seconds = (totalSeconds % 60).roundToInt()
            // Muestra el Km y el Ritmo exacto en ese punto
            tvContent.text = "Km ${String.format("%.1f", e.x)}: ${minutes}:${String.format("%02d", seconds)} min/km"
            super.refreshContent(e, highlight)
        }

        // Ajusta la posición de la burbuja para que no se salga de la pantalla
        override fun getOffset(): MPPointF {
            return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
        }
    }
}

/**
 * Adaptador para la lista de ejercicios de fuerza (RecyclerView).
 */
class EjerciciosDetalleAdapter(private val ejercicios: List<Ejercicio>) :
    RecyclerView.Adapter<EjerciciosDetalleAdapter.EjHolder>() {

    class EjHolder(v: View) : RecyclerView.ViewHolder(v) {
        val nombre: TextView = v.findViewById(R.id.tv_ej_nombre)
        val detalles: TextView = v.findViewById(R.id.tv_ej_detalles)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EjHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ejercicio_detalle, parent, false)
        return EjHolder(v)
    }

    override fun onBindViewHolder(holder: EjHolder, position: Int) {
        val ej = ejercicios[position]
        holder.nombre.text = ej.nombre
        holder.detalles.text = "${ej.series} series x ${ej.reps} reps • ${ej.peso} kg"
    }

    override fun getItemCount() = ejercicios.size
}

/**
 * Adaptador para el ViewPager2 que gestiona los fragmentos de Foto y Mapa.
 */
class MediaPagerAdapter(activity: FragmentActivity, private val fragments: List<Fragment>) :
    FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = fragments.size
    override fun createFragment(position: Int): Fragment = fragments[position]
}