package com.example.fitvera

// Define una interfaz llamada HomeFragmentListener.
// Una interfaz es un "contrato" o una plantilla que define un conjunto de métodos,
// pero no implementa el código de esos métodos.
// Esto se usa comúnmente en Android para la comunicación segura y desacoplada
// entre un Fragmento y su Actividad contenedora.

interface HomeFragmentListener {
    fun abrirPlanificar()
    fun abrirRegistrar()
    fun abrirVerEntrenamientos()
    fun abrirLogros()
    fun abrirRanking()
    fun CerrarAplicacion()
    fun abrirTerritorios()
    fun CerrarSesion()

}