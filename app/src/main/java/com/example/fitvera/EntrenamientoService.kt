package com.example.fitvera


import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint
import java.io.Serializable
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

// El servicio que se ejecutará en segundo plano para el seguimiento del entrenamiento
class EntrenamientoService : Service() {

    // Clientes y variables para el seguimiento de la ubicación
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    // Variables para el cálculo de métricas del entrenamiento
    private var distanciaTotal = 0.0
    private var desnivelTotal = 0.0
    private var ultimaPosicion: Location? = null
    private var distanciaUltimoTramo = 0.0
    private var tiempoUltimoTramo = 0L
    private var tramosCompletados = 0

    // Variables para el sensor de presión (barómetro)
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private var lastAltitudeBaro: Double? = null

    // VARIABLES PARA MEJORA DE PRECISIÓN DE DESNIVEL
    private var altitudSuavizada: Double? = null
    private val ALPHA = 0.15f
    private var acumuladorDesnivel = 0.0
    private val UMBRAL_DESNIVEL_REAL = 1.5

    // Variables para el manejo de notificaciones
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ENTRENAMIENTO_CHANNEL"
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    // Variables para el cronómetro
    private var startTime = 0L
    private var timeElapsed = 0L
    private var cronometroHandler = Handler(Looper.getMainLooper())
    private var cronometroRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isPaused) {
                // Calcula el tiempo transcurrido descontando el tiempo de pausa
                timeElapsed = System.currentTimeMillis() - startTime - pauseDurationTotal
                // Envía el estado actual a la UI y envia la notificacion
                actualizarNotificacionCronometro(timeElapsed)
                enviarEstadoActual(timeElapsed)
            }
            // Repite el runnable cada segundo
            cronometroHandler.postDelayed(this, 1000)
        }
    }
    // Banderas de estado del entrenamiento
    private var isRunning = false
    private var isPaused = false
    private var isAutoPaused = false

    // Listas para almacenar datos del entrenamiento
    private val ritmosPorTramo = ArrayList<String>()
    private val puntosRecorrido = ArrayList<GeoPoint>()
    private val ritmosNumericos = ArrayList<Double>()
    private var distanciaUltimoTramo01 = 0.0
    private var tiempoUltimoTramo01 = 0L
    private val ritmosNumericos01 = ArrayList<Double>()

    // Variables para la pausa automática
    private val pausaAutomaticaHandler = Handler(Looper.getMainLooper())
    private val PAUSA_AUTOMATICA_DELAY: Long = 3000 // Menos tiempo para pausar

    private val ultimasVelocidades = ArrayList<Double>()
    private val MAX_VELOCIDADES = 5

    private var distanciaVentana = 0.0
    private var tiempoVentana = System.currentTimeMillis()

    private var pauseStartTime = 0L
    private var pauseDurationTotal = 0L

    // Runnable para la pausa automática
    private val pausaAutomaticaRunnable = Runnable {
        if (!isPaused) {
            pausarEntrenamiento(automatica = true) // Llama a pausar si no se detecta movimiento
        }
    }

    // Nombres de acciones para los intents del servicio
    companion object {
        const val ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"
        const val ACTION_PAUSE_TRAINING = "ACTION_PAUSE_TRAINING"
        const val ACTION_RESUME_TRAINING = "ACTION_RESUME_TRAINING"
        const val ACTION_GET_STATUS = "ACTION_GET_STATUS"
        const val UPDATE_ACTION = "FITVERA_ENTRENAMIENTO_UPDATE"
    }

    // Listener para el sensor de presión
    private val baroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (isRunning && !isPaused) {
                val pressure = event.values[0]

                // Calcula la altitud a partir de la presión atmosférica
                val altitudeRaw = 44330.0 * (1.0 - Math.pow((pressure / 1013.25), 0.1903))

                // --- MEJORA: FILTRO PASO BAJO ---
                if (altitudSuavizada == null) altitudSuavizada = altitudeRaw
                altitudSuavizada = altitudSuavizada!! + ALPHA * (altitudeRaw - altitudSuavizada!!)

                if (lastAltitudeBaro != null) {
                    val diff = altitudSuavizada!! - lastAltitudeBaro!!

                    // --- MEJORA: ACUMULADOR DE UMBRAL ---
                    if (Math.abs(diff) < 5.0) { // Ignorar picos imposibles
                        acumuladorDesnivel += diff

                        if (acumuladorDesnivel >= UMBRAL_DESNIVEL_REAL) {
                            desnivelTotal += acumuladorDesnivel
                            acumuladorDesnivel = 0.0
                        } else if (acumuladorDesnivel <= -UMBRAL_DESNIVEL_REAL) {
                            acumuladorDesnivel = 0.0 // Reset en bajadas para limpiar ruido
                        }
                    }
                }
                lastAltitudeBaro = altitudSuavizada
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Se llama cuando el servicio es creado
    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        // Inicializa los clientes y gestores del servicio
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(1000)
            .build()
        locationCallback = object : LocationCallback() {
            @RequiresApi(Build.VERSION_CODES.Q)
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    // Llama a actualizar datos por cada nueva ubicación
                    actualizarDatos(location)
                }
            }
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        //Crea el costructor de la notificacion
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        crearCanalNotificacion()
        notificationBuilder = crearNotificationBuilder()
    }

    // Se llama cuando un componente inicia el servicio con un intent
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Inicia el servicio en primer plano para evitar que sea detenido por el sistema
            ACTION_START_FOREGROUND_SERVICE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notificationBuilder.build())
                }
                iniciarSeguimiento()
            }

            //Detencion del servicio
            ACTION_STOP_FOREGROUND_SERVICE -> {
                detenerSeguimiento()
                stopForeground(true)
                stopSelf()
            }

            //Pausa del entrenamiento manual
            ACTION_PAUSE_TRAINING -> {
                pausarEntrenamiento(automatica = false)
            }

            //Reanudación del Entrenamiento manual
            ACTION_RESUME_TRAINING -> {
                reanudarEntrenamiento(manual = true)
            }

            //Envia estado actual del entrenamiento
            ACTION_GET_STATUS -> {
                enviarEstadoActual()
            }
        }
        return START_STICKY
    }

    // Inicia el seguimiento del entrenamiento
    @SuppressLint("MissingPermission")

    private fun iniciarSeguimiento() {

        // Resetea todas las variables y comienza el cronómetro y el seguimiento de ubicación
        isRunning = true
        isPaused = false
        isAutoPaused = false
        distanciaTotal = 0.0
        desnivelTotal = 0.0
        acumuladorDesnivel = 0.0 // Reset acumulador
        altitudSuavizada = null // Reset suavizado
        distanciaUltimoTramo = 0.0
        tiempoUltimoTramo = System.currentTimeMillis()
        tramosCompletados = 0
        ultimaPosicion = null
        lastAltitudeBaro = null
        ritmosPorTramo.clear()
        puntosRecorrido.clear()
        ritmosNumericos.clear()
        ritmosNumericos01.clear()
        distanciaUltimoTramo01 = 0.0
        tiempoUltimoTramo01 = System.currentTimeMillis()
        distanciaVentana = 0.0
        tiempoVentana = System.currentTimeMillis()
        timeElapsed = 0L
        startTime = System.currentTimeMillis()
        pauseDurationTotal = 0L
        cronometroHandler.post(cronometroRunnable)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocation.requestLocationUpdates(locationRequest, locationCallback, null)
        }
        if (pressureSensor != null) {
            sensorManager?.registerListener(baroListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    //Detencion del servicio
    //Detiene ubicación , sensor y cronometro
    private fun detenerSeguimiento() {
        isRunning = false
        fusedLocation.removeLocationUpdates(locationCallback)
        sensorManager?.unregisterListener(baroListener)
        cronometroHandler.removeCallbacks(cronometroRunnable)
    }

    //Pausa de entrenamiento automatica
    private fun pausarEntrenamiento(automatica: Boolean) {
        if (!isPaused) {
            isPaused = true
            isAutoPaused = automatica
            // Guarda el momento de la pausa
            pauseStartTime = System.currentTimeMillis()
            cronometroHandler.removeCallbacks(cronometroRunnable)
            pausaAutomaticaHandler.removeCallbacks(pausaAutomaticaRunnable)
            ultimaPosicion = null
            enviarEstadoActual()

            // Actualiza la notificación a estado "pausado"
            actualizarNotificacion(distanciaTotal, 0.0, true)
        }
    }

    // Reanudacion del entrenamiento
    @SuppressLint("MissingPermission")
    private fun reanudarEntrenamiento(manual: Boolean = false) {
        // Reanuda solo si se pausó automáticamente o si la reanudación es manual
        if ((isAutoPaused && !manual) || manual) {
            isPaused = false
            isAutoPaused = false
            val pauseDuration = System.currentTimeMillis() - pauseStartTime
            // Acumula el tiempo total de pausa para luego restarlo
            pauseDurationTotal += pauseDuration
            tiempoUltimoTramo += pauseDuration
            tiempoUltimoTramo01 += pauseDuration
            enviarEstadoActual()
            actualizarNotificacion(distanciaTotal, 0.0, false)
            cronometroHandler.post(cronometroRunnable)
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocation.requestLocationUpdates(locationRequest, locationCallback, null)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun actualizarDatos(location: Location) {
        if (isPaused) {
            // Lógica de pausa automática basada en la velocidad
            val velocidad = location.speed.toDouble().coerceAtLeast(0.0)
            if (ultimasVelocidades.size >= MAX_VELOCIDADES) ultimasVelocidades.removeAt(0)
            ultimasVelocidades.add(velocidad)

            val velocidadMedia = if (ultimasVelocidades.isNotEmpty()) ultimasVelocidades.average() else 0.0
            val umbralVelocidad = 0.5 // m/s
            val umbralMovimiento = 5.0 // metros

            if (isAutoPaused && velocidadMedia >= umbralVelocidad) {
                reanudarEntrenamiento(manual = false)
            }
            ultimaPosicion = location
            return
        }

        // --- INICIO DE FILTROS DE FIABILIDAD ---

        // 1. FILTRO POR PRECISIÓN (ACCURACY)
        val UMBRAL_PRECISION = 25.0f // No aceptar puntos con > 25m de error
        if (location.accuracy > UMBRAL_PRECISION) {
            return // Punto descartado por baja precisión
        }

        if (ultimaPosicion != null) {

            val incremento = ultimaPosicion!!.distanceTo(location)

            // 2. FILTRO POR VELOCIDAD MÁXIMA (SPIKE REJECTION)
            val tiempoDeltaSegundos = (location.time - ultimaPosicion!!.time) / 1000.0
            if (tiempoDeltaSegundos > 0.5) { // Solo comprobar si ha pasado al menos medio segundo
                val velocidadCalculada = incremento / tiempoDeltaSegundos
                // 15 m/s (54 km/h) es un buen tope para descartar saltos.
                val UMBRAL_VELOCIDAD_MAXIMA = 15.0

                if (velocidadCalculada > UMBRAL_VELOCIDAD_MAXIMA) {
                    // Es un salto de GPS, no un movimiento real.
                    // No actualizamos ultimaPosicion para que el siguiente punto
                    // se compare con el último punto VÁLIDO.
                    return // Punto descartado por "salto"
                }
            }

            // --- FIN DE FILTROS DE FIABILIDAD ---

            // Si el punto es válido, SUMAMOS la distancia
            distanciaTotal += incremento
            distanciaUltimoTramo += incremento
            distanciaUltimoTramo01 += incremento
            distanciaVentana += incremento

            // Lógica de Desnivel (Solo si no hay Barómetro disponible)
            if (pressureSensor == null && location.hasAltitude() && ultimaPosicion!!.hasAltitude()) {
                // Solo confiar en el GPS si la precisión es aceptable
                if (location.accuracy < 15.0f) {
                    val diffAltura = location.altitude - ultimaPosicion!!.altitude
                    // --- MEJORA: UMBRAL DE GPS MÁS ALTO PARA EVITAR RUIDO ---
                    if (diffAltura > 3.0) {
                        desnivelTotal += diffAltura
                    }
                }
            }

            // ... (el resto de la lógica de auto-pause, ritmo, etc.)

            val ahora = System.currentTimeMillis()
            val ventanaTiempo = 5_000L // El servicio revisará el movimiento cada 5 segundos
            val umbralMovimiento = 5.0
            val umbralVelocidad = 0.5

            val velocidad = location.speed.toDouble().coerceAtLeast(0.0)
            if (ultimasVelocidades.size >= MAX_VELOCIDADES) ultimasVelocidades.removeAt(0)
            ultimasVelocidades.add(velocidad)

            if (ahora - tiempoVentana >= ventanaTiempo) {
                val velocidadMedia = if (ultimasVelocidades.isNotEmpty()) ultimasVelocidades.average() else 0.0

                if (velocidadMedia < umbralVelocidad && distanciaVentana < umbralMovimiento) {
                    if (!isPaused && !pausaAutomaticaHandler.hasCallbacks(pausaAutomaticaRunnable)) {
                        pausaAutomaticaHandler.postDelayed(pausaAutomaticaRunnable, PAUSA_AUTOMATICA_DELAY)
                    }
                } else {
                    pausaAutomaticaHandler.removeCallbacks(pausaAutomaticaRunnable)
                    if (isPaused && isAutoPaused) {
                        cronometroHandler.post { reanudarEntrenamiento(manual = false) }
                    }
                }

                distanciaVentana = 0.0
                tiempoVentana = ahora
            }

        }


        // Actualizamos ultimaPosicion SÓLO si el punto ha sido válido
        ultimaPosicion = location

        // Guarda la ubicación para el mapa
        puntosRecorrido.add(GeoPoint(location.latitude, location.longitude))

        // Lógica para el cálculo del ritmo por tramos
        val tiempoTramo = System.currentTimeMillis() - tiempoUltimoTramo
        val ritmoProvisional = if (distanciaUltimoTramo > 0) {
            tiempoTramo.toDouble() / 1000.0 / (distanciaUltimoTramo / 1000.0)
        } else {
            0.0
        }

        if (distanciaUltimoTramo01 >= 100.0) {
            val tiempoTramo01Now = System.currentTimeMillis() - tiempoUltimoTramo01
            val ritmoTramo01 = tiempoTramo01Now.toDouble() / 1000.0 / (distanciaUltimoTramo01 / 1000.0)
            ritmosNumericos01.add(ritmoTramo01)
            distanciaUltimoTramo01 = 0.0
            tiempoUltimoTramo01 = System.currentTimeMillis()
        }

        enviarEstadoActual(timeElapsed)

        if (distanciaUltimoTramo >= 500.0) {
            val tiempoTramo500 = System.currentTimeMillis() - tiempoUltimoTramo
            val ritmoTramo = tiempoTramo500.toDouble() / 1000.0 / (distanciaUltimoTramo / 1000.0)
            val minutos = (ritmoTramo / 60).toInt()
            val segundos = (ritmoTramo % 60).roundToInt()
            val ritmoTexto = "Tramo ${"%.1f".format((tramosCompletados + 1) * 0.5)} km: $minutos:${String.format("%02d", segundos)} min/km"
            ritmosPorTramo.add(ritmoTexto)
            ritmosNumericos.add(ritmoTramo)
            tiempoUltimoTramo = System.currentTimeMillis()
            distanciaUltimoTramo = 0.0
            tramosCompletados++
        }
        actualizarNotificacion(distanciaTotal, ritmoProvisional, isPaused)
    }

    // Envía el estado actual del entrenamiento a la UI a través de un broadcast
    private fun enviarEstadoActual(tiempoTranscurrido: Long? = null) {
        val tiempoTramo = System.currentTimeMillis() - tiempoUltimoTramo
        val ritmoProvisional = if (distanciaUltimoTramo > 0) {
            tiempoTramo.toDouble() / 1000.0 / (distanciaUltimoTramo / 1000.0)
        } else {
            0.0
        }
        val intent = Intent(UPDATE_ACTION)
        // Añade todos los datos relevantes al intent
        intent.putExtra("distancia", distanciaTotal)
        intent.putExtra("desnivel", desnivelTotal)
        intent.putExtra("latitud", ultimaPosicion?.latitude ?: 0.0)
        intent.putExtra("longitud", ultimaPosicion?.longitude ?: 0.0)
        intent.putExtra("ritmoProvisional", ritmoProvisional)
        intent.putExtra("tiempoTranscurrido", tiempoTranscurrido ?: timeElapsed)
        intent.putExtra("runningService", isRunning)
        intent.putExtra("pausedService", isPaused)
        intent.putExtra("isAutoPaused", isAutoPaused)
        intent.putExtra("listaRitmos", ritmosPorTramo as Serializable)
        intent.putExtra("puntosRecorrido", puntosRecorrido as Serializable)
        intent.putExtra("ritmosNumericos", ritmosNumericos as Serializable)
        intent.putExtra("ritmosNumericos01", ritmosNumericos01 as Serializable)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // Actualiza el texto de la notificación con la distancia y el ritmo
    private fun actualizarNotificacion(distancia: Double, ritmo: Double, isPaused: Boolean) {
        val text: String = if (isPaused) {
            "Entrenamiento pausado. Distancia: ${"%.2f".format(distancia / 1000)} km"
        } else {
            val min = (ritmo / 60).toInt()
            val seg = (ritmo % 60).roundToInt()
            "Distancia: ${"%.2f".format(distancia / 1000)} km\nRitmo: $min:${String.format("%02d", seg)} min/km"
        }
        notificationBuilder.setContentText(text)
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // Actualiza el título de la notificación con el cronómetro
    private fun actualizarNotificacionCronometro(tiempo: Long) {
        val horas = TimeUnit.MILLISECONDS.toHours(tiempo)
        val minutos = TimeUnit.MILLISECONDS.toMinutes(tiempo) % 60
        val segundos = TimeUnit.MILLISECONDS.toSeconds(tiempo) % 60

        if (!isPaused) {
            notificationBuilder.setContentTitle(
                "Entrenamiento: ${String.format("%02d:%02d:%02d", horas, minutos, segundos)}"
            )
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    // Crea el canal de notificación para versiones de Android 8.0 y superiores
    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Entrenamiento activo",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Crea y configura el constructor de la notificación
    private fun crearNotificationBuilder(): NotificationCompat.Builder {
        val notificationIntent = Intent(this, RegistrarEntrenamiento::class.java)
        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntentWithParentStack(Intent(this, MainActivity2::class.java))
        stackBuilder.addNextIntent(notificationIntent)

        val pendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Entrenamiento en curso")
            .setContentText("Calculando datos...")
            .setSmallIcon(R.drawable.logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Se llama cuando el servicio está siendo destruido
    override fun onDestroy() {
        detenerSeguimiento()
        super.onDestroy()
    }
}