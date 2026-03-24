package com.example.fitvera

import User
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.hdodenhof.circleimageview.CircleImageView

/**
 * Adaptador para mostrar la lista de amigos.
 * Implementa 'Filterable' para permitir la búsqueda dinámica por nombre.
 */
class FriendsAdapter(
    private var originalFriendsList: List<User>, // Lista maestra que nunca cambia (fuente de verdad)
    private val onUnfollowClick: (User) -> Unit, // Callback para el botón de dejar de seguir
    private val onItemClick: (User) -> Unit      // Callback para cuando se toca toda la celda (ver perfil)
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>(), Filterable {

    // Lista que se muestra realmente y que cambia según los filtros aplicados
    private var filteredFriendsList: List<User> = originalFriendsList.toMutableList()

    /**
     * ViewHolder: Contenedor que guarda las referencias a las vistas de cada fila
     * para no tener que buscarlas con 'findViewById' repetidamente (mejora el rendimiento).
     */
    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImageView: CircleImageView = itemView.findViewById(R.id.profileImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        val detailsTextView: TextView = itemView.findViewById(R.id.detailsTextView)
        val unfollowButton: ImageView = itemView.findViewById(R.id.actionButton)
    }

    /**
     * Infla el diseño XML ('layoutusuariosamigos') para cada fila de la lista.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layoutusuariosamigos, parent, false)
        return FriendViewHolder(view)
    }

    /**
     * Conecta los datos de un usuario específico con los elementos visuales de la fila.
     */
    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = filteredFriendsList[position]

        // Asignar textos básicos
        holder.nameTextView.text = user.nombre
        holder.detailsTextView.text = "${user.sexo}, ${user.edad} años"

        // Carga de imagen de perfil con Glide
        if (!user.fotoUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.fotoUrl)
                .apply(RequestOptions.placeholderOf(R.drawable.ic_default_profile)) // Imagen mientras carga
                .into(holder.profileImageView)
        } else {
            // Si no hay URL, poner imagen por defecto
            holder.profileImageView.setImageResource(R.drawable.ic_default_profile)
        }

        // Configurar clic en el botón de acción (dejar de seguir)
        holder.unfollowButton.setOnClickListener {
            onUnfollowClick(user)
        }

        // Configurar clic en cualquier parte de la tarjeta del amigo
        holder.itemView.setOnClickListener {
            onItemClick(user)
        }
    }

    /**
     * Devuelve el tamaño de la lista que se está filtrando actualmente.
     */
    override fun getItemCount(): Int {
        return filteredFriendsList.size
    }

    /**
     * Lógica del buscador (Filtro).
     */
    override fun getFilter(): Filter {
        return object : Filter() {
            // Se ejecuta en un hilo secundario para no bloquear la app al buscar
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()

                // Si el buscador está vacío, mostramos la lista original completa
                filteredFriendsList = if (charSearch.isEmpty()) {
                    originalFriendsList
                } else {
                    // Filtrar: devuelve los usuarios cuyo nombre empiece por el texto escrito
                    originalFriendsList.filter {
                        it.nombre.lowercase().startsWith(charSearch.lowercase())
                    }
                }

                val filterResults = FilterResults()
                filterResults.values = filteredFriendsList
                return filterResults
            }

            // Se ejecuta en el hilo principal para actualizar la interfaz con los resultados
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                filteredFriendsList = results.values as List<User>
                notifyDataSetChanged() // Notificar al RecyclerView que los datos cambiaron
            }
        }
    }
}