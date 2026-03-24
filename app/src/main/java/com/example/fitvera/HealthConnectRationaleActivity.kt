package com.example.fitvera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class HealthConnectRationaleActivity : AppCompatActivity() {

    companion object {
        const val RESULT_GOOGLE_FIT = 1
        const val RESULT_SAMSUNG_HEALTH = 2
        const val RESULT_MANUAL = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_connect_rationale)

        val btnBack: ImageButton = findViewById(R.id.btn_back)
        val btnGoogleFit: MaterialButton = findViewById(R.id.btn_google_fit)
        val btnSamsungHealth: MaterialButton = findViewById(R.id.btn_samsung_health)
        val tvPrivacy: TextView = findViewById(R.id.privacy_policy_link)

        // Acción para el botón de volver atrás
        btnBack.setOnClickListener {
            onBackPressed() // O simplemente finish()
        }

        btnGoogleFit.setOnClickListener {
            setResult(RESULT_GOOGLE_FIT)
            finish()
        }

        btnSamsungHealth.setOnClickListener {
            setResult(RESULT_SAMSUNG_HEALTH)
            finish()
        }

        tvPrivacy.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tu-web.com/politica-privacidad"))
            startActivity(browserIntent)
        }
    }
}
