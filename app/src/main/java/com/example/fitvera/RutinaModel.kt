import java.io.Serializable

data class Ejercicio(
    var nombre: String = "",
    var series: Int = 0,
    var reps: Int = 0,
    var peso: Double = 0.0
) : Serializable

data class Rutina(
    val nombreRutina: String = "",
    val tiempoMin: Int = 0,
    val dificultad: String = "",
    val ejercicios: List<Ejercicio> = listOf(),
    val userId: String = "",
    var id: String = "" // Añade esta línea si no la tienes
)