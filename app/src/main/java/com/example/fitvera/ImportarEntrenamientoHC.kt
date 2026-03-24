package com.example.fitvera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionReadRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

class ImportarEntrenamientoHC : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 1001 // Código para identificar la petición de permisos de Android
    private val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1002 // Código para identificar la petición de permisos de Google Fit

    // Definición de qué tipos de datos queremos leer de Google Fit (Distancia, Calorías y Ubicación)
    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_READ)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_importar_entrenamiento_hc) // Infla la vista de carga
        // PASO 1: Inicia verificando los permisos básicos de Android (GPS y Reconocimiento de actividad)
        checkAndroidPermissions()
    }

    private fun checkAndroidPermissions() {
        // Lista de permisos necesarios para acceder a la ubicación y sensores de movimiento
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION)
        // Filtra cuáles no han sido concedidos aún por el usuario
        val toRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) {
            // Si falta alguno, solicita los permisos al sistema
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            // Si ya tiene todos, procede a la conexión con la cuenta de Google
            iniciarFlujoGoogleFit()
        }
    }

    private fun iniciarFlujoGoogleFit() {
        // Obtiene la cuenta de Google vinculada a las opciones de Fitness configuradas arriba
        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            // PASO 2: Si no hay permisos de Google Fit, lanza la ventana de selección de cuenta de Google
            // IMPORTANTE: Aquí NO lanzamos ninguna corrutina aún para evitar el cuelgue de la UI.
            GoogleSignIn.requestPermissions(this, GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, account, fitnessOptions)
        } else {
            // Si ya hay permisos, empieza a extraer los datos directamente
            fetchData()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            // PASO 3: El usuario ha respondido a la ventana de permisos de Google.
            // Usamos lifecycleScope para lanzar una tarea asíncrona segura para el ciclo de vida.
            lifecycleScope.launch {
                delay(1000) // Esta pausa de 1 segundo asegura que la ventana de Google se cierre antes de pedir datos
                fetchData() // Llama a la extracción de datos
            }
        }
    }

    private fun fetchData() {
        // Ejecuta la lógica pesada en un hilo secundario (IO) para no bloquear la pantalla
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withTimeout(20000) { // Si tarda más de 20 segundos, cancela por seguridad
                    val account = GoogleSignIn.getAccountForExtension(this@ImportarEntrenamientoHC, fitnessOptions)

                    // 1. Buscar última sesión: Escanea las últimas 48 horas de actividad
                    val sessionResponse = Fitness.getSessionsClient(this@ImportarEntrenamientoHC, account)
                        .readSession(SessionReadRequest.Builder()
                            .setTimeInterval(System.currentTimeMillis() - (48 * 3600000), System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                            .readSessionsFromAllApps() // Incluye datos de Strava, Garmin, Samsung Health, etc.
                            .build()).await()

                    // Filtra sesiones que no sean "Estar quieto" y busca la más reciente por tiempo de inicio
                    val lastSession = sessionResponse.sessions
                        .filter { it.activity != FitnessActivities.STILL }
                        .maxByOrNull { it.getStartTime(TimeUnit.MILLISECONDS) }

                    if (lastSession == null) {
                        // Si no encuentra nada, avisa al usuario y cierra la actividad
                        withContext(Dispatchers.Main) { errorAndFinish("No hay entrenamientos.") }
                        return@withTimeout
                    }

                    // 2. Leer Historial: Configura la petición de datos específicos para el rango de tiempo de la sesión hallada
                    val readRequest = DataReadRequest.Builder()
                        .read(DataType.TYPE_LOCATION_SAMPLE) // Ubicación GPS
                        .read(DataType.TYPE_DISTANCE_DELTA) // Segmentos de distancia
                        .read(DataType.TYPE_CALORIES_EXPENDED) // Calorías quemadas
                        .setTimeRange(lastSession.getStartTime(TimeUnit.MILLISECONDS), lastSession.getEndTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
                        .build()

                    // Ejecuta la lectura del historial de Google Fit
                    val dataResponse = Fitness.getHistoryClient(this@ImportarEntrenamientoHC, account)
                        .readData(readRequest).await()

                    val rutaPuntos = ArrayList<GeoPoint>() // Almacenará las coordenadas para el mapa
                    var tiempoActivoMilis: Long = 0 // Tiempo real en movimiento
                    var distanciaTotalAcumulada = 0.0 // Distancia total en metros
                    var caloriasTotales = 0.0 // Suma de calorías

                    // Variables para calcular el ritmo dinámico cada 100 metros (gráficas)
                    val ritmosPorCada100m = ArrayList<Double>()
                    var distanciaTramoActual = 0.0
                    var tiempoTramoActualMilis: Long = 0

                    // Obtenemos los puntos de distancia para procesarlos cronológicamente
                    val distancePoints = dataResponse.getDataSet(DataType.TYPE_DISTANCE_DELTA).dataPoints

                    distancePoints.forEach { dp ->
                        val d = dp.getValue(Field.FIELD_DISTANCE).asFloat().toDouble() // Distancia del punto actual
                        val t = dp.getEndTime(TimeUnit.MILLISECONDS) - dp.getStartTime(TimeUnit.MILLISECONDS) // Tiempo del punto

                        distanciaTotalAcumulada += d
                        tiempoActivoMilis += t

                        // Lógica para segmentar el entrenamiento en tramos de 100 metros
                        distanciaTramoActual += d
                        tiempoTramoActualMilis += t

                        if (distanciaTramoActual >= 100.0) {
                            // Cálculo de ritmo (Segundos por kilómetro) para este tramo de 100m
                            val ritmoSegKm = (tiempoTramoActualMilis / 1000.0) / (distanciaTramoActual / 1000.0)
                            ritmosPorCada100m.add(ritmoSegKm)

                            // Reinicia las variables del tramo para el siguiente bloque de 100m
                            distanciaTramoActual = 0.0
                            tiempoTramoActualMilis = 0
                        }
                    }

                    // Extraer Calorías sumando todos los puntos registrados
                    dataResponse.getDataSet(DataType.TYPE_CALORIES_EXPENDED).dataPoints.forEach {
                        caloriasTotales += it.getValue(Field.FIELD_CALORIES).asFloat()
                    }
                    // Extraer Ubicaciones convirtiéndolas a objetos GeoPoint para OSMDroid
                    dataResponse.getDataSet(DataType.TYPE_LOCATION_SAMPLE).dataPoints.forEach {
                        val lat = it.getValue(Field.FIELD_LATITUDE).asFloat().toDouble()
                        val lng = it.getValue(Field.FIELD_LONGITUDE).asFloat().toDouble()
                        if (lat != 0.0) rutaPuntos.add(GeoPoint(lat, lng)) // Evita puntos nulos
                    }

                    // Si al final quedó un tramo pequeño (ej: final de carrera), calcula su ritmo también
                    if (distanciaTramoActual > 10.0) {
                        val ritmoFinal = (tiempoTramoActualMilis / 1000.0) / (distanciaTramoActual / 1000.0)
                        ritmosPorCada100m.add(ritmoFinal)
                    }

                    // Regresa al hilo principal para enviar los datos a la pantalla de resultados
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@ImportarEntrenamientoHC, ResultadosEntrenamiento::class.java).apply {
                            putExtra("distancia", distanciaTotalAcumulada) // Metros totales
                            putExtra("tiempo", tiempoActivoMilis) // Tiempo de movimiento real
                            putExtra("calorias", caloriasTotales) // Kcal totales
                            putExtra("puntosRecorrido", rutaPuntos) // Lista de GPS
                            putExtra("ritmosNumericos", ritmosPorCada100m) // Datos para la gráfica de ritmos
                            putExtra("esImportado", true) // Bandera para indicar que viene de Google Fit
                        }
                        startActivity(intent)
                        finish() // Cierra esta actividad de transición
                    }
                }
            } catch (e: Exception) {
                // Si algo falla (timeout, error de red), muestra el error y sale
                withContext(Dispatchers.Main) { errorAndFinish("Error: ${e.message}") }
            }
        }
    }

    private fun errorAndFinish(msg: String) {
        // Función auxiliar para mostrar un mensaje rápido y cerrar la actividad
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}