package com.example.fitvera

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fitvera.databinding.ActivityLeagueBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import java.text.SimpleDateFormat
import java.util.*

/**
 * Actividad que gestiona la visualización de la liga actual del usuario,
 * el ranking de miembros y la cuenta atrás del mes.
 */
class LeagueActivity : AppCompatActivity() {

    // ViewBinding para acceder a los componentes del layout sin usar findViewById
    private lateinit var binding: ActivityLeagueBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Handler y Runnable para actualizar la cuenta atrás cada minuto en segundo plano
    private val handler = Handler(Looper.getMainLooper())

    // Objeto que se ejecuta repetidamente para actualizar el tiempo restante de la liga
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            // Se vuelve a programar para ejecutarse en 60,000 milisegundos (1 minuto)
            handler.postDelayed(this, 60000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeagueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuración del botón de retroceso para cerrar la pantalla actual
        binding.btnBackLeague.setOnClickListener {
            finish()
        }

        // Configuración del RecyclerView para mostrar la lista de miembros de la liga
        binding.rvLeagueMembers.layoutManager = LinearLayoutManager(this)

        // Carga la información de la liga del usuario desde Firebase
        loadUserLeague()

        // Inicia el ciclo de la cuenta atrás
        handler.post(countdownRunnable)
    }

    /**
     * Paso 1: Obtiene el documento del ranking del usuario actual para saber en qué liga está.
     */
    private fun loadUserLeague() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("ranking_users").document(userId).get()
            .addOnSuccessListener { document ->
                // Obtenemos el ID de la liga (ej: "marzo_2024_div_2")
                val leagueId = document.getString("current_league_id")

                if (!leagueId.isNullOrEmpty()) {
                    // Extrae el número de división del ID de la liga
                    val division = leagueId.substringAfterLast("_div_", "1")

                    // Formatea el nombre del mes actual en español para el subtítulo
                    val monthName = SimpleDateFormat("MMMM", Locale("es", "ES"))
                        .format(Date())
                        .replaceFirstChar { it.uppercase() }

                    binding.tvLeagueSubtitle.text = "Temporada de $monthName"
                    binding.tvLeagueMainTitle.text = "División $division"

                    // Una vez tenemos el ID de la liga, buscamos a sus miembros
                    fetchLeagueMembers(leagueId)
                } else {
                    // Caso en el que el usuario aún no ha sido asignado a ninguna liga
                    binding.tvLeagueSubtitle.text = "Estado"
                    binding.tvLeagueMainTitle.text = "Buscando Liga..."
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Paso 2: Obtiene la lista de IDs de los miembros de la liga y luego sus datos de ranking.
     */
    private fun fetchLeagueMembers(leagueId: String) {
        // Accedemos al documento de la liga en la colección 'ligas_mensuales'
        db.collection("ligas_mensuales").document(leagueId).get()
            .addOnSuccessListener { leagueDoc ->
                // Obtenemos el array de IDs de usuarios miembros
                val memberIds = leagueDoc.get("members") as? List<String> ?: emptyList()

                if (memberIds.isNotEmpty()) {
                    // Consultamos los perfiles de esos miembros (máximo 30 por limitación de Firebase 'whereIn')
                    db.collection("ranking_users")
                        .whereIn(FieldPath.documentId(), memberIds.take(30))
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            // Convertimos los documentos de Firebase en una lista de objetos 'LeagueUser'
                            val userList = querySnapshot.documents.map { doc ->
                                LeagueUser(
                                    userId = doc.id,
                                    name = doc.getString("name") ?: "Atleta",
                                    photoUrl = doc.getString("photoUrl") ?: "",
                                    puntuacion = doc.getLong("score_total_month")?.toInt() ?: 0,
                                    activities = doc.getLong("activities_month")?.toInt() ?: 0
                                )
                            }

                            // Ordenamos la lista: 1º por mayor puntuación, 2º por número de actividades
                            val sortedList = userList.sortedWith(
                                compareByDescending<LeagueUser> { it.puntuacion }
                                    .thenByDescending { it.activities }
                            )

                            // Enviamos la lista ordenada al adaptador del RecyclerView
                            binding.rvLeagueMembers.adapter = LeagueAdapter(sortedList)
                        }
                }
            }
    }

    /**
     * Calcula cuánto tiempo falta hasta el último segundo del mes actual.
     */
    private fun updateCountdown() {
        val now = Calendar.getInstance()

        // Configuramos la fecha de fin: último día del mes a las 23:59:59
        val endOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        val diff = endOfMonth.timeInMillis - now.timeInMillis

        if (diff > 0) {
            // Cálculo matemático para convertir milisegundos en Días, Horas y Minutos
            val days = diff / (24 * 60 * 60 * 1000)
            val hours = (diff / (60 * 60 * 1000)) % 24
            val minutes = (diff / (60 * 1000)) % 60

            // Actualiza el TextView con el formato "0d 00h 00m"
            binding.tvCountdown.text = String.format("%dd %02dh %02dm", days, hours, minutes)
        } else {
            binding.tvCountdown.text = "Finalizada"
        }
    }

    /**
     * Limpieza al cerrar la actividad para evitar fugas de memoria (memory leaks).
     */
    override fun onDestroy() {
        super.onDestroy()
        // Detiene el temporizador cuando el usuario sale de la pantalla
        handler.removeCallbacks(countdownRunnable)
    }
}