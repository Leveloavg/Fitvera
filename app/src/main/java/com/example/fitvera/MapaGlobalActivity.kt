package com.example.fitvera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.IOException
import java.net.URLEncoder
import java.util.*

/**
 * Clase personalizada para dibujar el nombre del propietario sobre una zona conquistada.
 * Se hereda de Overlay para poder pintar directamente sobre el Canvas del mapa.
 */
class OwnerTextOverlay(
    private val geoPoint: GeoPoint,
    private val text: String,
    private val parentMapView: MapView
) : Overlay() {
    private val MIN_ZOOM_LEVEL = 17.0 // El nombre solo es visible si hay mucho zoom
    private val textPaint: Paint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(5f, 0f, 0f, Color.BLACK) // Sombra para leer mejor sobre el mapa
    }

    override fun draw(canvas: Canvas?, projection: Projection?) {
        if (canvas == null || projection == null) return
        // No dibujamos si el zoom es muy alejado para no saturar la pantalla
        if (parentMapView.zoomLevelDouble < MIN_ZOOM_LEVEL) return

        // Convertimos las coordenadas geográficas a puntos de píxeles en pantalla
        val screenPoint = projection.toPixels(geoPoint, null)
        canvas.drawText(text, screenPoint.x.toFloat(), screenPoint.y.toFloat() - 10f, textPaint)
    }
}

class MapaGlobalActivity : AppCompatActivity() {

    // Componentes de la interfaz
    private lateinit var mapaGlobalView: MapView
    private lateinit var fabCenterLocation: FloatingActionButton
    private lateinit var fabShowStats: FloatingActionButton
    private lateinit var tvDominioInfo: TextView
    private lateinit var searchInput: EditText
    private lateinit var suggestionsRecyclerView: RecyclerView
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var btnBack: ImageView

    // Servicios: Firebase para datos y OkHttp para búsquedas de direcciones
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val httpClient = OkHttpClient()

    // Cache y Corrutinas para optimizar las peticiones de red y búsqueda
    private val userCache = mutableMapOf<String, String>()
    private val searchJob = Job()
    private val searchScope = CoroutineScope(Dispatchers.Main + searchJob)

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Carga la configuración necesaria para OSMDroid (Mapas)
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContentView(R.layout.activity_mapa_global)

        // Inicialización de vistas
        mapaGlobalView = findViewById(R.id.mapa_global_view)
        fabCenterLocation = findViewById(R.id.fab_center_location)
        fabShowStats = findViewById(R.id.fab_show_stats)
        tvDominioInfo = findViewById(R.id.tv_dominio_info)
        searchInput = findViewById(R.id.search_input)
        suggestionsRecyclerView = findViewById(R.id.suggestions_recycler_view)
        btnBack = findViewById(R.id.btn_back)

        setupMap() // Configura el estilo y controles del mapa
        setupSearch() // Configura la barra de búsqueda y el teclado
        setupSuggestionsRecyclerView() // Prepara la lista de sugerencias de autocompletado
        checkLocationPermission() // Verifica permisos de GPS

        // Listeners para botones flotantes y retroceso
        fabCenterLocation.setOnClickListener { centerMapOnUser() }
        fabShowStats.setOnClickListener { showTerritoryRanking() }
        btnBack.setOnClickListener { finish() }

