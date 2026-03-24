// Archivo: com.example.fitvera/NotificacionSeguimiento.kt

package com.example.fitvera

// Enum actualizado: TipoSolicitud
enum class TipoSolicitud {
    ENVIADA, // Antes SENT
    RECIBIDA // Antes RECEIVED
}

// Clase de datos actualizada: NotificacionSeguimiento
data class NotificacionSeguimiento(
    val uid: String = "",
    val nombre: String = "",
    val fotoUrl: String? = null,
    val tipo: TipoSolicitud, // Usamos el nuevo enum
    val timestamp: Long = 0,
    var esLeida: Boolean = false // Antes isRead
)