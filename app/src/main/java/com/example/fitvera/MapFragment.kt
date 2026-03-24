package com.example.fitvera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Fragmento encargado de renderizar un mapa con el recorrido de una actividad deportiva.
 * Utiliza OSMDroid para mostrar mapas gratuitos y de código abierto.
 */
class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    // Lista de puntos que recibiremos del entrenamiento (Latitud, Longitud)
    private var puntosRecorrido: ArrayList<HashMap<String, Any>> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recuperamos los puntos pasados a través de los argumentos del Fragment
        arguments?.let {
            @Suppress("UNCHECKED_CAST")
            puntosRecorrido = it.getSerializable("puntos") as? ArrayList<HashMap<String, Any>> ?: arrayListOf()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inicialización dinámica de la vista del mapa
        mapView = MapView(requireContext())

        // Cargar la configuración de OSM (necesario para la caché y el agente de usuario)
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osm_prefs", 0)
        )

        // Configuración básica: Fuente de los mapas y controles táctiles (zoom, desplazamiento)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Si hay datos de recorrido, procedemos a dibujar
        if (puntosRecorrido.isNotEmpty()) {

            // 1. CONVERSIÓN DE DATOS: Pasamos los HashMap a objetos GeoPoint compatibles con el mapa
            val geoPoints = puntosRecorrido.mapNotNull {
                // Intentamos leer tanto "lat/lng" como "latitude/longitude" por compatibilidad
                val lat = it["lat"]?.toString()?.toDoubleOrNull()
                    ?: it["latitude"]?.toString()?.toDoubleOrNull()
                val lng = it["lng"]?.toString()?.toDoubleOrNull()
                    ?: it["longitude"]?.toString()?.toDoubleOrNull()

                if (lat != null && lng != null) GeoPoint(lat, lng) else null
            }

            if (geoPoints.isNotEmpty()) {
                // 2. DIBUJAR LA LÍNEA (POLYLINE): Representa el camino seguido
                val polyline = Polyline()
                polyline.setPoints(geoPoints)
                polyline.outlinePaint.color = 0xFFFF5722.toInt() // Color naranja corporativo
                polyline.outlinePaint.strokeWidth = 8f           // Grosor de la línea
                mapView.overlays.add(polyline)

                // 3. MARCADOR DE INICIO: Pin donde comenzó la actividad
                val startMarker = Marker(mapView)
                startMarker.position = geoPoints.first()
                startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                startMarker.title = "Inicio"
                mapView.overlays.add(startMarker)

                // 4. MARCADOR DE FIN: Pin donde terminó la actividad
                val endMarker = Marker(mapView)
                endMarker.position = geoPoints.last()
                endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                endMarker.title = "Fin"
                mapView.overlays.add(endMarker)

                // 5. AJUSTE DE CÁMARA: Calculamos un cuadro delimitador (BoundingBox)
                // Esto asegura que todo el recorrido sea visible al abrir la pantalla
                val bbox = BoundingBox.fromGeoPointsSafe(geoPoints)
                val controller = mapView.controller
                controller.setCenter(bbox.center)

                // 6. ZOOM ADAPTATIVO: Lógica para que el zoom no sea estático
                // Se calcula la diferencia entre los puntos más lejanos del recorrido
                val latDiff = bbox.latNorth - bbox.latSouth
                val lonDiff = bbox.lonEast - bbox.lonWest
                val maxDiff = maxOf(latDiff, lonDiff)

                // Elegimos un nivel de zoom según qué tan "largo" es el recorrido (km o metros)
                var zoom = when {
                    maxDiff < 0.005 -> 18.0 // Muy corto (ej. caminar por un parque)
                    maxDiff < 0.01 -> 17.0
                    maxDiff < 0.05 -> 15.0  // Medio (ej. trote de 5km)
                    maxDiff < 0.1 -> 14.0
                    else -> 12.0            // Largo (ej. ruta en coche o bici larga)
                }

                // Ajuste extra de zoom para recorridos extremadamente pequeños
                if (maxDiff < 0.002) zoom += 1.0

                controller.setZoom(zoom)
            }
        }

        return mapView
    }

    // --- MÉTODOS DE CICLO DE VIDA ---
    // Son obligatorios para que el mapa gestione correctamente la memoria y el renderizado

    override fun onResume() {
        super.onResume()
        mapView.onResume() // Reanuda el renderizado del mapa
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause() // Pausa para ahorrar batería
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDetach() // Limpia los recursos del mapa para evitar fugas de memoria
    }

    companion object {
        /**
         * Método estático para crear una instancia del fragmento pasando los puntos.
         * Facilita la creación del Fragment desde una Activity.
         */
        fun newInstance(puntos: ArrayList<HashMap<String, Any>>): MapFragment {
            val fragment = MapFragment()
            val args = Bundle()
            args.putSerializable("puntos", puntos) // Empaquetamos los datos
            fragment.arguments = args
            return fragment
        }
    }
}