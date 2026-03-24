package com.example.fitvera // Asegúrate de que el paquete sea correcto

import java.io.Serializable

data class Task(
    var id: String = "",
    val title: String = "",
    val description: String = "",
    val date: String = "", // Formato yyyy-MM-dd
    val time: String = "",
    val isCompleted: Boolean = false // 🚀 CAMPO NUEVO para el estado de completado
) : Serializable // Se mantiene Serializable para poder pasar el objeto entre Activities/Fragments