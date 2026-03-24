package com.example.fitvera

import Rutina
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RutinaAdapter(
    private var rutinas: List<Rutina>,
    private val onEdit: (Rutina) -> Unit,
    private val onDelete: (Rutina) -> Unit,
    private val onConfirm: (Rutina) -> Unit
) : RecyclerView.Adapter<RutinaAdapter.RutinaViewHolder>() {

    class RutinaViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvNombre: TextView = v.findViewById(R.id.tvNombreRutinaCard)
        val tvInfo: TextView = v.findViewById(R.id.tvInfoRutinaCard)
        val btnEdit: Button = v.findViewById(R.id.btnEditarRutina)
        val btnDelete: Button = v.findViewById(R.id.btnBorrarRutina)
        val btnConfirm: Button = v.findViewById(R.id.btnConfirmarRutina)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RutinaViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_rutina_lista, parent, false)
        return RutinaViewHolder(v)
    }

    override fun onBindViewHolder(holder: RutinaViewHolder, position: Int) {
        val rutina = rutinas[position]
        holder.tvNombre.text = rutina.nombreRutina
        holder.tvInfo.text = "Duración: ${rutina.tiempoMin} min | ${rutina.dificultad}"

        holder.btnEdit.setOnClickListener { onEdit(rutina) }
        holder.btnDelete.setOnClickListener { onDelete(rutina) }
        holder.btnConfirm.setOnClickListener { onConfirm(rutina) }
    }

    override fun getItemCount() = rutinas.size

    fun updateList(newList: List<Rutina>) {
        rutinas = newList
        notifyDataSetChanged()
    }
}