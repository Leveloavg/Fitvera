package com.example.fitvera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.Serializable

class ImportarEntrenamientoZepp : AppCompatActivity() {

    // Credenciales de Strava
    private val CLIENT_ID = "194350"
    private val CLIENT_SECRET = "a7319baf20ef67a9b54cd764f08ff0d91e3bba7c"
    private val REDIRECT_URI = "fitvera://callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_importar_entrenamiento_hc)

        val uri = intent?.data
        if (uri != null && uri.toString().startsWith(REDIRECT_URI)) {
            handleIntent(intent)
        } else {
            iniciarFlujoStrava()
        }
    }

    private fun iniciarFlujoStrava() {
        try {
            val url = "https://www.strava.com/oauth/mobile/authorize?client_id=$CLIENT_ID" +
                    "&redirect_uri=$REDIRECT_URI&response_type=code&scope=read,profile:read_all,activity:read_all"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // Esto asegura que se abra el selector de navegador si hay dudas
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            errorAndFinish("Error al abrir el navegador: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        if (uri != null && uri.toString().startsWith(REDIRECT_URI)) {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                obtenerTokenYDatos(code)
            } else if (uri.getQueryParameter("error") != null) {
                errorAndFinish("Autorización cancelada")
            }
        }
    }

    private fun obtenerTokenYDatos(code: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Intercambio de Código por Access Token
                val formBody = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("code", code)
                    .add("grant_type", "authorization_code")
                    .build()

                val tokenRequest = Request.Builder().url("https://www.strava.com/oauth/token").post(formBody).build()
                val tokenResponse = client.newCall(tokenRequest).execute()
                val tokenJson = JSONObject(tokenResponse.body?.string() ?: "{}")

                if (!tokenJson.has("access_token")) {
                    withContext(Dispatchers.Main) { errorAndFinish("No se pudo obtener el acceso") }
                    return@launch
                }
                val accessToken = tokenJson.getString("access_token")

                // 2. Obtener la última actividad
                val activitiesRequest = Request.Builder()
                    .url("https://www.strava.com/api/v3/athlete/activities?per_page=30")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                val actResponse = client.newCall(activitiesRequest).execute()
                val activitiesArray = JSONArray(actResponse.body?.string() ?: "[]")

                if (activitiesArray.length() > 0) {
                    val activity = activitiesArray.getJSONObject(0)
                    val activityId = activity.getLong("id")

                    // 3. Obtener STREAMS para la gráfica de ritmo real
                    val streamsRequest = Request.Builder()
                        .url("https://www.strava.com/api/v3/activities/$activityId/streams?keys=distance,time&key_by_type=true")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()

                    val streamsResponse = client.newCall(streamsRequest).execute()
                    val streamsJson = JSONObject(streamsResponse.body?.string() ?: "{}")

                    procesarActividadStrava(activity, streamsJson)
                } else {
                    withContext(Dispatchers.Main) { errorAndFinish("No hay actividades para importar") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { errorAndFinish("Error de red: ${e.message}") }
            }
        }
    }

    private suspend fun procesarActividadStrava(activity: JSONObject, streams: JSONObject) {
        val distanciaTotalMetros = activity.optDouble("distance", 0.0)
        val tiempoTotalSegundos = activity.optLong("moving_time", 0L)
        val desnivel = activity.optDouble("total_elevation_gain", 0.0)

        // --- LÓGICA DE CALORÍAS ---
        // Intentamos obtener las calorías de Strava
        var kcal = activity.optDouble("calories", 0.0)

        // Si Strava devuelve 0 o no tiene el campo, calculamos una estimación
        if (kcal <= 0.0 && distanciaTotalMetros > 0) {
            val pesoUsuario = 75.0 // Valor por defecto. Puedes obtenerlo de SharedPreferences si lo tienes.
            val distanciaKm = distanciaTotalMetros / 1000.0

            // Fórmula estándar: Peso (kg) * Distancia (km) * 1.036
            kcal = pesoUsuario * distanciaKm * 1.036

            // Ajuste leve por desnivel positivo (opcional)
            if (desnivel > 0) {
                kcal += (desnivel * 0.1)
            }
        }

        // 1. Decodificar Mapa
        val rutaPuntos = ArrayList<GeoPoint>()
        val mapObj = activity.optJSONObject("map")
        val summaryPolyline = mapObj?.optString("summary_polyline", "") ?: ""

        if (summaryPolyline.isNotEmpty()) {
            try {
                val decoded = PolyUtil.decode(summaryPolyline)
                for (latLng in decoded) {
                    rutaPuntos.add(GeoPoint(latLng.latitude, latLng.longitude))
                }
            } catch (e: Exception) {
                Log.e("STRAVA_DEBUG", "Error decodificando mapa")
            }
        }

        // 2. Calcular ritmos reales cada 100 metros
        val ritmos = ArrayList<Double>()
        if (streams.has("distance") && streams.has("time")) {
            val distData = streams.getJSONObject("distance").getJSONArray("data")
            val timeData = streams.getJSONObject("time").getJSONArray("data")

            var hitoMetros = 100.0
            var lastD = 0.0
            var lastT = 0

            for (i in 0 until distData.length()) {
                val currentD = distData.getDouble(i)
                val currentT = timeData.getInt(i)

                if (currentD >= hitoMetros) {
                    val dD = currentD - lastD
                    val dT = currentT - lastT

                    if (dD > 0) {
                        val ritmoTramo = dT / (dD / 1000.0)
                        ritmos.add(ritmoTramo)
                    }
                    lastD = currentD
                    lastT = currentT
                    hitoMetros += 100.0
                }
            }
        }

        if (ritmos.isEmpty()) {
            val promedio = if (distanciaTotalMetros > 0) tiempoTotalSegundos / (distanciaTotalMetros / 1000.0) else 0.0
            val numPuntos = (distanciaTotalMetros / 100).toInt().coerceAtLeast(1)
            for (i in 1..numPuntos) ritmos.add(promedio)
        }

        // 3. Enviar a Resultados
        withContext(Dispatchers.Main) {
            val intent = Intent(this@ImportarEntrenamientoZepp, ResultadosEntrenamiento::class.java).apply {
                putExtra("distancia", distanciaTotalMetros)
                putExtra("tiempo", tiempoTotalSegundos * 1000)
                putExtra("calorias", kcal)
                putExtra("desnivel", desnivel)
                putExtra("puntosRecorrido", rutaPuntos as Serializable)
                putExtra("ritmosNumericos", ritmos as Serializable)
                putExtra("esImportado", true)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun errorAndFinish(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
