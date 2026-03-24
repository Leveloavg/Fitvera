package com.example.fitvera

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.*
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

class RegistrarEntrenamiento : AppCompatActivity() {

    // Declaración de variables para los componentes de la UI y la lógica de la actividad.
    private lateinit var map: MapView
    private lateinit var tvCronometro: TextView
    private lateinit var tvRitmo: TextView
    private lateinit var tvDistancia: TextView
    private lateinit var tvDesnivel: TextView
    private lateinit var btnIniciar: Button
    private lateinit var btnPausar: Button
    private lateinit var llRitmos: LinearLayout
    private lateinit var tvPausado: TextView
    private lateinit var btnFinalizar: Button
    private lateinit var btnExit: ImageButton
    private lateinit var tvDate: TextView

    // Nuevas variables para el selector de deporte
    private lateinit var containerChangeSport: LinearLayout
    private lateinit var badgeIndicator: View

    // Variables de estado del entrenamiento
    private var running = false
    private var paused = false
    private var startTime = 0L
    private var pauseTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var recorrido: Polyline? = null
    private var fadeIn = true
    private val handlerParpadeo = Handler(Looper.getMainLooper())
    private val parpadeoRunnable = object : Runnable {

        override fun run() {
            // Alterna la opacidad para crear un efecto de parpadeo
            tvPausado.alpha = if (fadeIn) 1f else 0.2f
            fadeIn = !fadeIn
            handlerParpadeo.postDelayed(this, 600)
        }
    }

    // Variables para almacenar los datos finales del entrenamiento
    private var distanciaFinal = 0.0
    private var desnivelFinal = 0.0
    private var tiempoFinal = 0L
    private var ritmoFinal = 0.0
    private var puntosRecorridoFinal: ArrayList<GeoPoint> = ArrayList()
    private var ritmosNumericosFinal: ArrayList<Double> = ArrayList()
    private var ritmosNumericos01Final: ArrayList<Double> = ArrayList()

    // Este BroadcastReceiver se encarga de recibir los datos del EntrenamientoService
    private val broadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            // Verifica si la acción del Intent es la de actualización del servicio
            if (intent?.action == EntrenamientoService.UPDATE_ACTION) {
                // Extrae los datos del Intent
                distanciaFinal = intent.getDoubleExtra("distancia", 0.0)
                desnivelFinal = intent.getDoubleExtra("desnivel", 0.0)
                val latitud = intent.getDoubleExtra("latitud", 0.0)
                val longitud = intent.getDoubleExtra("longitud", 0.0)
                ritmoFinal = intent.getDoubleExtra("ritmoProvisional", 0.0)
                val ritmoTramo = intent.getDoubleExtra("ritmoTramo", -1.0)
                val tramoCompletado = intent.getIntExtra("tramoCompletado", -1)
                tiempoFinal = intent.getLongExtra("tiempoTranscurrido", 0L)
                val listaRitmosGuardada = intent.getSerializableExtra("listaRitmos") as? ArrayList<String>
                val runningService = intent.getBooleanExtra("runningService", false)
                val pausedService = intent.getBooleanExtra("pausedService", false)
                val isAutoPaused = intent.getBooleanExtra("isAutoPaused", false)
                puntosRecorridoFinal = intent.getSerializableExtra("puntosRecorrido") as? ArrayList<GeoPoint> ?: ArrayList()

                // Actualiza las listas de ritmos con los datos del servicio
                if (ritmoTramo != -1.0) {
                    ritmosNumericosFinal.add(ritmoTramo)
                }

                val ritmosNumericosGuardados = intent.getSerializableExtra("ritmosNumericos") as? ArrayList<Double>
                if (ritmosNumericosGuardados != null) {
                    ritmosNumericosFinal = ritmosNumericosGuardados
                }

                val ritmosNumericos01Guardados = intent.getSerializableExtra("ritmosNumericos01") as? ArrayList<Double>
                if (ritmosNumericos01Guardados != null) {
                    ritmosNumericos01Final = ritmosNumericos01Guardados
                }

                // Actualiza los TextViews del cronómetro, distancia, desnivel y ritmo.
                val horas = TimeUnit.MILLISECONDS.toHours(tiempoFinal)
                val minutos = TimeUnit.MILLISECONDS.toMinutes(tiempoFinal) % 60
                val segundos = TimeUnit.MILLISECONDS.toSeconds(tiempoFinal) % 60
                tvCronometro.text = String.format("%02d:%02d:%02d", horas, minutos, segundos)

                tvDistancia.text = " %.2f km".format(distanciaFinal / 1000)
                tvDesnivel.text = " %.0f m".format(desnivelFinal)
                val minProvisional = (ritmoFinal / 60).toInt()
                val segProvisional = (ritmoFinal % 60).roundToInt()
                tvRitmo.text = "$minProvisional:${String.format("%02d", segProvisional)} min/km"


                // Actualiza el mapa con la nueva ruta
                if (recorrido == null) {
                    recorrido = Polyline()
                    map.overlays.add(recorrido)
                    recorrido?.outlinePaint?.strokeWidth = 8f
                    recorrido?.outlinePaint?.color = 0xFF2196F3.toInt()
                }
                if (puntosRecorridoFinal.isNotEmpty()) {
                    recorrido?.setPoints(puntosRecorridoFinal)
                    map.controller.setCenter(puntosRecorridoFinal.last())
                } else {
                    val nuevaPos = GeoPoint(latitud, longitud)
                    val puntos = recorrido?.points?.toMutableList() ?: mutableListOf()
                    puntos.add(nuevaPos)
                    recorrido?.setPoints(puntos)
                    map.controller.setCenter(nuevaPos)
                }
                map.invalidate()


                // Actualiza la lista de ritmos por tramo en la UI
                if (ritmoTramo != -1.0) {
                    val minutosTramo = (ritmoTramo / 60).toInt()
                    val segundosTramo = (ritmoTramo % 60).roundToInt()
                    val tvRitmoTramo = TextView(this@RegistrarEntrenamiento)
                    tvRitmoTramo.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    tvRitmoTramo.text = "Tramo ${"%.1f".format(tramoCompletado * 0.5)} km: $minutosTramo:${String.format("%02d", segundosTramo)} min/km"

                    if (llRitmos.childCount >= 6) {
                        llRitmos.removeViewAt(llRitmos.childCount - 1)
                    }
                    llRitmos.addView(tvRitmoTramo, 0)
                }

                if (listaRitmosGuardada != null) {
                    llRitmos.removeAllViews()
                    val ritmosToShow = if (listaRitmosGuardada.size > 6) {
                        listaRitmosGuardada.subList(listaRitmosGuardada.size - 6, listaRitmosGuardada.size)
                    } else {
                        listaRitmosGuardada
                    }
                    ritmosToShow.forEach { ritmo ->
                        val tvRitmoTramo = TextView(this@RegistrarEntrenamiento)
                        tvRitmoTramo.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.CENTER
                        }
                        tvRitmoTramo.text = ritmo
                        llRitmos.addView(tvRitmoTramo, 0)
                    }
                }

