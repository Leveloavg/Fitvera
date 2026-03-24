package com.example.fitvera

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val tasks: List<Task>,
    private val onEditClick: (Task) -> Unit,
    private val onDeleteClick: (Task) -> Unit
    // 🗑️ ELIMINADO: private val onCompleteClick: (Task, Boolean) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textViewTaskTitle)
        val description: TextView = itemView.findViewById(R.id.textViewTaskDescription)
        val time: TextView = itemView.findViewById(R.id.textViewTaskTime)
        val btnEdit: ImageButton = itemView.findViewById(R.id.buttonEdit)
        val btnDelete: ImageButton = itemView.findViewById(R.id.buttonDelete)
        // 🗑️ ELIMINADO: val checkbox: CheckBox = itemView.findViewById(R.id.checkboxTask)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.title.text = task.title
        holder.description.text = task.description

        // Manejar visualización de la hora
        if (task.time.isNotEmpty()) {
            holder.time.text = task.time
            holder.time.visibility = View.VISIBLE
        } else {
            holder.time.text = "--:--"
            holder.time.visibility = View.VISIBLE
        }

        // 🚀 RESTAURADO: El texto no se tacha.
        holder.title.paintFlags = holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        holder.description.paintFlags = holder.description.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        holder.title.alpha = 1.0f
        holder.description.alpha = 1.0f

        holder.btnEdit.setOnClickListener { onEditClick(task) }
        holder.btnDelete.setOnClickListener { onDeleteClick(task) }

        // 🗑️ ELIMINADO: Lógica del Checkbox
    }

    override fun getItemCount() = tasks.size
}