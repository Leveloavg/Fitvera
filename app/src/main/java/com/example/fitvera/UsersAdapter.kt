package com.example.fitvera

import User
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView

// La clase UsersAdapter maneja los datos y las vistas para un RecyclerView.
// Implementa Filterable para permitir la búsqueda y el filtrado.
class UsersAdapter(private var originalUserList: List<User>) :
    RecyclerView.Adapter<UsersAdapter.UserViewHolder>(), Filterable {

    // 'filteredUserList' es la lista que se muestra, y puede ser modificada por el filtro.
    private var filteredUserList: List<User> = originalUserList.toMutableList()

    // Instancias de Firebase para interactuar con la autenticación y Firestore.
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // 'UserViewHolder' contiene las referencias a las vistas de cada elemento de la lista.
    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImageView: CircleImageView = itemView.findViewById(R.id.profileImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        val friendsCountTextView: TextView = itemView.findViewById(R.id.friendsCountTextView)
        val followButton: Button = itemView.findViewById(R.id.followButton)
    }

    // Nuevo método para actualizar el estado del usuario en ambas listas
    // Se usa cuando se envía una solicitud para que la tarjeta cambie inmediatamente a "Pendiente".
    fun updateUserStatus(userToUpdate: User) {
        // Encontrar y actualizar en la lista original (haciéndola mutable temporalmente)
        val mutableOriginalList = originalUserList.toMutableList()
        val originalIndex = mutableOriginalList.indexOfFirst { it.uid == userToUpdate.uid }

        if (originalIndex != -1) {
            // Crea una nueva copia del usuario con el estado actualizado
            val updatedUser = mutableOriginalList[originalIndex].copy(hasSentRequest = true)
            mutableOriginalList[originalIndex] = updatedUser
            originalUserList = mutableOriginalList.toList()

            // Actualizar la lista filtrada si el usuario está en ella
            val mutableFilteredList = filteredUserList.toMutableList()
            val filteredIndex = mutableFilteredList.indexOfFirst { it.uid == userToUpdate.uid }

            if (filteredIndex != -1) {
                mutableFilteredList[filteredIndex] = updatedUser
                filteredUserList = mutableFilteredList.toList()
                // Notificar el cambio para refrescar la vista solo de esa tarjeta
                notifyItemChanged(filteredIndex)
            }
        }
    }

    // Este método es llamado cuando el RecyclerView necesita un nuevo ViewHolder.
    // Infla el diseño XML para una tarjeta de usuario.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_card_item, parent, false)
        return UserViewHolder(view)
    }

    // Este método enlaza los datos de un objeto User a las vistas en el ViewHolder.
    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = filteredUserList[position]
        val context = holder.itemView.context

        holder.nameTextView.text = user.nombre

        // Usa Glide para cargar la imagen de perfil del usuario.
        if (!user.fotoUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(user.fotoUrl)
                .apply(RequestOptions.placeholderOf(R.drawable.ic_default_profile))
                .into(holder.profileImageView)
        } else {
            holder.profileImageView.setImageResource(R.drawable.ic_default_profile)
        }

        // Usamos el campo mutualFriendsCount que ya se calculó en el fragment
        val mutualFriendsCount = user.mutualFriendsCount
        val friendWord = if (mutualFriendsCount == 1) "amigo" else "amigos"
        holder.friendsCountTextView.text = "Sigue a $mutualFriendsCount $friendWord en común"

        // Lógica para determinar la apariencia del botón según el estado de la relación.
        if (user.isFriend) {
            // Si ya son amigos, el botón muestra "Amigos" y está deshabilitado.
            holder.followButton.text = "Amigos"
            holder.followButton.setBackgroundColor(Color.parseColor("#4CAF50"))
            holder.followButton.isEnabled = false
        } else if (user.hasSentRequest || user.hasReceivedRequest) {
            // Si se ha enviado o recibido una solicitud, el botón muestra "Pendiente" y está deshabilitado.
            holder.followButton.text = "Pendiente"
            holder.followButton.setBackgroundColor(Color.parseColor("#FFC107"))
            holder.followButton.isEnabled = false
        } else {
            // De lo contrario, el botón muestra "Seguir" y está habilitado.
            holder.followButton.text = "Seguir"
            holder.followButton.setBackgroundColor(Color.parseColor("#2196F3"))
            holder.followButton.isEnabled = true
        }

        // ----------------------------------------------------
        // LÓGICA DE CLIC EN LA TARJETA COMPLETA
        // ----------------------------------------------------
        holder.itemView.setOnClickListener {
            // Se crea un Intent para iniciar la actividad del perfil
            val intent = Intent(context, OtroUsuarioPerfilActivity::class.java)

            // Se pasa el objeto User completo a la actividad.
            intent.putExtra("user_data", user)

            context.startActivity(intent)
        }
        // ----------------------------------------------------


        // Maneja la acción de clic en el botón de seguir
        holder.followButton.setOnClickListener {
            val buttonText = holder.followButton.text.toString()
            if (buttonText == "Seguir") {
                // Si el botón dice "Seguir", se envía una solicitud de seguimiento.
                // Se pasa el objeto user y el contexto para poder actualizar el estado localmente y mostrar el Toast.
                sendFollowRequest(auth.currentUser?.uid ?: "", user, holder.itemView.context)
            } else {
                Toast.makeText(context, "La solicitud ya está en curso.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Este método envía una solicitud de seguimiento usando una escritura por lotes para asegurar la consistencia.
    // Se añade 'contexto' como parámetro para poder mostrar el Toast.
    private fun sendFollowRequest(senderId: String, user: User, contexto: android.content.Context) {
        val receiverId = user.uid
        val batch = firestore.batch()

        // 1. Crea una entrada de solicitud en la subcolección del receptor.
        val receiverRequestRef = firestore.collection("usuarios").document(receiverId)
            .collection("follow_requests").document(senderId)
        batch.set(receiverRequestRef, mapOf("timestamp" to System.currentTimeMillis()))

        // 2. Crea una entrada en la subcolección de solicitudes enviadas del remitente.
        val senderRequestRef = firestore.collection("usuarios").document(senderId)
            .collection("follow_requests_sent").document(receiverId)
        batch.set(senderRequestRef, mapOf("timestamp" to System.currentTimeMillis()))

        // Confirma el lote. Si tiene éxito, actualiza el estado del botón y muestra un mensaje.
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(
                    contexto, // Se usa el contexto pasado como parámetro
                    "Solicitud de seguimiento enviada.",
                    Toast.LENGTH_SHORT
                ).show()

                // LLAMADA AL NUEVO MÉTODO: Actualiza el estado de la tarjeta inmediatamente
                updateUserStatus(user)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    contexto, // Se usa el contexto pasado como parámetro
                    "Error al enviar la solicitud: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Devuelve el número total de elementos en la lista filtrada.
    override fun getItemCount(): Int {
        return filteredUserList.size
    }

    // Este método proporciona la lógica de filtrado para la barra de búsqueda.
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()
                filteredUserList = if (charSearch.isEmpty()) {
                    originalUserList
                } else {
                    originalUserList.filter {
                        // Filtra la lista basándose en si el nombre comienza con la consulta de búsqueda.
                        it.nombre.lowercase().startsWith(charSearch.lowercase())
                    }
                }
                val filterResults = FilterResults()
                filterResults.values = filteredUserList
                return filterResults
            }

            // Publica los resultados filtrados y notifica al adaptador sobre el cambio en los datos.
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                filteredUserList = results.values as List<User>
                notifyDataSetChanged()
            }
        }
    }
}