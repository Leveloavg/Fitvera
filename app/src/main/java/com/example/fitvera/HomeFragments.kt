package com.example.fitvera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class HomeFragments : Fragment() {

    private lateinit var listener: HomeFragmentListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is HomeFragmentListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement HomeFragmentListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        view.findViewById<Button>(R.id.btn_get_started)?.setOnClickListener {
            val intent = Intent(requireContext(), LeagueActivity::class.java)
            startActivity(intent)
        }

        // Este botón abre el selector (HealthConnectRationaleActivity)
        view.findViewById<LinearLayout>(R.id.btn_registrar)?.setOnClickListener {
            listener.abrirRegistrar()
        }

        view.findViewById<LinearLayout>(R.id.btn_verentrenamientos)?.setOnClickListener {
            listener.abrirVerEntrenamientos()
        }

        view.findViewById<LinearLayout>(R.id.btnplanificar)?.setOnClickListener {
            listener.abrirPlanificar()
        }

        view.findViewById<LinearLayout>(R.id.btn_logros)?.setOnClickListener {
            listener.abrirLogros()
        }

        view.findViewById<LinearLayout>(R.id.btn_verranking)?.setOnClickListener {
            listener.abrirRanking()
        }

        view.findViewById<LinearLayout>(R.id.btn_verterritorio)?.setOnClickListener {
            listener.abrirTerritorios()
        }

        view.findViewById<LinearLayout>(R.id.btn_cerrarsesion)?.setOnClickListener {
            listener.CerrarSesion()
        }
    }
}