package com.example.fitvera

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Modelo de datos representativo de un usuario dentro de la liga.
 */
data class LeagueUser(
    val userId: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val puntuacion: Int = 0,
    val activities: Int = 0
)

/**
 * Adaptador que gestiona la visualización de la tabla de clasificación (Leaderboard).
 */
class LeagueAdapter(private val users: List<LeagueUser>) :
    RecyclerView.Adapter<LeagueAdapter.LeagueViewHolder>() {

    /**
     * ViewHolder: Almacena las referencias a las vistas de cada fila para optimizar el scroll.
     */
    class LeagueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tv_rank)
        val tvUsername: TextView = view.findViewById(R.id.tv_username)
        val tvPoints: TextView = view.findViewById(R.id.tv_points)
        val ivAvatar: ImageView = view.findViewById(R.id.iv_user_avatar)
        val viewStatus: View = view.findViewById(R.id.view_status_indicator)
        val rewardLayout: LinearLayout = view.findViewById(R.id.reward_layout)
        val ivReward: ImageView = view.findViewById(R.id.iv_reward_icon)
        val tvRewardValue: TextView = view.findViewById(R.id.tv_reward_value)
    }

    /**
     * Crea la vista física (infla el XML) para cada fila de la liga.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeagueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_league_user, parent, false)
        return LeagueViewHolder(view)
    }

    /**
     * Une los datos del usuario con los elementos visuales de la fila actual.
     */
    override fun onBindViewHolder(holder: LeagueViewHolder, position: Int) {
        val user = users[position]
        val rank = position + 1 // La posición 0 es el Rank 1

        // Asignación de textos básicos
        holder.tvRank.text = rank.toString()
        holder.tvUsername.text = user.name
        holder.tvPoints.text = "${user.puntuacion} pts"

        // -------------------------------------------------------------------------
        // 1. INDICADORES DE ASCENSO/DESCENSO (Lógica Visual)
        // -------------------------------------------------------------------------
        when {
            position < 3 -> {
                // Zona de Ascenso (Top 3): Se muestra un indicador Verde
                holder.viewStatus.visibility = View.VISIBLE
                holder.viewStatus.setBackgroundColor(Color.parseColor("#4CAF50"))
            }
            position >= 17 -> {
                // Zona de Descenso (Últimos del ranking): Se muestra un indicador Rojo
                holder.viewStatus.visibility = View.VISIBLE
                holder.viewStatus.setBackgroundColor(Color.parseColor("#F44336"))
            }
            else -> {
                // Zona Neutra: El indicador se oculta
                holder.viewStatus.visibility = View.INVISIBLE
            }
        }

        // -------------------------------------------------------------------------
        // 2. LÓGICA DE RECOMPENSAS (Gamificación)
        // -------------------------------------------------------------------------
        when (rank) {
            1 -> {
                // Primer lugar: Icono de trofeo y máxima recompensa
                holder.ivReward.setImageResource(R.drawable.ic_trophy)
                holder.tvRewardValue.text = "+100"
                holder.rewardLayout.visibility = View.VISIBLE
            }
            in 2..7 -> {
                // Del 2º al 7º lugar: Icono de moneda con valores decrecientes
                holder.ivReward.setImageResource(R.drawable.ic_coin)
                val coins = when(rank) {
                    2 -> 80; 3 -> 60; 4 -> 40; 5 -> 20; 6 -> 10; else -> 5
                }
                holder.tvRewardValue.text = "+$coins"
                holder.rewardLayout.visibility = View.VISIBLE
            }
            else -> {
                // Resto de posiciones: No muestran layout de recompensa
                holder.rewardLayout.visibility = View.GONE
            }
        }

        // Carga de imagen de perfil circular usando la librería Glide
        Glide.with(holder.itemView.context)
            .load(user.photoUrl)
            .placeholder(R.drawable.ic_user_placeholder) // Imagen por defecto mientras carga
            .circleCrop() // Corta la imagen en forma de círculo
            .into(holder.ivAvatar)
    }

    /**
     * Retorna la cantidad total de usuarios en la liga actual.
     */
    override fun getItemCount() = users.size
}