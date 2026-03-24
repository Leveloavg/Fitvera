package com.example.fitvera

import Ejercicio
import com.google.firebase.firestore.IgnoreExtraProperties
import java.io.Serializable
import java.util.HashMap

@IgnoreExtraProperties
data class Entrenamiento(
    var id: String? = null,
    var userId: String = "",
    var nombre: String = "",
    var fecha: String = "",
    var tipo: String = "Carrera", // 🚀 NUEVO: Para diferenciar entre "Carrera" y "Fuerza"
    var distancia: Double = 0.0,
    var desnivel: Double = 0.0,
    var tiempo: Long = 0L,
    var ritmoPromedio: Double = 0.0,
    var calorias: Double = 0.0,
    var dificultad: String = "",
    var fotoUrl: String? = null,
    val puntuacion: Int = 0,
    var puntosRecorrido: List<HashMap<String, Any>> = emptyList(),
    var ritmosNumericos: List<Double> = emptyList(),
    var ejercicios: List<Ejercicio>? = null // 🚀 NUEVO: Para guardar la lista de ejercicios de fuerza
) : Serializable {

    // Constructor sin argumentos requerido por Firebase
    constructor() : this(
        id = null,
        userId = "",
        nombre = "",
        fecha = "",
        tipo = "Carrera",
        distancia = 0.0,
        desnivel = 0.0,
        tiempo = 0L,
        ritmoPromedio = 0.0,
        calorias = 0.0,
        dificultad = "",
        fotoUrl = null,
        puntosRecorrido = emptyList(),
        ritmosNumericos = emptyList(),
        ejercicios = null
    )
}