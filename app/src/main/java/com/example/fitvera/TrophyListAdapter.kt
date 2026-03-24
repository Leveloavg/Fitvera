package com.example.fitvera

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
// Asegúrate de que esta línea coincida con el package de tu AndroidManifest
import com.example.fitvera.R

class TrophyListAdapter(
    private var trophyList: List<DesafiosActivity.Trophy>,
    private val imageResourceResolver: (String) -> Int
) : RecyclerView.Adapter<TrophyListAdapter.TrophyViewHolder>() {

    class TrophyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardRoot: MaterialCardView = itemView.findViewById(R.id.card_trophy_root)
        val imageView: ImageView = itemView.findViewById(R.id.trophy_image_view_list)
        val titleView: TextView = itemView.findViewById(R.id.trophy_title_list)
        val descriptionView: TextView = itemView.findViewById(R.id.trophy_description_list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrophyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.trophy_card_list_item, parent, false)
        return TrophyViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrophyViewHolder, position: Int) {
        val trophy = trophyList[position]
        val context = holder.itemView.context
        val type = trophy.type.lowercase()

        // 1. Asignación de imagen según el tipo
        val imageResId = when (type) {
            "oro_top1" -> R.drawable.trofeoprimero
            "plata_top2" -> R.drawable.trofeoplata
            "bronce_top3" -> R.drawable.trofeotercero
            "oro_top10" -> R.drawable.medallaoro
            "plata_top50" -> R.drawable.medallaplata
            "bronce_top100" -> R.drawable.medallabronze
            else -> imageResourceResolver(trophy.type)
        }
        holder.imageView.setImageResource(imageResId)

        // 2. Limpieza de Tint para iconos de ranking (para ver el color original)
        if (type.contains("top") || type.contains("oro") || type.contains("plata") || type.contains("bronce")) {
            holder.imageView.imageTintList = null
        } else {
            // Tint para desafíos normales usando el colorPrimary del sistema
            val tv = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)) {
                holder.imageView.imageTintList = ColorStateList.valueOf(tv.data)
            }
        }

        // 3. Estilo de Borde (Stroke) dinámico
        val sColor = when {
            type.contains("oro") -> ContextCompat.getColor(context, R.color.gold_color)
            type.contains("plata") -> ContextCompat.getColor(context, R.color.silver_color)
            type.contains("bronce") -> ContextCompat.getColor(context, R.color.bronze_color)
            else -> Color.TRANSPARENT
        }

        val sWidth = when {
            type.contains("oro") -> 6
            type.contains("plata") -> 4
            type.contains("bronce") -> 3
            else -> 0
        }

        holder.cardRoot.strokeColor = sColor
        holder.cardRoot.strokeWidth = sWidth

        holder.titleView.text = trophy.title
        holder.descriptionView.text = trophy.description
    }

    override fun getItemCount(): Int = trophyList.size

    fun updateTrophies(newTrophyList: List<DesafiosActivity.Trophy>) {
        this.trophyList = newTrophyList
        notifyDataSetChanged()
    }
}