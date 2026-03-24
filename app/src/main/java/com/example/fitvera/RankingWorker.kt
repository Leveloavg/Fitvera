package com.example.fitvera

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.*

// RankingWorker es un trabajador en segundo plano que usa Corrutinas para tareas asíncronas
class RankingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val db = FirebaseFirestore.getInstance() // Instancia de la base de datos Firestore

    // Función principal que ejecuta el trabajo
    override suspend fun doWork(): Result {
        return try {
            // Obtener fecha actual: Año, Semana y Mes (formato 2026-03)
            val cal = Calendar.getInstance()
            val curYear = cal.get(Calendar.YEAR)
            val curWeek = cal.get(Calendar.WEEK_OF_YEAR)
            val curMonth = String.format(Locale.getDefault(), "%d-%02d", curYear, cal.get(Calendar.MONTH) + 1)

            // Referencia al documento de control que guarda la última vez que se ejecutó la limpieza
            val controlRef = db.collection("config").document("ranking_control")
            val controlSnapshot = controlRef.get().await()

            // Si es la primera vez que se ejecuta (no existe el documento), inicializa y termina
            if (!controlSnapshot.exists()) {
                controlRef.set(mapOf(
                    "last_week" to curWeek,
                    "last_month" to curMonth,
                    "last_year" to curYear
                )).await()

                markExistingUsers(curYear, curMonth, curWeek) // Marca a los usuarios con la fecha actual
                return Result.success()
            }

            // Obtener cuándo fue la última vez que se procesó cada periodo
            val lastWeek = controlSnapshot.getLong("last_week")?.toInt() ?: curWeek
            val lastMonth = controlSnapshot.getString("last_month") ?: curMonth
            val lastYear = controlSnapshot.getLong("last_year")?.toInt() ?: curYear

            // --- PASO 1: ENTREGA DE PREMIOS ---

            // Si la semana ha cambiado (es una nueva semana)
            if (curWeek != lastWeek) {
                // Entregar trofeos a los 10 mejores de la semana en 3 categorías
                awardOnlyTrophies("week", "kilometers_week", "Distancia Semanal")
                awardOnlyTrophies("week", "activities_week", "Actividad Semanal")
                awardOnlyTrophies("week", "pace_week", "Ritmo Semanal")
                controlRef.update("last_week", curWeek).await() // Actualizar control
            }

            // Si el mes ha cambiado
            if (curMonth != lastMonth) {
                // 1. Repartir monedas de las ligas y resetear puntos de liga (Score)
                awardLeaguePrizes(lastMonth)

                // 2. Entregar trofeos mensuales por rendimiento
                awardOnlyTrophies("month", "kilometers_month", "Distancia Mensual")
                awardOnlyTrophies("month", "activities_month", "Actividad Mensual")
                awardOnlyTrophies("month", "pace_month", "Ritmo Mensual")
                awardOnlyTrophies("month", "territories_total", "Conquista de Zonas")

                // 3. Organizar a los usuarios en nuevas ligas para el nuevo mes
                createNewLeagues(curMonth)
                controlRef.update("last_month", curMonth).await()
            }

            // Si el año ha cambiado
            if (curYear != lastYear) {
                awardOnlyTrophies("year", "kilometers_year", "Distancia Anual")
                awardOnlyTrophies("year", "activities_year", "Actividad Anual")
                awardOnlyTrophies("year", "pace_year", "Ritmo Anual")
                controlRef.update("last_year", curYear).await()
            }

            // --- PASO 2: LIMPIEZA DE ESTADÍSTICAS ---
            // Si algo cambió, pone a 0 los kilómetros/actividades del periodo que caducó
            if (curWeek != lastWeek || curMonth != lastMonth || curYear != lastYear) {
                cleanDatabase(curYear, curMonth, curWeek)
            }

            // Actualizar el contador global de territorios de todos los usuarios
            updateGlobalTerritories()

            Result.success() // Trabajo terminado con éxito
        } catch (e: Exception) {
            Log.e("RankingWorker", "Error: ${e.message}")
            Result.retry() // Si falla, reintentar más tarde
        }
    }

    // Inicializa los documentos de ranking de los usuarios con fechas válidas
    private suspend fun markExistingUsers(curYear: Int, curMonth: String, curWeek: Int) {
        val snapshot = db.collection("ranking_users").get().await()
        if (snapshot.isEmpty) return

        val batch = db.batch()
        for (doc in snapshot.documents) {
            batch.update(doc.reference, mapOf(
                "last_update_year" to curYear,
                "last_update_month" to curMonth,
                "last_update_week" to curWeek
            ))
        }
        batch.commit().await()
    }

    // Busca los Top 10 de una categoría y les guarda un documento en su colección "trofeos"
    private suspend fun awardOnlyTrophies(period: String, field: String, label: String) {
        // Decide si el mejor es el que tiene más (Km) o el que tiene menos (Ritmo/Pace)
        val direction = if (field.contains("pace")) Query.Direction.ASCENDING else Query.Direction.DESCENDING
        val snapshot = db.collection("ranking_users")
            .whereGreaterThan(field, 0) // Solo usuarios activos
            .orderBy(field, direction)
            .limit(10).get().await()

        if (snapshot.isEmpty) return
        val batch = db.batch()
        val cal = Calendar.getInstance()

        // Crear etiqueta de tiempo para el título del trofeo (ej: 2026_W12)
        val timeSuffix = when (period) {
            "week" -> "${cal.get(Calendar.YEAR)}_W${cal.get(Calendar.WEEK_OF_YEAR)}"
            "month" -> "${cal.get(Calendar.YEAR)}_M${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
            "year" -> "${cal.get(Calendar.YEAR)}_YEAR"
            else -> cal.get(Calendar.YEAR).toString()
        }

        snapshot.documents.forEachIndexed { index, doc ->
            val pos = index + 1
            val userId = doc.id
            // Define el tipo de medalla según la posición
            val type = when (pos) {
                1 -> "oro_top1"
                2 -> "plata_top2"
                3 -> "bronce_top3"
                else -> "plata_top50"
            }

            val trophyId = "trofeo_${field}_${timeSuffix}"
            val trophyRef = db.collection("usuarios").document(userId).collection("trofeos").document(trophyId)

            // Guarda el trofeo en el perfil del usuario
            batch.set(trophyRef, mapOf(
                "title" to "Top $pos en $label ($timeSuffix)",
                "type" to type,
                "date" to FieldValue.serverTimestamp(),
                "category" to label,
                "pos" to pos
            ), SetOptions.merge())
        }
        batch.commit().await()
    }

    // Pone a 0 los contadores de los usuarios cuando el periodo (semana/mes/año) ha expirado
    private suspend fun cleanDatabase(curYear: Int, curMonth: String, curWeek: Int) {
        val snapshot = db.collection("ranking_users").get().await()
        val batch = db.batch()

        for (doc in snapshot.documents) {
            val updates = mutableMapOf<String, Any>()
            val userYear = doc.getLong("last_update_year")?.toInt()
            val userMonth = doc.getString("last_update_month")
            val userWeek = doc.getLong("last_update_week")?.toInt()

            // Si la semana del usuario es vieja, resetear sus datos semanales
            if (userWeek != null && userWeek != curWeek) {
                updates["kilometers_week"] = 0.0
                updates["activities_week"] = 0
                updates["pace_week"] = 0L
                updates["last_update_week"] = curWeek
            }

            // Si el mes del usuario es viejo, resetear datos mensuales
            if (userMonth != null && userMonth != curMonth) {
                updates["kilometers_month"] = 0.0
                updates["activities_month"] = 0
                updates["pace_month"] = 0L
                updates["last_update_month"] = curMonth
            }

            // Si el año es viejo, resetear datos anuales
            if (userYear != null && userYear != curYear) {
                updates["kilometers_year"] = 0.0
                updates["activities_year"] = 0
                updates["pace_year"] = 0L
                updates["last_update_year"] = curYear
            }

            if (updates.isNotEmpty()) {
                batch.update(doc.reference, updates)
            }
        }
        batch.commit().await()
    }

    // Divide a todos los usuarios en grupos de 20 para crear ligas competitivas
    private suspend fun createNewLeagues(monthId: String) {
        val allUsers = db.collection("ranking_users")
            .orderBy("score_total_month", Query.Direction.DESCENDING) // Los mejores arriba
            .orderBy("activities_month", Query.Direction.DESCENDING)
            .get().await()

        val userDocuments = allUsers.documents
        val totalUsers = userDocuments.size
        val usersPerLeague = 20
        val batch = db.batch()

        // Crear documentos de liga cada 20 usuarios
        for (i in 0 until totalUsers step usersPerLeague) {
            val leagueIndex = (i / usersPerLeague) + 1
            val leagueId = "liga_${monthId}_div_$leagueIndex"
            val end = if (i + usersPerLeague > totalUsers) totalUsers else i + usersPerLeague
            val subList = userDocuments.subList(i, end)
            val memberIds = subList.map { it.id }

            // Guardar la liga
            batch.set(db.collection("ligas_mensuales").document(leagueId), mapOf(
                "month" to monthId,
                "division" to leagueIndex,
                "members" to memberIds,
                "status" to "active"
            ))

            // Actualizar a cada usuario indicándole a qué liga pertenece ahora
            memberIds.forEach { uid ->
                batch.update(db.collection("ranking_users").document(uid), "current_league_id", leagueId)
            }
        }
        batch.commit().await()
    }

    // Analiza las ligas del mes pasado, da premios en FitCoins y resetea los puntos (Score)
    private suspend fun awardLeaguePrizes(lastMonth: String) {
        val ligasSnapshot = db.collection("ligas_mensuales")
            .whereEqualTo("month", lastMonth).get().await()

        for (leagueDoc in ligasSnapshot.documents) {
            val memberIds = leagueDoc.get("members") as? List<String> ?: continue
            val division = leagueDoc.getLong("division") ?: 1
            val membersData = mutableListOf<LeagueUser>()

            // Obtener los puntos reales de cada miembro de esa liga
            for (uid in memberIds) {
                val uDoc = db.collection("ranking_users").document(uid).get().await()
                if (uDoc.exists()) {
                    membersData.add(LeagueUser(
                        userId = uid,
                        name = uDoc.getString("name") ?: "Atleta",
                        puntuacion = uDoc.getLong("score_total_month")?.toInt() ?: 0,
                        activities = uDoc.getLong("activities_month")?.toInt() ?: 0
                    ))
                }
            }

            // Ordenar los miembros por puntuación para saber quién ganó
            val ranked = membersData.sortedWith(compareByDescending<LeagueUser> { it.puntuacion }
                .thenByDescending { it.activities })

            val batch = db.batch()
            ranked.forEachIndexed { index, user ->
                val pos = index + 1
                // Tabla de premios en FitCoins
                val coins = when (pos) {
                    1 -> 100
                    2 -> 80
                    3 -> 60
                    4 -> 40
                    5 -> 20
                    6 -> 10
                    7 -> 5
                    else -> 0
                }

                val trophyId = "liga_final_$lastMonth"
                val trophyRef = db.collection("usuarios").document(user.userId).collection("trofeos").document(trophyId)

                // Guardar trofeo de liga
                batch.set(trophyRef, mapOf(
                    "title" to "Posición $pos - División $division ($lastMonth)",
                    "type" to if(pos <= 3) "oro_top1" else "plata_top50",
                    "date" to FieldValue.serverTimestamp(),
                    "category" to "Ligas Mensuales",
                    "pos" to pos,
                    "coins_awarded" to coins
                ), SetOptions.merge())

                // Incrementar las monedas en el perfil del usuario si ganó algo
                if (coins > 0) {
                    batch.update(db.collection("usuarios").document(user.userId), "coins", FieldValue.increment(coins.toLong()))
                }

                // IMPORTANTE: Resetear los puntos del ranking a 0 para el nuevo mes
                batch.update(db.collection("ranking_users").document(user.userId), "score_total_month", 0)
            }
            batch.commit().await()
        }
    }

    // Sincroniza el contador de territorios totales contando los documentos en "zonasGlobales"
    private suspend fun updateGlobalTerritories() {
        val allUsers = db.collection("ranking_users").get().await()
        for (userDoc in allUsers.documents) {
            val uid = userDoc.id
            val zonasSnapshot = db.collection("zonasGlobales")
                .whereEqualTo("propietarioUID", uid)
                .get().await()

            db.collection("ranking_users").document(uid)
                .update("territories_total", zonasSnapshot.size())
        }
    }
}