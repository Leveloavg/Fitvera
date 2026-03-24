package com.example.fitvera

// Importaciones para UI, Logs, Firebase y Corrutinas (para tareas en segundo plano)
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Actividad que muestra el listado de trofeos ganados por un usuario.
 * Permite filtrar por categorías (distancia, desnivel, entrenamientos).
 */
class MostrarTrofeos : AppCompatActivity() {

    // Instancia de la base de datos Firestore
    private val db = FirebaseFirestore.getInstance()

    // Vistas de la interfaz
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoTrophies: TextView
    private lateinit var tvTitle: TextView
    private lateinit var trophiesAdapter: TrophyListAdapter

    // Variables de control recibidas por Intent
    private var userId: String? = null
    private var filterType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mostrartrofeos)

        // 1. OBTENCIÓN DE DATOS DEL INTENT
        // Intentamos obtener el ID del usuario y el tipo de filtro enviado desde la pantalla anterior
        userId = intent.getStringExtra("USER_ID") ?: intent.getStringExtra("user_id")
        filterType = intent.getStringExtra("FILTER_TYPE") ?: intent.getStringExtra("filter_type")

        // Validación de seguridad: Si no hay ID de usuario, cerramos la pantalla
        if (userId == null) {
            Log.e("MostrarTrofeos", "ID de usuario no recibido en el Intent")
            Toast.makeText(this, "Error: Sesión no válida.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. CONFIGURAR TOOLBAR Y TÍTULO DINÁMICO
        try {
            val btnBack: ImageButton = findViewById(R.id.btn_back_trophies)
            tvTitle = findViewById(R.id.tv_toolbar_title)

            // Personalizamos el título de la pantalla según la categoría pulsada
            tvTitle.text = when(filterType) {
                "distancia" -> "Trofeos de Distancia"
                "desnivel" -> "Trofeos de Desnivel"
                "entrenamientos" -> "Trofeos de Constancia"
                else -> "Mis Trofeos"
            }

            // Configurar el botón de "Atrás"
            btnBack.setOnClickListener { finish() }
        } catch (e: Exception) {
            Log.e("MostrarTrofeos", "Error en UI: ${e.message}")
        }

        // 3. INICIALIZAR RECYCLERVIEW (La lista visual)
        recyclerView = findViewById(R.id.recycler_view_all_trophies)
        tvNoTrophies = findViewById(R.id.tv_no_trophies_detail)
        recyclerView.layoutManager = LinearLayoutManager(this) // Lista vertical

        // 4. INICIALIZAR ADAPTADOR
        // Le pasamos la función 'getTrophyImageResource' para que el adaptador sepa qué icono poner
        trophiesAdapter = TrophyListAdapter(emptyList(), this::getTrophyImageResource)
        recyclerView.adapter = trophiesAdapter

        // 5. CARGAR DATOS DESDE LA NUBE
        loadFilteredTrophies(userId!!, filterType)
    }

    /**
     * Realiza la consulta a Firestore para obtener los trofeos del usuario.
     * @param uid ID del usuario en Firebase.
     * @param type Categoría por la cual filtrar (puede ser null para mostrar todos).
     */
    private fun loadFilteredTrophies(uid: String, type: String?) {
        // Ejecutamos en un hilo secundario (IO) para no congelar la aplicación
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Consultamos la sub-colección "trofeos" dentro del documento del usuario
                val documents = db.collection("usuarios")
                    .document(uid)
                    .collection("trofeos")
                    .get().await()

                // Convertimos los documentos JSON de Firebase a una lista de objetos de Kotlin
                val allTrophies = documents.toObjects(DesafiosActivity.Trophy::class.java)

                // 🛑 LÓGICA DE FILTRADO:
                // Si recibimos un tipo, filtramos la lista. Si no, la dejamos completa.
                val filteredList = if (type != null) {
                    allTrophies.filter { it.type == type }
                } else {
                    allTrophies
                }

                // Volvemos al hilo principal (Main) para actualizar la interfaz de usuario
                withContext(Dispatchers.Main) {
                    if (filteredList.isEmpty()) {
                        // Si no hay trofeos, mostramos el mensaje de "No tienes trofeos aún"
                        recyclerView.visibility = View.GONE
                        tvNoTrophies.visibility = View.VISIBLE
                    } else {
                        // Si hay trofeos, ocultamos el mensaje y mostramos la lista actualizada
                        recyclerView.visibility = View.VISIBLE
                        tvNoTrophies.visibility = View.GONE
                        trophiesAdapter.updateTrophies(filteredList)
                    }
                }
            } catch (e: Exception) {
                // Manejo de errores de conexión o lectura
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MostrarTrofeos, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Determina qué imagen (R.drawable) corresponde a cada tipo de trofeo.
     * @param type El identificador del tipo de trofeo.
     * @return El recurso ID del icono correspondiente.
     */
    private fun getTrophyImageResource(type: String): Int {
        return when (type) {
            "distancia" -> R.drawable.trofeodistancia
            "desnivel" -> R.drawable.trofeodesnivel
            "entrenamientos" -> R.drawable.trofeonumerodenetrenamientos
            else -> R.drawable.trofeodesafio // Icono por defecto
        }
    }
}