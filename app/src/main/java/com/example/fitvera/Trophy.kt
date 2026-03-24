package com.example.fitvera

// Clase de datos simple y robusta.
// Usar VAL con valores por defecto es lo más seguro para Firebase/Firestore.
data class Trophy(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val type: String = ""
)