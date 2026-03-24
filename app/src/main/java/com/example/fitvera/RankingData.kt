package com.example.fitvera

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class RankingEntry(
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",

    @get:PropertyName("name") @set:PropertyName("name")
    var name: String = "Anónimo",

    @get:PropertyName("photoUrl") @set:PropertyName("photoUrl")
    var photoUrl: String? = null,

    @get:PropertyName("coins") @set:PropertyName("coins")
    var coins: Long = 0L,

    // Kilómetros (Usamos Double pero inicializamos con .0 para forzar el tipo)
    var kilometers_total: Double = 0.0,
    var kilometers_year: Double = 0.0,
    var kilometers_month: Double = 0.0,
    var kilometers_week: Double = 0.0,

    // Actividades
    var activities_total: Int = 0,
    var activities_year: Int = 0,
    var activities_month: Int = 0,
    var activities_week: Int = 0,

    // Ritmo
    var pace_total: Long = 0L,
    var pace_year: Long = 0L,
    var pace_month: Long = 0L,
    var pace_week: Long = 0L,

    // Territorios
    var territories_total: Int = 0,

    // Control (Importante: last_update_month como String para el filtro "2026-03")
    var last_update_week: Int = -1,
    var last_update_month: String? = null,
    var last_update_year: Int = -1
)

