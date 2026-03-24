package com.example.fitvera


import Ejercicio
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView

class EjercicioAdapter(private val lista: MutableList<Ejercicio>) :
    RecyclerView.Adapter<EjercicioAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val etNombre: EditText = view.findViewById(R.id.etNombreEx)
        val etSeries: EditText = view.findViewById(R.id.etSeries)
        val etReps: EditText = view.findViewById(R.id.etReps)
        val etPeso: EditText = view.findViewById(R.id.etPeso)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteEjercicio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ejercicio_card, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ejercicio = lista[position]

        // Sincronizar datos de la lista a la UI sin disparar los listeners
        holder.etNombre.setText(ejercicio.nombre)
        holder.etSeries.setText(if(ejercicio.series > 0) ejercicio.series.toString() else "")
        holder.etReps.setText(if(ejercicio.reps > 0) ejercicio.reps.toString() else "")
        holder.etPeso.setText(if(ejercicio.peso > 0.0) ejercicio.peso.toString() else "")

        // Listener para Nombre
        holder.etNombre.addTextChangedListener {
            lista[holder.adapterPosition] = lista[holder.adapterPosition].copy(nombre = it.toString())
        }

        // Listener para Series
        holder.etSeries.addTextChangedListener {
            val valor = it.toString().toIntOrNull() ?: 0
            lista[holder.adapterPosition] = lista[holder.adapterPosition].copy(series = valor)
        }

        // Listener para Repeticiones
        holder.etReps.addTextChangedListener {
            val valor = it.toString().toIntOrNull() ?: 0
            lista[holder.adapterPosition] = lista[holder.adapterPosition].copy(reps = valor)
        }

        // Listener para Peso
        holder.etPeso.addTextChangedListener {
            val valor = it.toString().toDoubleOrNull() ?: 0.0
            lista[holder.adapterPosition] = lista[holder.adapterPosition].copy(peso = valor)
        }

        // Botón para eliminar este ejercicio específico
        holder.btnDelete.setOnClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                lista.removeAt(currentPos)
                notifyItemRemoved(currentPos)
                notifyItemRangeChanged(currentPos, lista.size)
            }
        }
    }

    override fun getItemCount() = lista.size
}