                running = runningService
                paused = pausedService
                // Sincroniza el estado de la UI con el servicio
                actualizarEstadoUI(isAutoPaused)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Carga la configuración de OSMdroid para el mapa
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.registrarentrenamiento)

        // Inicialización de las vistas de la UI
        tvDate = findViewById(R.id.tv_date)
        btnExit = findViewById(R.id.btn_exit)
        tvCronometro = findViewById(R.id.tvcronometro)
        tvCronometro.text = "00:00:00"
        tvRitmo = findViewById(R.id.tvritmo)
        tvDistancia = findViewById(R.id.tvdistancia)
        tvDesnivel = findViewById(R.id.tvdesnivel)
        btnIniciar = findViewById(R.id.btniniciar)
        btnPausar = findViewById(R.id.btnpausar)
        llRitmos = findViewById(R.id.ll_ritmos_por_km)
        tvPausado = findViewById(R.id.tvPausado)
        btnFinalizar = findViewById(R.id.btnfinalizar)
        map = findViewById(R.id.mapa)

        // Inicialización de los nuevos componentes del selector
        containerChangeSport = findViewById(R.id.container_change_sport)
        badgeIndicator = findViewById(R.id.badge_indicator)

        // Configuración inicial del mapa (zoom, controles, etc.)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(16.0)

        // Inicializa el Polyline para dibujar la ruta
        recorrido = Polyline()
        map.overlays.add(recorrido)
        recorrido?.outlinePaint?.strokeWidth = 8f
        recorrido?.outlinePaint?.color = 0xFF2196F3.toInt()

        // Muestra la fecha actual en la UI
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE, d 'de' MMM", Locale("es", "ES"))
        val formattedDate = dateFormat.format(calendar.time)
        tvDate.text = formattedDate

        // Iniciar animación de rebote en el selector para llamar la atención del usuario
        val bounceAnim = AnimationUtils.loadAnimation(this, R.anim.bounce_slow)
        containerChangeSport.startAnimation(bounceAnim)

        // Manejadores de eventos de los botones
        btnExit.setOnClickListener {
            startActivity(Intent(this, MainActivity2::class.java))
            finish()
        }

        btnIniciar.setOnClickListener {
            iniciarEntrenamiento()
            actualizarEstadoUI(false)
        }
        btnPausar.setOnClickListener {
            if (running && !paused) {
                pausarEntrenamiento()
                actualizarEstadoUI(false)
            } else if (running && paused) {
                reanudarEntrenamiento()
                actualizarEstadoUI(false)
            }
        }
        btnFinalizar.setOnClickListener {
            if (running) {
                val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
                builder.setTitle("Finalizar entrenamiento")
                builder.setMessage("¿Estás seguro de que quieres finalizar el entrenamiento?")
                builder.setPositiveButton("Sí") { _, _ ->
                    reproducirSonidoYVibracion()
                    finalizarEntrenamiento()
                    actualizarEstadoUI(false)
                }
                builder.setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                    if (paused) {
                        reanudarEntrenamiento()
                    }
                    actualizarEstadoUI(false)
                }
                val dialog = builder.create()
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getColor(android.R.color.holo_green_dark))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }

        // Listener para el selector de deporte
        containerChangeSport.setOnClickListener { view ->
            badgeIndicator.visibility = View.GONE
            containerChangeSport.clearAnimation() // Detener animación al interactuar
            mostrarMenuCambioDeporte(view)
        }

        pedirPermisosUbicacion()
    }

    // Nueva función para el menú desplegable de deportes
    private fun mostrarMenuCambioDeporte(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_tipo_entrenamiento, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.option_fuerza -> {
                    if (running) {
                        Toast.makeText(this, "Finaliza el entrenamiento actual primero", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(this, RegistrarEntrenamientoFuerza::class.java)
                        startActivity(intent)
                        finish()
                    }
                    true
                }
                R.id.option_correr -> {
                    Toast.makeText(this, "Ya estás en modo Carrera", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // Función para actualizar el estado de los botones y el texto de la UI
    private fun actualizarEstadoUI(isAutoPaused: Boolean) {
        if (running) {
            btnIniciar.visibility = View.GONE
            btnPausar.visibility = View.VISIBLE
            btnFinalizar.visibility = View.VISIBLE

            // Deshabilitar cambio de deporte durante el entrenamiento para evitar errores
            containerChangeSport.isEnabled = false
            containerChangeSport.alpha = 0.5f

            if (paused) {
                btnPausar.text = "REANUDAR"
                btnPausar.setBackgroundColor(getColor(android.R.color.holo_green_dark))
                mostrarPausado(isAutoPaused)
            } else {
                btnPausar.text = "PAUSAR"
                btnPausar.setBackgroundColor(getColor(android.R.color.holo_orange_dark))
                ocultarPausado()
            }
        } else {
            btnIniciar.visibility = View.VISIBLE
            btnPausar.visibility = View.GONE
            btnFinalizar.visibility = View.GONE
            tvPausado.visibility = View.GONE

            // Rehabilitar selector al estar detenido
            containerChangeSport.isEnabled = true
            containerChangeSport.alpha = 1.0f
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Guarda el estado de la actividad para restaurarlo si el sistema la recrea
        outState.putBoolean("running", running)
        outState.putBoolean("paused", paused)
        if (recorrido != null) {
            outState.putSerializable("puntosRecorridoFinal", puntosRecorridoFinal as Serializable)
        }
        outState.putSerializable("ritmosNumericosFinal", ritmosNumericosFinal as Serializable)
        outState.putSerializable("ritmosNumericos01Final", ritmosNumericos01Final as Serializable)

        val ritmosList = ArrayList<String>()
        for (i in 0 until llRitmos.childCount) {
            val textView = llRitmos.getChildAt(i) as TextView
            ritmosList.add(textView.text.toString())
        }
        outState.putSerializable("listaRitmos", ritmosList as Serializable)
    }

    override fun onResume() {
        super.onResume()
        // Registra el BroadcastReceiver para recibir actualizaciones del servicio
        val filter = IntentFilter(EntrenamientoService.UPDATE_ACTION)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter)
        // Sincroniza el estado de la UI con el servicio en caso de que la app se haya cerrado
        sincronizarConServicio()
    }

    override fun onPause() {
        super.onPause()
        // Desregistra el BroadcastReceiver para evitar fugas de memoria
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    private fun sincronizarConServicio() {
        // Envía un Intent al servicio para obtener su estado actual
        val intent = Intent(this, EntrenamientoService::class.java).apply {
            action = EntrenamientoService.ACTION_GET_STATUS
        }
        startService(intent)
    }

    // Reproduce un sonido y vibra el dispositivo
    private fun reproducirSonidoYVibracion() {
        try {
            val mp = MediaPlayer.create(this, R.raw.whistle)
            mp.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }

    // Muestra el texto "PAUSADO" con efecto de parpadeo
    private fun mostrarPausado(isAutoPaused: Boolean) {
        tvPausado.text = if (isAutoPaused) "PAUSADO AUTOMÁTICAMENTE" else "PAUSADO"
        tvPausado.visibility = View.VISIBLE
        handlerParpadeo.post(parpadeoRunnable)
    }


    // Oculta el texto "PAUSADO" y detiene el parpadeo
    private fun ocultarPausado() {
        handlerParpadeo.removeCallbacks(parpadeoRunnable)
        tvPausado.visibility = View.GONE
    }

    // Inicia el servicio de entrenamiento en primer plano
    private fun iniciarEntrenamiento() {
        running = true
        paused = false
        val intent = Intent(this, EntrenamientoService::class.java)
        intent.action = EntrenamientoService.ACTION_START_FOREGROUND_SERVICE
        // Pide permiso de servicio en primer plano para Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startForegroundService(intent)
            } else {
                requestPermissions(arrayOf(Manifest.permission.FOREGROUND_SERVICE_LOCATION), 1001)
                Toast.makeText(this, "Permiso FGS location requerido", Toast.LENGTH_SHORT).show()
            }
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Entrenamiento iniciado", Toast.LENGTH_SHORT).show()
    }

    // Envía un Intent al servicio para pausar el entrenamiento
    private fun pausarEntrenamiento() {
        paused = true
        val intent = Intent(this, EntrenamientoService::class.java)
        intent.action = EntrenamientoService.ACTION_PAUSE_TRAINING
        startService(intent)
        Toast.makeText(this, "Entrenamiento pausado", Toast.LENGTH_SHORT).show()
    }

    // Envía un Intent al servicio para reanudar el entrenamiento
    private fun reanudarEntrenamiento() {
        paused = false
        val intent = Intent(this, EntrenamientoService::class.java)
        intent.action = EntrenamientoService.ACTION_RESUME_TRAINING
        startService(intent)
        Toast.makeText(this, "Entrenamiento reanudado", Toast.LENGTH_SHORT).show()
    }

    // Finaliza el entrenamiento, detiene el servicio y muestra los resultados
    private fun finalizarEntrenamiento() {
        val stopIntent = Intent(this, EntrenamientoService::class.java)
        stopIntent.action = EntrenamientoService.ACTION_STOP_FOREGROUND_SERVICE
        stopService(stopIntent)

        // Calcula el ritmo promedio si no se registraron ritmos por tramos
        if (ritmosNumericos01Final.isEmpty()) {
            val ritmoPromedioGeneral = if (distanciaFinal > 0) {
                (tiempoFinal.toDouble() / 1000.0) / (distanciaFinal / 1000.0)
            } else {
                0.0
            }
            ritmosNumericos01Final.add(ritmoPromedioGeneral)
        }

        // Calcula las calorías quemadas
        val calorias = calcularCalorias(distanciaFinal, tiempoFinal)

        // Inicia la actividad de resultados con los datos del entrenamiento
        val resultadosIntent = Intent(this, ResultadosEntrenamiento::class.java)
        resultadosIntent.putExtra("distancia", distanciaFinal)
        resultadosIntent.putExtra("desnivel", desnivelFinal)
        resultadosIntent.putExtra("tiempo", tiempoFinal)
        resultadosIntent.putExtra("calorias", calorias)
        resultadosIntent.putExtra("puntosRecorrido", puntosRecorridoFinal as Serializable)
        resultadosIntent.putExtra("ritmosNumericos", ritmosNumericos01Final as Serializable)

        startActivity(resultadosIntent)
        finish() // Cierra la actividad actual
    }

    // Función para calcular las calorías quemadas
    private fun calcularCalorias(distanciaKm: Double, tiempoMs: Long): Double {
        val pesoKg = 70.0
        val duracionHoras = tiempoMs.toDouble() / 3600000.0
        val ritmoMinKm = if (distanciaKm > 0) duracionHoras / (distanciaKm / 1000) else 0.0
        val met = when {
            ritmoMinKm <= 4.0 -> 8.0
            ritmoMinKm <= 5.0 -> 10.0
            ritmoMinKm <= 6.0 -> 11.5
            else -> 12.0
        }
        return met * pesoKg * duracionHoras
    }

    // Verifica si la aplicación tiene permiso para acceder a la ubicación
    private fun tienePermisos(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Pide al usuario el permiso de ubicación si no lo tiene
    private fun pedirPermisosUbicacion() {
        if (!tienePermisos()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }


    // Maneja el resultado de la solicitud de permisos
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, EntrenamientoService::class.java)
            intent.action = EntrenamientoService.ACTION_START_FOREGROUND_SERVICE
            startForegroundService(intent)
            Toast.makeText(this, "Permiso concedido, service iniciado", Toast.LENGTH_SHORT).show()
        }
    }
}