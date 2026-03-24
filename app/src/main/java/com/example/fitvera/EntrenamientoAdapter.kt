package com.example.fitvera


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fitvera.databinding.ItemTrainingCardBinding
import com.example.fitvera.databinding.ItemTrainingFuerzaBinding
import com.bumptech.glide.Glide
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Adaptador para mostrar una lista mixta de entrenamientos (Running y Fuerza).
 * Utiliza ViewHolders múltiples para cambiar el diseño según el tipo de ejercicio.
 */
class EntrenamientoAdapter(
    private val entrenamientoList: List<Entrenamiento>, // Lista de objetos Entrenamiento
    private val onItemClicked: (Entrenamiento) -> Unit // Callback para manejar clics en cada elemento
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Constantes para identificar el tipo de diseño (ViewType)
    private val TYPE_RUNNING = 1
    private val TYPE_STRENGTH = 2

    /**
     * Determina qué tipo de vista usar para un elemento específico de la lista.
     */
    override fun getItemViewType(position: Int): Int {
        // Si el tipo es "Fuerza" devuelve el ID de fuerza, de lo contrario devuelve Running
        return if (entrenamientoList[position].tipo == "Fuerza") TYPE_STRENGTH else TYPE_RUNNING
    }

    /**
     * Crea los contenedores visuales (ViewHolder) basándose en el ViewType.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_STRENGTH) {
            // Infla el diseño para ejercicios de gimnasio
            val binding = ItemTrainingFuerzaBinding.inflate(inflater, parent, false)
            FuerzaViewHolder(binding)
        } else {
            // Infla el diseño para carreras (con mapa)
            val binding = ItemTrainingCardBinding.inflate(inflater, parent, false)
            RunningViewHolder(binding)
        }
    }

    /**
     * Une los datos del objeto Entrenamiento con la vista del ViewHolder.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entrenamiento = entrenamientoList[position]
        // Dependiendo de la clase del holder, llama al método bind correspondiente
        if (holder is RunningViewHolder) {
            holder.bind(entrenamiento)
        } else if (holder is FuerzaViewHolder) {
            holder.bind(entrenamiento)
        }
    }

    override fun getItemCount(): Int = entrenamientoList.size

    // --- VIEWHOLDER PARA CARRERAS (Diseño con Mapa y Estadísticas de carrera) ---
    inner class RunningViewHolder(private val binding: ItemTrainingCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Configura el evento de clic en toda la tarjeta
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClicked(entrenamientoList[pos])
            }
        }

        /**
         * Asigna los datos de carrera a los campos de texto, mapa y fotos.
         */
        fun bind(entrenamiento: Entrenamiento) {
            binding.trainingName.text = entrenamiento.nombre
            binding.trainingDate.text = entrenamiento.fecha

            // Convierte distancia de metros a KM
            val distanceKm = entrenamiento.distancia / 1000.0
            binding.tvDistance.text = String.format(Locale.US, "%.2f km", distanceKm)

            // Calcula y formatea el ritmo (min/km)
            binding.tvPace.text = formatRitmo(entrenamiento.tiempo.toDouble(), entrenamiento.distancia)

            // Otras estadísticas
            binding.tvDesnivel.text = String.format(Locale.US, "%.0f m", entrenamiento.desnivel)
            binding.tvCalories.text = String.format(Locale.US, "%.0f kcal", entrenamiento.calorias)

            // Carga el mapa con el recorrido GPS y la foto si existe
            setupMap(binding.trainingMapPreview, entrenamiento.puntosRecorrido)
            setupPhoto(binding.trainingPhotoView, entrenamiento.fotoUrl)
        }
    }

    // --- VIEWHOLDER PARA FUERZA (Diseño optimizado para ejercicios de gimnasio) ---
    inner class FuerzaViewHolder(private val binding: ItemTrainingFuerzaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClicked(entrenamientoList[pos])
            }
        }

        fun bind(entrenamiento: Entrenamiento) {
            binding.trainingName.text = entrenamiento.nombre
            binding.trainingDateUserHeader.text = entrenamiento.fecha

            // Genera una cadena de texto acumulando los ejercicios (ej: "• Press Banca: 3x12 (60kg)")
            if (!entrenamiento.ejercicios.isNullOrEmpty()) {
                val resumen = entrenamiento.ejercicios!!.joinToString("\n") { ej ->
                    "• ${ej.nombre}: ${ej.series}x${ej.reps} (${ej.peso}kg)"
                }
                binding.tvResumenEjercicios.text = resumen
            } else {
                binding.tvResumenEjercicios.text = "Sin detalles de ejercicios"
            }
        }
    }

    // --- FUNCIONES AUXILIARES ---

    /**
     * Calcula el ritmo medio (Pace) en formato Minutos:Segundos por kilómetro.
     */
    private fun formatRitmo(tiempoMS: Double, distanciaMeters: Double): String {
        if (distanciaMeters <= 0.0 || tiempoMS <= 0.0) return "--:--/km"

        val tiempoSegundos = tiempoMS / 1000.0
        val ritmoSecPerKm = (tiempoSegundos / distanciaMeters) * 1000.0
        val totalSegundos = ritmoSecPerKm.toLong()

        val min = TimeUnit.SECONDS.toMinutes(totalSegundos)
        val seg = totalSegundos - TimeUnit.MINUTES.toSeconds(min)

        return String.format("%d:%02d/km", min, seg)
    }

    /**
     * Configura la vista de mapa (OSMDroid) y dibuja la línea del recorrido (Polyline).
     */
    private fun setupMap(mapView: MapView, puntos: List<HashMap<String, Any>>?) {
        mapView.setTileSource(TileSourceFactory.MAPNIK) // Usa el estilo de mapa estándar
        mapView.setMultiTouchControls(false) // Desactiva zoom táctil para no interferir con el scroll
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.overlays.clear()

        // Si no hay coordenadas GPS, oculta el mapa para ahorrar espacio
        if (puntos.isNullOrEmpty()) {
            mapView.visibility = View.GONE
            return
        }

        mapView.visibility = View.VISIBLE
        // Convierte los HashMaps de Firebase en objetos GeoPoint (Lat/Lon)
        val geoPoints = puntos.mapNotNull {
            try {
                GeoPoint(it["latitude"] as Double, it["longitude"] as Double)
            } catch (e: Exception) { null }
        }

        if (geoPoints.isNotEmpty()) {
            // Crea la línea azul del recorrido
            val recorrido = Polyline()
            recorrido.setPoints(geoPoints)
            mapView.overlays.add(recorrido)

            // Ajusta el zoom del mapa para que se vea todo el recorrido completo
            mapView.post {
                val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
                mapView.zoomToBoundingBox(boundingBox, true, 50)
            }
        }
    }

    /**
     * Carga una imagen desde una URL de internet usando la librería Glide.
     */
    private fun setupPhoto(imageView: ImageView, photoUrl: String?) {
        if (photoUrl.isNullOrEmpty()) {
            imageView.visibility = View.GONE // Oculta si no hay foto
        } else {
            imageView.visibility = View.VISIBLE
            Glide.with(imageView.context)
                .load(photoUrl)
                .centerCrop() // Ajusta la imagen al centro
                .into(imageView)
        }
    }
}