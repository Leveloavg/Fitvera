package com.example.fitvera


import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.HashMap
import java.util.concurrent.TimeUnit

// --- Modelos de Datos Auxiliares ---

// Representa los datos básicos de un usuario en Firebase
data class User(
    val nombre: String? = null,
    val fotoUrl: String? = null,
    val uid: String? = null
)

// Representa el perfil simplificado para mostrar en el encabezado de cada actividad
data class UserProfile(
    val userId: String,
    val name: String,
    val photoUrl: String?
)

// Clase contenedora que une un Entrenamiento con el Perfil de quien lo hizo
data class EntrenamientoWithUser(
    val entrenamiento: Entrenamiento,
    val user: UserProfile
)

class FeedActividades : AppCompatActivity() {

    // Variables de la interfaz
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EntrenamientosAdapter
    private lateinit var searchBar: EditText
    private lateinit var addTrainingButton: MaterialButton

    // Instancias de Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Listas para manejar los datos y el filtrado
    private val allTrainingsWithUser = mutableListOf<EntrenamientoWithUser>()
    private val filteredListWithUser = mutableListOf<EntrenamientoWithUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cargar configuración de OSMDroid (Mapas)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_entrenamientos)

        // Inicializar vistas
        recyclerView = findViewById(R.id.trainings_list)
        searchBar = findViewById(R.id.search_bar)
        addTrainingButton = findViewById(R.id.add_training_button)

        // Configuración del RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EntrenamientosAdapter(filteredListWithUser)
        recyclerView.adapter = adapter

        // Botón para ir a registrar una nueva actividad
        addTrainingButton.setOnClickListener {
            startActivity(Intent(this, RegistrarEntrenamiento::class.java))
        }

        // Botón atrás
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // Lógica del buscador: filtra la lista cada vez que el texto cambia
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
        })

        // Carga inicial de datos
        cargarEntrenamientosUnificados()
    }

    override fun onResume() {
        super.onResume()
        // Recargar al volver a la pantalla para ver actualizaciones
        cargarEntrenamientosUnificados()
    }

    /**
     * Función principal que descarga datos de múltiples usuarios de forma asíncrona.
     */
    private fun cargarEntrenamientosUnificados() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Usamos Corrutinas para no bloquear la aplicación mientras descargamos de Internet
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Obtener los IDs de todos mis amigos
                val friendsResult = db.collection("usuarios")
                    .document(currentUserId)
                    .collection("friends")
                    .get().await()

                val userIdsToLoad = mutableListOf(currentUserId) // Incluyo mi propio ID
                userIdsToLoad.addAll(friendsResult.map { it.id })

                // 2. Cargar perfiles de usuario (Nombre y Foto) de todos los IDs
                val userProfiles = mutableMapOf<String, UserProfile>()
                userIdsToLoad.map { userId ->
                    async {
                        val doc = db.collection("usuarios").document(userId).get().await()
                        if (doc.exists()) {
                            val user = doc.toObject(User::class.java)
                            userProfiles[userId] = UserProfile(
                                userId = userId,
                                name = user?.nombre ?: "Usuario",
                                photoUrl = user?.fotoUrl
                            )
                        }
                    }
                }.awaitAll() // Espera a que todos los perfiles se descarguen

                // 3. Cargar todos los entrenamientos de cada usuario
                val allTrainings = mutableListOf<EntrenamientoWithUser>()
                userIdsToLoad.map { userId ->
                    async {
                        val profile = userProfiles[userId]
                        if (profile != null) {
                            val trainings = db.collection("usuarios").document(userId)
                                .collection("entrenamientos").get().await()
                                .map { doc ->
                                    val entrenamiento = doc.toObject(Entrenamiento::class.java)
                                    entrenamiento.id = doc.id
                                    EntrenamientoWithUser(entrenamiento, profile)
                                }
                            synchronized(allTrainings) { allTrainings.addAll(trainings) }
                        }
                    }
                }.awaitAll()

                // 4. Ordenar por fecha (más reciente primero) y mostrar en el hilo principal
                withContext(Dispatchers.Main) {
                    allTrainings.sortWith(compareByDescending {
                        try {
                            // Intenta parsear la fecha soportando dos formatos distintos
                            val formato = if (it.entrenamiento.fecha.contains("/")) "dd/MM/yyyy" else "dd-MM-yyyy"
                            SimpleDateFormat(formato, Locale.getDefault()).parse(it.entrenamiento.fecha)
                        } catch (e: Exception) { Date(0) }
                    })

                    allTrainingsWithUser.clear()
                    allTrainingsWithUser.addAll(allTrainings)
                    filterList(searchBar.text.toString()) // Aplicar filtro actual si existe
                }
            } catch (e: Exception) {
                Log.e("FeedActividades", "Error loading feed", e)
            }
        }
    }

    /**
     * Filtra la lista por nombre de actividad, nombre de usuario o tipo (Fuerza/Carrera).
     */
    private fun filterList(query: String) {
        val lowerCaseQuery = query.lowercase()
        filteredListWithUser.clear()
        filteredListWithUser.addAll(
            allTrainingsWithUser.filter {
                it.entrenamiento.nombre.lowercase().contains(lowerCaseQuery) ||
                        it.user.name.lowercase().contains(lowerCaseQuery) ||
                        it.entrenamiento.tipo.lowercase().contains(lowerCaseQuery)
            }
        )
        adapter.notifyDataSetChanged() // Refrescar la lista visualmente
    }

    // --- ADAPTADOR CON MÚLTIPLES TIPOS DE VISTA ---
    inner class EntrenamientosAdapter(private val list: List<EntrenamientoWithUser>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_RUNNING = 1
        private val TYPE_STRENGTH = 2

        // Determina qué layout usar basado en el tipo de entrenamiento
        override fun getItemViewType(position: Int): Int {
            return if (list[position].entrenamiento.tipo == "Fuerza") TYPE_STRENGTH else TYPE_RUNNING
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_STRENGTH) {
                // Layout para Gimnasio/Fuerza
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_training_fuerza, parent, false)
                StrengthViewHolder(view)
            } else {
                // Layout para Running/Cardio
                val view = LayoutInflater.from(parent.context).inflate(R.layout.entrenamientosamigos, parent, false)
                RunningViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val data = list[position]
            val entrenamiento = data.entrenamiento
            val user = data.user

            // --- Lógica común: Cabecera del Perfil (quién subió la foto) ---
            val ivUserPhoto = holder.itemView.findViewById<ImageView>(R.id.user_profile_photo)
            val tvUserName = holder.itemView.findViewById<TextView>(R.id.user_name)
            val tvDateHeader = holder.itemView.findViewById<TextView>(R.id.training_date_user_header)

            tvUserName.text = user.name
            tvDateHeader.text = entrenamiento.fecha
            Glide.with(holder.itemView.context)
                .load(user.photoUrl ?: R.drawable.default_avatar)
                .placeholder(R.drawable.default_avatar)
                .circleCrop() // Foto de usuario redonda
                .into(ivUserPhoto)

            // --- Lógica específica para FUERZA ---
            if (holder is StrengthViewHolder) {
                holder.tvName.text = entrenamiento.nombre
                val minutos = TimeUnit.MILLISECONDS.toMinutes(entrenamiento.tiempo)
                holder.tvDuracion.text = "$minutos min"
                holder.tvDificultad.text = entrenamiento.dificultad

                // Crea una lista de texto con los ejercicios realizados
                holder.tvResumen.text = entrenamiento.ejercicios?.joinToString("\n") { ej ->
                    "• ${ej.nombre}: ${ej.series}x${ej.reps} (${ej.peso}kg)"
                } ?: "Sin detalles"

            }
            // --- Lógica específica para RUNNING ---
            else if (holder is RunningViewHolder) {
                holder.tvName.text = entrenamiento.nombre
                holder.tvDistance.text = String.format("%.2f km", entrenamiento.distancia / 1000)
                holder.tvPace.text = formatRitmo(entrenamiento.ritmoPromedio)
                holder.tvDesnivel.text = "${entrenamiento.desnivel.toInt()} m"
                holder.tvCalories.text = "${entrenamiento.calorias.toInt()} kcal"

                // Si hay coordenadas GPS, muestra la miniatura del mapa
                if (!entrenamiento.puntosRecorrido.isNullOrEmpty()) {
                    holder.mapPreview.visibility = View.VISIBLE
                    setupMiniMap(holder.mapPreview, entrenamiento.puntosRecorrido)
                } else {
                    holder.mapPreview.visibility = View.GONE
                }

                // Si hay foto de la ruta, la muestra usando Glide
                if (!entrenamiento.fotoUrl.isNullOrEmpty()) {
                    holder.photoView.visibility = View.VISIBLE
                    Glide.with(holder.itemView.context).load(entrenamiento.fotoUrl).into(holder.photoView)
                } else {
                    holder.photoView.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = list.size

        // ViewHolder para elementos de Running
        inner class RunningViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.training_name)
            val tvDistance: TextView = view.findViewById(R.id.tv_distance)
            val tvPace: TextView = view.findViewById(R.id.tv_pace)
            val tvDesnivel: TextView = view.findViewById(R.id.tv_desnivel)
            val tvCalories: TextView = view.findViewById(R.id.tv_calories)
            val mapPreview: MapView = view.findViewById(R.id.training_map_preview)
            val photoView: ImageView = view.findViewById(R.id.training_photo_view)
            init { setupClick(view) }
        }

        // ViewHolder para elementos de Fuerza
        inner class StrengthViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.training_name)
            val tvDuracion: TextView = view.findViewById(R.id.tv_fuerza_duracion)
            val tvDificultad: TextView = view.findViewById(R.id.tv_fuerza_dificultad)
            val tvResumen: TextView = view.findViewById(R.id.tv_resumen_ejercicios)
            init { setupClick(view) }
        }

        // Configura el clic para abrir el detalle completo de la actividad
        private fun RecyclerView.ViewHolder.setupClick(view: View) {
            view.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val intent = Intent(view.context, EntrenamientoDetalles::class.java).apply {
                        putExtra("entrenamiento", list[pos].entrenamiento)
                    }
                    view.context.startActivity(intent)
                }
            }
        }
    }

    /**
     * Configura una pequeña previsualización estática del mapa con la ruta GPS.
     */
    private fun setupMiniMap(mapView: MapView, puntos: List<HashMap<String, Any>>?) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(false) // Desactiva interacción para que sea solo preview
        mapView.controller.setZoom(13.0)
        puntos?.let {
            val geoPoints = it.map { p -> GeoPoint(p["latitude"] as Double, p["longitude"] as Double) }
            val recorrido = Polyline().apply { setPoints(geoPoints) }
            mapView.overlays.clear()
            mapView.overlays.add(recorrido)
            if (geoPoints.isNotEmpty()) mapView.controller.setCenter(geoPoints.first())
            mapView.invalidate()
        }
    }

    /**
     * Convierte segundos/km a formato minutos:segundos legibles.
     */
    private fun formatRitmo(ritmoSegundos: Double): String {
        val min = (ritmoSegundos / 60).toInt()
        val seg = (ritmoSegundos % 60).toInt()
        return String.format(Locale.getDefault(), "%d:%02d/km", min, seg)
    }
}