        updateUserDominio() // Actualiza cuántas zonas posee el usuario actual
    }

    /**
     * Consulta en Firebase las zonas del usuario logueado y su posición en el ranking mundial.
     */
    private fun updateUserDominio() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("zonasGlobales")
            .whereEqualTo("propietarioUID", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                val count = snapshots.size()
                db.collection("ranking_users")
                    .orderBy("territories_total", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { ranking ->
                        var pos = 1
                        var found = false
                        for (doc in ranking.documents) {
                            if (doc.id == uid) {
                                found = true
                                break
                            }
                            pos++
                        }

                        // Muestra: "Tienes X zonas - Rank #Y" o solo las zonas si no está en ranking
                        tvDominioInfo.text = if (found) {
                            getString(R.string.map_my_domain_format, count, pos)
                        } else {
                            getString(R.string.map_my_domain_no_ranking, count)
                        }
                    }
            }
    }

    /**
     * Obtiene los 5 usuarios con más zonas del mundo y los muestra en un diálogo.
     */
    private fun showTerritoryRanking() {
        db.collection("ranking_users")
            .orderBy("territories_total", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { result ->
                val sb = StringBuilder()
                result.forEachIndexed { index, doc ->
                    val nombre = doc.getString("name") ?: doc.getString("nombre") ?: getString(R.string.map_anonymous)
                    val zonas = doc.getLong("territories_total") ?: 0
                    sb.append(getString(R.string.map_ranking_row_format, index + 1, nombre, zonas))
                }

                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.map_ranking_title))
                    .setMessage(if (sb.isEmpty()) getString(R.string.map_ranking_empty) else sb.toString())
                    .setPositiveButton(getString(R.string.btn_close), null)
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.map_ranking_error), Toast.LENGTH_SHORT).show()
            }
    }

    // --- LÓGICA DE BÚSQUEDA ---

    private fun setupSuggestionsRecyclerView() {
        suggestionAdapter = SuggestionAdapter(emptyList()) { selectedSuggestion ->
            searchInput.setText(selectedSuggestion)
            geocodeLocation(selectedSuggestion) // Convierte dirección a coordenadas
            suggestionsRecyclerView.visibility = View.GONE
            hideKeyboard()
        }
        suggestionsRecyclerView.layoutManager = LinearLayoutManager(this)
        suggestionsRecyclerView.adapter = suggestionAdapter
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun setupSearch() {
        searchInput.hint = getString(R.string.map_search_hint)

        // Ejecuta la búsqueda cuando se pulsa "Intro/Lupa" en el teclado
        searchInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchJob.cancelChildren()
                suggestionsRecyclerView.visibility = View.GONE
                hideKeyboard()
                val query = searchInput.text.toString()
                if (query.isNotBlank()) geocodeLocation(query)
                return@setOnEditorActionListener true
            }
            false
        }

        // Detecta cambios en el texto para sugerir lugares en tiempo real
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length > 2) {
                    searchJob.cancelChildren()
                    searchScope.launch {
                        delay(300) // Espera 300ms para no saturar la red mientras se escribe
                        fetchSuggestions(query)
                    }
                } else {
                    suggestionsRecyclerView.visibility = View.GONE
                }
            }
        })
    }

    /**
     * Llama a la API Nominatim (OpenStreetMap) para obtener nombres de lugares que coincidan con la búsqueda.
     */
    private fun fetchSuggestions(query: String) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&addressdetails=1"
        val request = Request.Builder().url(url).header("User-Agent", "FitVeraAndroidApp/1.0").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonString ->
                    try {
                        val jsonArray = JSONArray(jsonString)
                        val suggestions = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            suggestions.add(jsonArray.getJSONObject(i).getString("display_name"))
                        }
                        runOnUiThread {
                            if (suggestions.isNotEmpty()) {
                                suggestionAdapter.updateSuggestions(suggestions)
                                suggestionsRecyclerView.visibility = View.VISIBLE
                            } else {
                                suggestionsRecyclerView.visibility = View.GONE
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { suggestionsRecyclerView.visibility = View.GONE }
                    }
                }
            }
        })
    }

    /**
     * Convierte un nombre de lugar en Latitud y Longitud para mover la cámara del mapa.
     */
    private fun geocodeLocation(query: String) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"
        val request = Request.Builder().url(url).header("User-Agent", "FitVeraAndroidApp/1.0").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonString ->
                    try {
                        val jsonArray = JSONArray(jsonString)
                        if (jsonArray.length() > 0) {
                            val result = jsonArray.getJSONObject(0)
                            val lat = result.getString("lat").toDouble()
                            val lon = result.getString("lon").toDouble()
                            runOnUiThread {
                                mapaGlobalView.controller.animateTo(GeoPoint(lat, lon))
                                mapaGlobalView.controller.setZoom(13.0)
                                mapaGlobalView.invalidate()
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        })
    }

    // --- LÓGICA DE MAPA ---

    private fun setupMap() {
        mapaGlobalView.setTileSource(TileSourceFactory.MAPNIK)
        mapaGlobalView.setMultiTouchControls(true)
        mapaGlobalView.controller.setZoom(10.0)

        // Añade brújula visual al mapa
        val compassOverlay = CompassOverlay(this, mapaGlobalView)
        compassOverlay.enableCompass()
        mapaGlobalView.overlays.add(compassOverlay)

        loadConqueredZones() // Carga los polígonos de las zonas conquistadas
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            initializeLocationOverlay()
        }
    }

    /**
     * Activa el punto azul que indica la posición actual del usuario en el mapa.
     */
    private fun initializeLocationOverlay() {
        val locationProvider = GpsMyLocationProvider(this)
        val myLocationOverlay = MyLocationNewOverlay(locationProvider, mapaGlobalView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.runOnFirstFix {
            runOnUiThread { centerMapOnUser() }
        }
        mapaGlobalView.overlays.add(myLocationOverlay)
    }

    private fun centerMapOnUser() {
        val locationOverlay = mapaGlobalView.overlays.find { it is MyLocationNewOverlay } as? MyLocationNewOverlay
        locationOverlay?.myLocation?.let {
            mapaGlobalView.controller.animateTo(it)
            mapaGlobalView.controller.setZoom(15.0)
        }
    }

    /**
     * Limpia y recarga las zonas conquistadas desde Firebase.
     */
    private fun loadConqueredZones() {
        // Mantener solo las capas de sistema (GPS, Brújula, Etiquetas)
        val overlaysToKeep = mapaGlobalView.overlays.filter {
            it is MyLocationNewOverlay || it is CompassOverlay || it is OwnerTextOverlay
        }
        mapaGlobalView.overlays.clear()
        mapaGlobalView.overlays.addAll(overlaysToKeep)

        // Trae las zonas de la base de datos (limitado a 200 para evitar lag)
        db.collection("zonasGlobales")
            .limit(200)
            .get()
            .addOnSuccessListener { result ->
                result.forEach { document ->
                    val zoneKey = document.id // ID con formato ZONE_LAT_LONG
                    val propietarioUID = document.getString("propietarioUID")
                    if (propietarioUID != null && zoneKey.startsWith("ZONE_")) {
                        val zoneColor = getColorForUser(propietarioUID)
                        fetchUserNameAndDraw(zoneKey, propietarioUID, zoneColor)
                    }
                }
                mapaGlobalView.invalidate()
            }
    }

    /**
     * Obtiene el nombre del dueño y dibuja el polígono de la zona.
     */
    private fun fetchUserNameAndDraw(zoneKey: String, uid: String, color: Int) {
        val userName = userCache[uid]
        if (userName != null) {
            val centerPoint = drawMicroZone(zoneKey, color)
            centerPoint?.let { addOwnerLabel(it, userName) }
        } else {
            db.collection("usuarios").document(uid).get()
                .addOnSuccessListener { userDoc ->
                    val name = userDoc.getString("nombre") ?: getString(R.string.map_user_default)
                    userCache[uid] = name
                    val centerPoint = drawMicroZone(zoneKey, color)
                    centerPoint?.let { addOwnerLabel(it, name) }
                }
        }
    }

    /**
     * Descodifica la clave de la zona (ZONE_LAT_LONG) y dibuja un cuadrado en esas coordenadas.
     */
    private fun drawMicroZone(zoneKey: String, color: Int): GeoPoint? {
        val parts = zoneKey.split("_")
        if (parts.size < 5) return null

        // Convertimos la nomenclatura "NEG" de vuelta a signos negativos y armamos el Double
        val latStr = (parts[1] + "." + parts[2]).replace("NEG", "-")
        val lonStr = (parts[3] + "." + parts[4]).replace("NEG", "-")

        return try {
            val baseLat = latStr.toDouble()
            val baseLon = lonStr.toDouble()
            val precision = 0.001 // Tamaño del lado del cuadrado (territorio)

            // Definimos los 4 puntos del cuadrado
            val points = arrayListOf(
                GeoPoint(baseLat, baseLon),
                GeoPoint(baseLat + precision, baseLon),
                GeoPoint(baseLat + precision, baseLon + precision),
                GeoPoint(baseLat, baseLon + precision),
                GeoPoint(baseLat, baseLon)
            )

            val polygon = Polygon(mapaGlobalView)
            polygon.points = points
            // Relleno semi-transparente y borde sólido
            polygon.fillColor = Color.argb(100, Color.red(color), Color.green(color), Color.blue(color))
            polygon.strokeColor = color
            polygon.strokeWidth = 2f
            mapaGlobalView.overlays.add(polygon)

            // Retornamos el centro para poner el texto del nombre
            GeoPoint(baseLat + precision / 2, baseLon + precision / 2)
        } catch (e: Exception) { null }
    }

    /**
     * Añade la capa de texto sobre el punto central de una zona.
     */
    private fun addOwnerLabel(position: GeoPoint, ownerName: String) {
        val textOverlay = OwnerTextOverlay(position, ownerName, mapaGlobalView)
        mapaGlobalView.overlays.add(textOverlay)
    }

    /**
     * Genera un color consistente basado en el ID único del usuario.
     */
    private fun getColorForUser(uid: String): Int {
        val colors = arrayOf(Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA)
        return colors[kotlin.math.abs(uid.hashCode()) % colors.size]
    }

    // --- CICLO DE VIDA ---

    override fun onResume() {
        super.onResume()
        mapaGlobalView.onResume()
        loadConqueredZones()
        updateUserDominio()
    }

    override fun onPause() {
        super.onPause()
        mapaGlobalView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob.cancel() // Cancela cualquier búsqueda en curso al salir
    }
}