package com.example.fitvera

// Importaciones necesarias para UI, Firebase, Mapas y utilidades de tiempo
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

class MostrarEntrenamientos : AppCompatActivity() {

    // Vistas principales
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EntrenamientosAdapter
    private lateinit var searchBar: EditText
    private lateinit var addTrainingButton: MaterialButton
    private lateinit var userToLoadId: String // UID del usuario cuyos entrenamientos queremos ver

    // Firebase y Listas de datos
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val entrenamientosList = mutableListOf<Entrenamiento>() // Lista original (maestra)
    private val filteredList = mutableListOf<Entrenamiento>() // Lista que se muestra (filtrada)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configuración de OSMDroid para la caché de mapas
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_entrenamientos)

        // 1. VINCULACIÓN DE VISTAS
        recyclerView = findViewById(R.id.trainings_list)
        searchBar = findViewById(R.id.search_bar)
        addTrainingButton = findViewById(R.id.add_training_button)

        // 2. LÓGICA DE USUARIO: ¿Estamos viendo mis entrenamientos o los de otro?
        userToLoadId = intent.getStringExtra("user_id") ?: auth.currentUser?.uid ?: ""

        // Si no es mi perfil, ocultamos el botón de añadir entrenamiento
        if (userToLoadId != auth.currentUser?.uid) {
            addTrainingButton.visibility = View.GONE
        }

        // 3. CONFIGURACIÓN DEL RECYCLERVIEW
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EntrenamientosAdapter(filteredList)
        recyclerView.adapter = adapter

        // 4. LISTENERS DE NAVEGACIÓN Y BÚSQUEDA
        addTrainingButton.setOnClickListener {
            startActivity(Intent(this, RegistrarEntrenamiento::class.java))
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // Filtro de búsqueda en tiempo real
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
        })

        cargarEntrenamientos()
    }

    /**
     * Descarga la colección de entrenamientos del usuario desde Firestore.
     */
    private fun cargarEntrenamientos() {
        if (userToLoadId.isEmpty()) return

        db.collection("usuarios").document(userToLoadId).collection("entrenamientos")
            .get()
            .addOnSuccessListener { documents ->
                entrenamientosList.clear()
                for (doc in documents) {
                    try {
                        val entrenamiento = doc.toObject(Entrenamiento::class.java)
                        entrenamiento.id = doc.id
                        entrenamientosList.add(entrenamiento)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // Ordenar por fecha (descendente: los más nuevos primero)
                entrenamientosList.sortWith(compareByDescending {
                    try {
                        val fechaStr = it.fecha ?: ""
                        val formato = if (fechaStr.contains("/")) "dd/MM/yyyy" else "dd-MM-yyyy"
                        java.text.SimpleDateFormat(formato, Locale.getDefault()).parse(fechaStr)
                    } catch (e: Exception) { null }
                })

                // Actualizar la lista filtrada y el adaptador
                filteredList.clear()
                filteredList.addAll(entrenamientosList)
                adapter.notifyDataSetChanged()
            }
    }

    /**
     * Filtra la lista por nombre, fecha o tipo de actividad.
     */
    private fun filterList(query: String) {
        val lowerCaseQuery = query.lowercase()
        filteredList.clear()
        filteredList.addAll(
            entrenamientosList.filter {
                (it.nombre ?: "").lowercase().contains(lowerCaseQuery) ||
                        (it.fecha ?: "").lowercase().contains(lowerCaseQuery) ||
                        (it.tipo ?: "").lowercase().contains(lowerCaseQuery)
            }
        )
        adapter.notifyDataSetChanged()
    }

    /**
     * ADAPTADOR: Gestiona cómo se dibujan los elementos en la lista.
     * Soporta dos tipos de vista: Carrera (Running) y Fuerza (Strength).
     */
    inner class EntrenamientosAdapter(private val entrenamientos: MutableList<Entrenamiento>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_RUNNING = 1
        private val TYPE_STRENGTH = 2

        // Determina qué diseño usar según el tipo de entrenamiento
        override fun getItemViewType(position: Int): Int {
            return if (entrenamientos[position].tipo == "Fuerza") TYPE_STRENGTH else TYPE_RUNNING
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_STRENGTH) {
                StrengthViewHolder(inflater.inflate(R.layout.item_training_fuerza, parent, false))
            } else {
                RunningViewHolder(inflater.inflate(R.layout.item_training_card, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val entrenamiento = entrenamientos[position]
            val context = holder.itemView.context
            val isMyProfile = userToLoadId == auth.currentUser?.uid // Permiso para borrar

            if (holder is StrengthViewHolder) {
                // --- LÓGICA PARA ITEMS DE FUERZA ---
                holder.btnDelete.visibility = if (isMyProfile) View.VISIBLE else View.GONE
                holder.tvName.text = entrenamiento.nombre ?: "Entrenamiento"
                holder.tvDate.text = entrenamiento.fecha ?: ""
                holder.tvDuracion.text = "${TimeUnit.MILLISECONDS.toMinutes(entrenamiento.tiempo)} min"
                holder.tvDificultad.text = entrenamiento.dificultad ?: "Media"

                // Crea un resumen textual de los ejercicios realizados
                holder.tvResumen.text = entrenamiento.ejercicios?.joinToString("\n") { ej ->
                    "• ${ej.nombre ?: "Ej"}: ${ej.series ?: 0}x${ej.reps ?: 0} (${ej.peso ?: 0.0}kg)"
                } ?: context.getString(R.string.no_exercises)

                holder.btnDelete.setOnClickListener { mostrarDialogoBorrado(entrenamiento, position) }

            } else if (holder is RunningViewHolder) {
                // --- LÓGICA PARA ITEMS DE CARRERA/GPS ---
                holder.btnDelete.visibility = if (isMyProfile) View.VISIBLE else View.GONE
                holder.tvName.text = entrenamiento.nombre ?: "Carrera"
                holder.tvDate.text = entrenamiento.fecha ?: ""
                holder.tvDistance.text = String.format(Locale.US, "%.2f km", (entrenamiento.distancia / 1000.0))
                holder.tvPace.text = formatRitmo(entrenamiento.ritmoPromedio)
                holder.tvDesnivel.text = "${entrenamiento.desnivel.toInt()} m"
                holder.tvCalories.text = "${entrenamiento.calorias.toInt()} kcal"

                // Si hay coordenadas GPS, mostramos la miniatura del mapa
                if (!entrenamiento.puntosRecorrido.isNullOrEmpty()) {
                    holder.mapPreview.visibility = View.VISIBLE
                    try { setupMiniMap(holder.mapPreview, entrenamiento.puntosRecorrido) } catch (e: Exception) { holder.mapPreview.visibility = View.GONE }
                } else { holder.mapPreview.visibility = View.GONE }

                // Si hay foto adjunta, la cargamos con Glide
                if (!entrenamiento.fotoUrl.isNullOrEmpty()) {
                    holder.photoView.visibility = View.VISIBLE
                    Glide.with(context).load(entrenamiento.fotoUrl).placeholder(R.drawable.default_avatar).into(holder.photoView)
                } else { holder.photoView.visibility = View.GONE }

                holder.btnDelete.setOnClickListener { mostrarDialogoBorrado(entrenamiento, position) }
            }
        }

        /**
         * Confirmación de borrado para evitar accidentes.
         */
        private fun mostrarDialogoBorrado(entrenamiento: Entrenamiento, position: Int) {
            AlertDialog.Builder(this@MostrarEntrenamientos)
                .setTitle(R.string.delete_confirmation_title)
                .setMessage(R.string.delete_confirmation_message)
                .setPositiveButton(R.string.delete_confirm) { _, _ ->
                    val docId = entrenamiento.id ?: return@setPositiveButton
                    db.collection("usuarios").document(userToLoadId)
                        .collection("entrenamientos").document(docId)
                        .delete()
                        .addOnSuccessListener {
                            // Borrar de las listas locales para actualizar la UI sin recargar todo
                            entrenamientosList.removeAll { it.id == docId }
                            entrenamientos.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, entrenamientos.size)
                            Toast.makeText(this@MostrarEntrenamientos, R.string.delete_success, Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton(R.string.delete_cancel, null)
                .show()
        }

        override fun getItemCount(): Int = entrenamientos.size

        // ViewHolders: Contenedores de las vistas para mejorar el rendimiento al hacer scroll
        inner class RunningViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.training_name)
            val tvDate: TextView = view.findViewById(R.id.training_date)
            val tvDistance: TextView = view.findViewById(R.id.tv_distance)
            val tvPace: TextView = view.findViewById(R.id.tv_pace)
            val tvDesnivel: TextView = view.findViewById(R.id.tv_desnivel)
            val tvCalories: TextView = view.findViewById(R.id.tv_calories)
            val mapPreview: MapView = view.findViewById(R.id.training_map_preview)
            val photoView: ImageView = view.findViewById(R.id.training_photo_view)
            val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_training)
            init { setupClick(view) }
        }

        inner class StrengthViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.training_name)
            val tvDate: TextView = view.findViewById(R.id.training_date_user_header)
            val tvDuracion: TextView = view.findViewById(R.id.tv_fuerza_duracion)
            val tvDificultad: TextView = view.findViewById(R.id.tv_fuerza_dificultad)
            val tvResumen: TextView = view.findViewById(R.id.tv_resumen_ejercicios)
            val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_training)
            init { setupClick(view) }
        }

        // Abre la pantalla de detalles al tocar un entrenamiento
        private fun RecyclerView.ViewHolder.setupClick(view: View) {
            view.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val intent = Intent(view.context, EntrenamientoDetalles::class.java).apply {
                        putExtra("entrenamiento", entrenamientos[pos])
                    }
                    view.context.startActivity(intent)
                }
            }
        }
    }

    /**
     * Dibuja una línea roja (Polyline) en un minimapa estático dentro de la tarjeta.
     */
    private fun setupMiniMap(mapView: MapView, puntos: List<HashMap<String, Any>>?) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(false) // Desactivar tacto para no interferir con el scroll
        mapView.controller.setZoom(13.0)
        puntos?.let {
            val geoPoints = it.mapNotNull { p ->
                val lat = p["latitude"] as? Double
                val lon = p["longitude"] as? Double
                if (lat != null && lon != null) GeoPoint(lat, lon) else null
            }
            if (geoPoints.isNotEmpty()) {
                val recorrido = Polyline().apply {
                    setPoints(geoPoints)
                    outlinePaint.color = 0xFFFF0000.toInt() // Línea roja
                }
                mapView.overlays.clear()
                mapView.overlays.add(recorrido)
                mapView.controller.setCenter(geoPoints.first())
            }
            mapView.invalidate()
        }
    }

    /**
     * Convierte segundos a formato de ritmo deportivo (min:seg/km).
     */
    private fun formatRitmo(ritmoSegundos: Double): String {
        val min = (ritmoSegundos / 60).toInt()
        val seg = (ritmoSegundos % 60).toInt()
        return String.format(Locale.getDefault(), "%d:%02d/km", min, seg)
    }
}
