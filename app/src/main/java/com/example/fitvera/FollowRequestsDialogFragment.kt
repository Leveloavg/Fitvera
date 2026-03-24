package com.example.fitvera

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView

// Clase de datos para representar una solicitud de seguimiento.
// Contiene el ID del remitente, su nombre y la URL de su foto de perfil.
data class FollowRequest(
    val senderId: String = "",
    val nombre: String = "",
    val fotoUrl: String? = null
)


class FollowRequestsDialogFragment : BottomSheetDialogFragment() {

    // Vistas y dependencias de Firebase.
    private lateinit var requestsRecyclerView: RecyclerView
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_follow_requests, container, false)
    }


    // Se llama después de que la vista del fragmento ha sido creada.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializa el RecyclerView y establece un LayoutManager.
        requestsRecyclerView = view.findViewById(R.id.requestsRecyclerView)
        requestsRecyclerView.layoutManager = LinearLayoutManager(context)

        // Carga las solicitudes de seguimiento del usuario actual.
        loadRequests()
    }

    // Carga las solicitudes de seguimiento recibidas desde Firestore.
    private fun loadRequests() {
        val currentUserId = auth.currentUser?.uid ?: return
        val requestsRef = firestore.collection("usuarios").document(currentUserId)
            .collection("follow_requests")

        // Obtiene todos los documentos de la subcolección 'follow_requests'.
        requestsRef.get()
            .addOnSuccessListener { snapshots ->
                val requestsList = mutableListOf<FollowRequest>()
                // Si no hay solicitudes, configura el adaptador con una lista vacía y sale.
                if (snapshots.isEmpty) {
                    requestsRecyclerView.adapter = RequestsAdapter(requestsList, this)
                    return@addOnSuccessListener
                }

                // Para cada solicitud encontrada, obtiene la información del usuario remitente.
                snapshots.documents.forEach { doc ->
                    val senderId = doc.id
                    firestore.collection("usuarios").document(senderId).get()
                        .addOnSuccessListener { senderDoc ->
                            if (senderDoc.exists()) {
                                // Extrae el nombre y la foto del remitente y los añade a la lista.
                                val senderName = senderDoc.getString("nombre") ?: "Usuario"
                                val senderPhoto = senderDoc.getString("fotoUrl")
                                requestsList.add(FollowRequest(senderId, senderName, senderPhoto))
                            }
                            // Cuando todas las solicitudes han sido cargadas, configura el adaptador.
                            // Esto asegura que la lista se muestre solo cuando esté completa.
                            if (requestsList.size == snapshots.size()) {
                                val adapter = RequestsAdapter(requestsList, this)
                                requestsRecyclerView.adapter = adapter
                            }
                        }
                        .addOnFailureListener {
                            Log.e("FollowDialog", "Error loading sender user data: ${it.message}")
                        }
                }
            }
            .addOnFailureListener {
                Log.e("FollowDialog", "Error loading follow requests: ${it.message}")
                Toast.makeText(context, "Error al cargar las solicitudes.", Toast.LENGTH_SHORT).show()
            }
    }

    // Maneja la lógica de aceptar o denegar una solicitud.
    fun handleRequest(senderId: String, accept: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        val batch = firestore.batch()

        // 1. Elimina la solicitud del destinatario
        val requestRef = firestore.collection("usuarios").document(currentUserId)
            .collection("follow_requests").document(senderId)
        batch.delete(requestRef)

        // 2. Elimina la solicitud del remitente
        val senderRequestRef = firestore.collection("usuarios").document(senderId)
            .collection("follow_requests_sent").document(currentUserId)
        batch.delete(senderRequestRef)

        if (accept) {
            val timestamp = System.currentTimeMillis()

            // 3. Si se acepta, crea una entrada de "amigo" para el usuario actual
            val currentUserFriendRef = firestore.collection("usuarios").document(currentUserId)
                .collection("friends").document(senderId)
            batch.set(currentUserFriendRef, hashMapOf("timestamp" to timestamp))

            // 4. Crea una entrada de "amigo" para el remitente
            val senderFriendRef = firestore.collection("usuarios").document(senderId)
                .collection("friends").document(currentUserId)
            batch.set(senderFriendRef, hashMapOf("timestamp" to timestamp))
        }

        // Confirma todas las operaciones en un solo lote para garantizar la atomicidad.
        batch.commit()
            .addOnSuccessListener {
                if (accept) {
                    Toast.makeText(context, "Solicitud aceptada. Ahora sois amigos.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Solicitud denegada.", Toast.LENGTH_SHORT).show()
                }
                // Cierra el diálogo después de procesar la solicitud.
                dismiss()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al procesar la solicitud: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("FollowDialog", "Error", e)
            }
    }

    // Clase interna para el adaptador del RecyclerView.
    private class RequestsAdapter(
        private val requests: List<FollowRequest>,
        private val fragment: FollowRequestsDialogFragment
    ) : RecyclerView.Adapter<RequestsAdapter.RequestViewHolder>() {

        // ViewHolder para el diseño de la tarjeta de solicitud.
        class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val profileImageView: CircleImageView = itemView.findViewById(R.id.profileImageView)
            val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
            val acceptButton: Button = itemView.findViewById(R.id.acceptButton)
            val denyButton: Button = itemView.findViewById(R.id.denyButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_follow_request, parent, false)
            return RequestViewHolder(view)
        }

        override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
            val request = requests[position]
            holder.nameTextView.text = request.nombre

            // Carga la foto de perfil del remitente.
            if (!request.fotoUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(request.fotoUrl).into(holder.profileImageView)
            } else {
                holder.profileImageView.setImageResource(R.drawable.ic_default_profile)
            }

            // Establece los oyentes de clic para los botones de aceptar y denegar.
            holder.acceptButton.setOnClickListener {
                fragment.handleRequest(request.senderId, true)
            }

            holder.denyButton.setOnClickListener {
                fragment.handleRequest(request.senderId, false)
            }
        }

        // Devuelve el número total de solicitudes.
        override fun getItemCount(): Int = requests.size
    }
}