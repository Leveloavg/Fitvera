package com.example.fitvera


import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * ChallengeWorker: Clase encargada de ejecutar tareas en segundo plano.
 * Hereda de CoroutineWorker para permitir el uso de Suspend functions (programación asíncrona).
 */
class ChallengeWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    // Instancias de Firebase para acceder a la base de datos y autenticación
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * doWork: Es el corazón del Worker. Se ejecuta cuando el sistema lo decide.
     */
    override suspend fun doWork(): Result {
        // 1. Verificar si el usuario está logueado, si no, salimos con éxito pero sin hacer nada
        val userId = auth.currentUser?.uid ?: return Result.success()

        // 2. Obtener el mes y año actual para filtrar los retos (ej: "03-2024")
        val calendar = Calendar.getInstance()
        val monthYear = SimpleDateFormat("MM-yyyy", Locale.getDefault()).format(calendar.time)

        try {
            Log.d("ChallengeWorker", "Iniciando verificación automática para: $userId")

            // 3. Referencia al documento de retos mensuales de este usuario específico
            val monthlyRef = db.collection("usuarios").document(userId)
                .collection("monthly_challenges").document(monthYear)

            // Intentamos obtener el documento de forma asíncrona (.await())
            val doc = monthlyRef.get().await()

            val bundle = if (!doc.exists()) {
                // Si no existen retos para este mes, los creamos
                Log.d("ChallengeWorker", "No hay retos para este mes. Generando...")
                generateAndSaveChallenges(userId, monthYear)
            } else {
                // Si existen, convertimos el documento de Firebase a nuestro objeto de datos
                doc.toObject(DesafiosActivity.MonthlyChallengesBundle::class.java)
            }

            // 4. Si tenemos retos (nuevos o existentes), procesamos el progreso del usuario
            if (bundle != null) {
                processProgressAndTrophies(userId, bundle, monthYear)
            }

            return Result.success() // Tarea completada con éxito
        } catch (e: Exception) {
            Log.e("ChallengeWorker", "Error en segundo plano: ${e.message}")
            // Si algo falla (ej. sin internet), pedimos al sistema que lo reintente más tarde
            return Result.retry()
        }
    }

    /**
     * Calcula el progreso del usuario basándose en sus entrenamientos guardados.
     */
    private suspend fun processProgressAndTrophies(userId: String, bundle: DesafiosActivity.MonthlyChallengesBundle, monthYear: String) {
        // Obtenemos todos los entrenamientos del usuario de la base de datos
        val entrenamientosDocs = db.collection("usuarios").document(userId)
            .collection("entrenamientos").get().await()

        val listaEntrenamientos = entrenamientosDocs.toObjects(DesafiosActivity.Entrenamiento::class.java)

        // Parseamos el mes y año objetivo para comparar con las fechas de los entrenamientos
        val parts = monthYear.split("-")
        val targetMonth = parts[0].toInt() - 1 // Calendar usa meses de 0 a 11
        val targetYear = parts[1].toInt()

        // Creamos una lista con los 4 tipos de retos que tiene el usuario este mes
        val challenges = listOf(bundle.destacado, bundle.distancia, bundle.desnivel, bundle.entrenamientos)

        for (challenge in challenges) {
            // Saltamos retos vacíos o sin meta definida
            if (challenge.id.isEmpty() || challenge.goal <= 0) continue

            var currentProgress = 0.0

            // Recorremos cada entrenamiento para sumar progreso si coincide con el mes actual
            for (e in listaEntrenamientos) {
                try {
                    val cal = Calendar.getInstance()
                    val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(e.fecha)
                    date?.let {
                        cal.time = it
                        // Si el entrenamiento es del mes y año que estamos evaluando:
                        if (cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear) {
                            when (challenge.type) {
                                "distancia" -> currentProgress += e.distancia
                                "desnivel" -> currentProgress += e.desnivel
                                "entrenamientos" -> currentProgress += 1.0
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("ChallengeWorker", "Error parseando fecha: ${e.fecha}")
                }
            }

            // Si el progreso sumado supera o iguala la meta, intentamos otorgar el trofeo
            if (currentProgress >= challenge.goal) {
                awardTrophy(userId, challenge, monthYear)
            }
        }
    }

    /**
     * Registra un trofeo en la base de datos si el usuario no lo tiene ya.
     */
    private suspend fun awardTrophy(userId: String, challenge: DesafiosActivity.MonthlyChallenge, monthYear: String) {
        // Referencia al posible trofeo (usamos el ID del reto para que sea único)
        val trophyRef = db.collection("usuarios").document(userId).collection("trofeos").document(challenge.id)
        val doc = trophyRef.get().await()

        // Solo guardamos y notificamos si el trofeo NO existe aún
        if (!doc.exists()) {
            val parts = monthYear.split("-")
            val monthName = getMonthName(parts[0].toInt() - 1)

            val trophyData = hashMapOf(
                "id" to challenge.id,
                "title" to "${challenge.title} - $monthName ${parts[1]}",
                "description" to challenge.description,
                "type" to challenge.type
            )

            // Guardar en Firestore
            trophyRef.set(trophyData).await()

            // 🔔 Disparar la notificación visual para el usuario
            sendTrophyNotification(challenge.title)
        }
    }

    /**
     * Genera retos aleatorios para el mes si es la primera vez que el usuario entra ese mes.
     */
    private suspend fun generateAndSaveChallenges(userId: String, monthYear: String): DesafiosActivity.MonthlyChallengesBundle {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        // Usamos una semilla basada en año/mes para que los retos sean "aleatorios" pero consistentes
        val random = Random(year * 100 + month)
        val monthName = getMonthName(month)

        // Creamos un "Reto Destacado" al azar entre 3 opciones
        val destacadoChallenge = when (random.nextInt(3)) {
            0 -> DesafiosActivity.MonthlyChallenge("destacado_distancia_$monthYear", "Maratón de $monthName", "Corre 80 km este mes.", 80000.0, "distancia")
            1 -> DesafiosActivity.MonthlyChallenge("destacado_desnivel_$monthYear", "Escalador de $monthName", "Sube 500m de desnivel.", 500.0, "desnivel")
            else -> DesafiosActivity.MonthlyChallenge("destacado_entrenos_$monthYear", "Constancia de $monthName", "Registra 15 entrenos.", 15.0, "entrenamientos")
        }

        // Empaquetamos todos los retos del mes
        val bundle = DesafiosActivity.MonthlyChallengesBundle(
            destacado = destacadoChallenge,
            distancia = DesafiosActivity.MonthlyChallenge("dist_mes_$monthYear", "Desafío de Distancia", "Meta mensual de distancia.", 40000.0, "distancia"),
            desnivel = DesafiosActivity.MonthlyChallenge("desn_mes_$monthYear", "Desafío de Desnivel", "Meta mensual de desnivel.", 400.0, "desnivel"),
            entrenamientos = DesafiosActivity.MonthlyChallenge("entr_mes_$monthYear", "Desafío de Entrenamientos", "Meta mensual de sesiones.", 12.0, "entrenamientos")
        )

        // Guardamos el pack de retos en Firestore
        db.collection("usuarios").document(userId)
            .collection("monthly_challenges").document(monthYear)
            .set(bundle).await()

        return bundle
    }

    /**
     * Crea y muestra una notificación en el sistema Android.
     */
    private fun sendTrophyNotification(title: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "trofeos_channel"

        // Para Android 8.0 (Oreo) o superior, es obligatorio crear un Canal de Notificación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Logros FitVera", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // Configuración estética y de comportamiento de la notificación
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("¡Nuevo Trofeo Ganado!")
            .setContentText("Has completado el desafío: $title")
            .setSmallIcon(android.R.drawable.star_on) // Icono de estrella por defecto
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Se cierra al tocarla
            .build()

        // Disparar la notificación con un ID único basado en el tiempo actual
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Función auxiliar para obtener el nombre del mes en español y con la primera letra en mayúscula.
     */
    private fun getMonthName(month: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, month)
        return SimpleDateFormat("MMMM", Locale("es", "ES")).format(cal.time)
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}