package com.example.fitvera

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class RankingCalculator(private val firestore: FirebaseFirestore) {

    suspend fun recalculateUserStats(userId: String) {
        try {
            val userDoc = firestore.collection("usuarios").document(userId).get().await()
            val name = userDoc.getString("nombre") ?: "Corredor"

            // 🚩 NUEVO: Contar territorios reales en la colección global
            val zonasSnapshot = firestore.collection("zonasGlobales")
                .whereEqualTo("propietarioUID", userId)
                .get().await()
            val totalTerritorios = zonasSnapshot.size()

            val entrenamientosSnapshot = firestore.collection("usuarios").document(userId)
                .collection("entrenamientos").get().await()

            val calendar = Calendar.getInstance()
            val curYear = calendar.get(Calendar.YEAR)
            val curMonth = calendar.get(Calendar.MONTH)
            val curWeek = calendar.get(Calendar.WEEK_OF_YEAR)

            var tDist = 0.0; var tTime = 0L; var tAct = 0
            var yDist = 0.0; var yTime = 0L; var yAct = 0
            var mDist = 0.0; var mTime = 0L; var mAct = 0
            var wDist = 0.0; var wTime = 0L; var wAct = 0

            val df = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

            for (doc in entrenamientosSnapshot.documents) {
                val e = doc.toObject(Entrenamiento::class.java) ?: continue

                tDist += e.distancia
                tTime += e.tiempo
                tAct++

                val date = try { df.parse(e.fecha) } catch (ex: Exception) { null } ?: continue
                calendar.time = date
                if (calendar.get(Calendar.YEAR) == curYear) {
                    yDist += e.distancia; yTime += e.tiempo; yAct++
                    if (calendar.get(Calendar.MONTH) == curMonth) {
                        mDist += e.distancia; mTime += e.tiempo; mAct++
                    }
                    if (calendar.get(Calendar.WEEK_OF_YEAR) == curWeek) {
                        wDist += e.distancia; wTime += e.tiempo; wAct++
                    }
                }
            }

            val rankingData = RankingEntry(
                userId = userId,
                name = name,
                photoUrl = userDoc.getString("fotoUrl"),
                kilometers_total = tDist / 1000.0,
                kilometers_year = yDist / 1000.0,
                kilometers_month = mDist / 1000.0,
                kilometers_week = wDist / 1000.0,
                activities_total = tAct,
                activities_year = yAct,
                activities_month = mAct,
                activities_week = wAct,
                pace_total = calculatePace(tTime, tDist / 1000.0),
                pace_year = calculatePace(yTime, yDist / 1000.0),
                pace_month = calculatePace(mTime, mDist / 1000.0),
                pace_week = calculatePace(wTime, wDist / 1000.0),
                territories_total = totalTerritorios, // 🚩 CAMPO ACTUALIZADO
                last_update_week = curWeek,
                last_update_month = "$curYear-${curMonth + 1}",
                last_update_year = curYear
            )

            firestore.collection("ranking_users").document(userId)
                .set(rankingData, SetOptions.merge()).await()

        } catch (e: Exception) {
            Log.e("RankingCalc", "Error recalculando stats: ${e.message}")
        }
    }

    private fun calculatePace(timeMillis: Long, distKm: Double): Long {
        return if (distKm > 0.0) ((timeMillis / 1000.0) / distKm).toLong() else 0L
    }
}