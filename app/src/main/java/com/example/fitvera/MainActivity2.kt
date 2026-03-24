package com.example.fitvera

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.work.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Actividad Principal de la App.
 * Implementa 'HomeFragmentListener' para responder a clics en los botones de los fragmentos hijos.
 */
class MainActivity2 : AppCompatActivity(), HomeFragmentListener {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RATIONALE_REQUEST_CODE = 2000 // Código para identificar la respuesta de selección de fuente de datos

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.actividadprincipal)

        // 1. CONFIGURACIÓN DE GOOGLE SIGN-IN: Necesario para poder cerrar sesión correctamente después.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 2. 🔥 TRABAJOS EN SEGUNDO PLANO: Configura tareas que se ejecutan solas (Logros/Rankings).
        setupAutoWorkers()

        // 3. 🏆 REVISAR TROFEOS: Al entrar, mira si el usuario ganó algo recientemente para mostrar la sorpresa.
        checkForNewRankingTrophies()

        // 4. 🚩 LIGAS: Asegura que el usuario esté inscrito en la liga del mes actual nada más entrar.
        checkAndAssignLeague()

        // 5. NAVEGACIÓN: Configura el menú inferior (BottomNavigation).
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // Botón opcional de inicio rápido (si existe en el layout principal).
        findViewById<Button>(R.id.btn_get_started)?.setOnClickListener {
            startActivity(Intent(this, LeagueActivity::class.java))
        }

        // Si es la primera vez que se abre, cargamos el fragmento de Inicio (HomeFragments).
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            replaceFragment(HomeFragments())
        }

        setupNavigation()
    }

    // --- 🚩 LÓGICA DE LIGAS AUTOMÁTICAS ---

    /**
     * Verifica si el usuario ya tiene una liga asignada para el mes actual (Año-Mes).
     */
    private fun checkAndAssignLeague() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Obtenemos el ID del mes actual (ej: "2024-03")
        val cal = Calendar.getInstance()
        val monthId = String.format(Locale.US, "%d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val leagueId = "liga_${monthId}_div_1" // Por defecto a División 1

        db.collection("ranking_users").document(userId).get()
            .addOnSuccessListener { document ->
                val currentLeague = document.getString("current_league_id")

                // Si no tiene liga o es de un mes anterior, lo reasignamos
                if (currentLeague == null || !currentLeague.contains(monthId)) {
                    autoAssignUserToLeague(userId, monthId)
                } else {
                    // Si ya está en la liga, nos aseguramos de que su ID esté en la lista de miembros de la liga
                    db.collection("ligas_mensuales").document(leagueId).update("members", FieldValue.arrayUnion(userId))
                }
            }
    }

    /**
     * Inscribe al usuario en la base de datos de rankings y en el documento de la liga mensual.
     */
    private fun autoAssignUserToLeague(userId: String, monthId: String) {
        val db = FirebaseFirestore.getInstance()
        val leagueId = "liga_${monthId}_div_1"
        val rankingRef = db.collection("ranking_users").document(userId)

        rankingRef.get().addOnSuccessListener { doc ->
            val updates = hashMapOf<String, Any>(
                "userId" to userId,
                "name" to (FirebaseAuth.getInstance().currentUser?.displayName ?: "Atleta"),
                "current_league_id" to leagueId,
                "last_update_month" to monthId
            )

            // Si es un usuario nuevo (no tiene score), inicializamos sus contadores a 0
            if (!doc.contains("score_total_month")) {
                updates["score_total_month"] = 0
                updates["activities_month"] = 0
                updates["kilometers_month"] = 0.0
            }

            // Batch: Ejecuta varias operaciones a la vez (Actualiza usuario + Añade a lista de liga)
            db.batch().apply {
                set(rankingRef, updates, SetOptions.merge())
                update(db.collection("ligas_mensuales").document(leagueId), "members", FieldValue.arrayUnion(userId))
            }.commit().addOnFailureListener {
                // Si la actualización falla porque la liga no existe, la creamos de cero
                createNewMonthlyLeague(leagueId, monthId, userId)
            }
        }
    }

    /**
     * Crea un nuevo documento de liga si es el primer usuario que entra este mes.
     */
    private fun createNewMonthlyLeague(leagueId: String, monthId: String, firstUserId: String) {
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "month" to monthId,
            "division" to 1,
            "members" to listOf(firstUserId),
            "status" to "active"
        )
        db.collection("ligas_mensuales").document(leagueId).set(data)
    }

    // --- 🏆 SISTEMA DE TROFEOS ---

    /**
     * Busca en la subcolección 'trofeos' si hay alguno ganado desde ayer.
     */
    private fun checkForNewRankingTrophies() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time

        db.collection("usuarios").document(userId).collection("trofeos")
            .whereGreaterThan("date", yesterday) // Solo trofeos muy recientes
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val trophyDoc = snapshot.documents.first()
                    val title = trophyDoc.getString("title") ?: "¡Felicidades!"
                    val type = trophyDoc.getString("type") ?: ""
                    showTrophyCongratulations(title, type)
                }
            }
    }

    /**
     * Muestra un cuadro de diálogo personalizado con una animación al ganar un trofeo.
     */
    private fun showTrophyCongratulations(title: String, type: String) {
        val builder = android.app.AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.trophy_congrats, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_congrats_title)
        val ivTrophy = dialogView.findViewById<ImageView>(R.id.iv_congrats_trophy)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_congrats_close)

        tvTitle.text = title

        // Asignamos la imagen según el tipo de trofeo (Oro, Plata, Bronce, etc.)
        val imageRes = when (type) {
            "oro_top1" -> R.drawable.trofeoprimero
            "plata_top2" -> R.drawable.trofeoplata
            "bronce_top3" -> R.drawable.trofeotercero
            "oro_top10" -> R.drawable.medallaoro
            "plata_top50" -> R.drawable.medallaplata
            else -> R.drawable.medallabronze
        }
        ivTrophy.setImageResource(imageRes)

        // Lanzamos una animación de escalado para que el trofeo "salte" en pantalla
        ivTrophy.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up))

        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Fondo transparente para el diseño redondeado
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // --- ⚙️ WORKERS (Tareas automáticas) ---

    /**
     * Configura WorkManager para ejecutar tareas de mantenimiento incluso con la app cerrada.
     */
    private fun setupAutoWorkers() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        // ChallengeWorker: Revisa logros cada 8 horas.
        val challengeRequest = PeriodicWorkRequestBuilder<ChallengeWorker>(8, TimeUnit.HOURS)
            .setConstraints(constraints).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "FitVeraAutoLogros", ExistingPeriodicWorkPolicy.KEEP, challengeRequest
        )

        // RankingWorker: Revisa posiciones de ranking cada 12 horas.
        val rankingRequest = PeriodicWorkRequestBuilder<RankingWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "RankingSystem", ExistingPeriodicWorkPolicy.REPLACE, rankingRequest
        )
    }

    // --- 🧭 NAVEGACIÓN ---

    /**
     * Gestiona los clics en la barra inferior para cambiar entre Fragmentos o Actividades.
     */
    private fun setupNavigation() {
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { replaceFragment(HomeFragments()); true }
                R.id.nav_register -> {
                    startActivity(Intent(this, RegistrarEntrenamiento::class.java))
                    true
                }
                R.id.nav_profile -> { replaceFragment(perfil()); true }
                R.id.nav_search -> { replaceFragment(SearchFragment()); true }
                R.id.nav_activities -> { startActivity(Intent(this, FeedActividades::class.java)); true }
                else -> false
            }
        }
    }

    /**
     * Reemplaza el contenido del contenedor principal por un nuevo Fragmento.
     */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit()
    }

    /**
     * Abre la pantalla de selección de fuente de datos (Google Fit vs Samsung vs Manual).
     * Usa 'startActivityForResult' para saber qué eligió el usuario.
     */
    override fun abrirRegistrar() {
        val intent = Intent(this, HealthConnectRationaleActivity::class.java)
        startActivityForResult(intent, RATIONALE_REQUEST_CODE)
    }

    /**
     * Captura la elección de la pantalla 'Rationale' y abre la actividad correspondiente.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RATIONALE_REQUEST_CODE) {
            when (resultCode) {
                1 -> startActivity(Intent(this, ImportarEntrenamientoHC::class.java)) // Google Fit
                2 -> startActivity(Intent(this, ImportarEntrenamientoZepp::class.java)) // Samsung/Zepp
                3 -> startActivity(Intent(this, RegistrarEntrenamiento::class.java)) // Manual
            }
        }
    }

    // --- INTERFAZ HOMEFRAGMENTLISTENER ---
    // Métodos que los fragmentos llaman cuando el usuario toca botones de la pantalla de inicio.

    override fun abrirPlanificar() = startActivity(Intent(this, PlanificarEntrenamiento::class.java))
    override fun abrirVerEntrenamientos() = startActivity(Intent(this, MostrarEntrenamientos::class.java))
    override fun abrirLogros() = startActivity(Intent(this, DesafiosActivity::class.java))
    override fun abrirRanking() = startActivity(Intent(this, RankingActivity::class.java))
    override fun abrirTerritorios() = startActivity(Intent(this, MapaGlobalActivity::class.java))
    override fun CerrarAplicacion() = finish()

    /**
     * Cierra sesión completamente: limpia tareas, Firebase y la cuenta de Google.
     */
    override fun CerrarSesion() {
        WorkManager.getInstance(applicationContext).cancelAllWork()
        FirebaseAuth.getInstance().signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            val intent = Intent(this, MainActivity::class.java)
            // Limpia la pila de actividades para que no pueda volver atrás con el botón físico.
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}