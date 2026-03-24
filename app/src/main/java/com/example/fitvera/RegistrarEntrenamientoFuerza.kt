package com.example.fitvera

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class RegistrarEntrenamientoFuerza : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var btnExit: ImageButton
    private lateinit var containerChangeSport: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pestanasentrenamientofuerza)

        // 1. Inicializar Vistas (IDs corregidos para coincidir con el XML)
        tabLayout = findViewById(R.id.tablayout) // Antes era tabLayout
        viewPager = findViewById(R.id.viewpager) // Antes era viewPager
        btnExit = findViewById(R.id.btn_exit)
        containerChangeSport = findViewById(R.id.container_change_sport)

        // 2. Configurar el Adaptador
        val adapter = FuerzaPagerAdapter(this)
        viewPager.adapter = adapter

        // 3. Unir TabLayout con ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "CREAR RUTINA" else "MIS RUTINAS"
        }.attach()

        // 4. Botón de Salida
        btnExit.setOnClickListener {
            // Asegúrate de que MainActivity2 existe en tu paquete
            val intent = Intent(this, MainActivity2::class.java)
            startActivity(intent)
            finish()
        }

        // 5. Selector de Deporte
        containerChangeSport.setOnClickListener { view ->
            mostrarMenuCambioDeporte(view)
        }
    }

    private fun mostrarMenuCambioDeporte(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_tipo_entrenamiento, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.option_correr -> {
                    val intent = Intent(this, RegistrarEntrenamiento::class.java)
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.option_fuerza -> {
                    Toast.makeText(this, "Ya estás en modo Fuerza", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}