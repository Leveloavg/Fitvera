package com.example.fitvera

// Importaciones de librerías de Android, Firebase y Corrutinas
import User
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.ArrayList

class SearchFragment : Fragment() {

    // Declaración de variables de la interfaz (UI)
    private lateinit var usersRecyclerView: RecyclerView // Lista de usuarios sugeridos/buscados
    private lateinit var searchEditText: EditText       // Campo de texto para buscar
    private lateinit var adapter: UsersAdapter           // Adaptador para la lista de usuarios
    private lateinit var contentContainer: View          // Contenedor principal de la vista

    // Vistas para el sistema de notificaciones
    private lateinit var notificationsButton: ImageButton // Botón de la campana/notificaciones
    private lateinit var unreadIndicator: View            // Punto rojo de "no leído"

    // Listas de datos
    private var allUsersList: List<User> = ArrayList()    // Todos los usuarios cargados
    private var allRequests: MutableList<NotificacionSeguimiento> = mutableListOf() // Solicitudes de amistad

    // Instancias de Firebase
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Infla el diseño XML de la pantalla de búsqueda
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.lupabusqueda, container, false)
    }

    // Se ejecuta cuando la vista ya ha sido creada
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Vinculación de variables con los IDs del XML
        usersRecyclerView = view.findViewById(R.id.usersRecyclerView)
        searchEditText = view.findViewById(R.id.searchEditText)
        contentContainer = view.findViewById(R.id.contentContainer)
        notificationsButton = view.findViewById(R.id.btn_notifications)
        unreadIndicator = view.findViewById(R.id.unread_requests_indicator)

        // Configura el RecyclerView en una cuadrícula de 2 columnas
        usersRecyclerView.layoutManager = GridLayoutManager(context, 2)

        // Oculta el contenido inicialmente mientras carga
        contentContainer.visibility = View.GONE

        // Al hacer clic en la campana, muestra el diálogo de solicitudes
        notificationsButton.setOnClickListener {
            showRequestsDialog()
        }

        // Listener para detectar cuando el usuario escribe en el buscador
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Filtra la lista del adaptador según el texto escrito
                if (::adapter.isInitialized) {
                    adapter.filter.filter(s.toString())
                }
            }
        })
    }

    // Se ejecuta cada vez que el fragmento vuelve a estar visible
    override fun onResume() {
        super.onResume()
        loadUsersWithFriendStatus() // Recarga usuarios y sus estados de amistad
        loadFollowRequests()        // Recarga las notificaciones pendientes
    }

    // ==========================================================
    // LÓGICA DE NOTIFICACIONES (SOLICITUDES)
    // ==========================================================

    // Carga tanto solicitudes recibidas como enviadas desde Firestore
    private fun loadFollowRequests() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Inicia una corrutina en hilo secundario (IO)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requests = mutableListOf<NotificacionSeguimiento>()
                var hasUnread = false

                // 1. Obtener solicitudes que me han enviado a mí (RECIBIDAS)
                val receivedDocs = firestore.collection("usuarios").document(currentUserId)
                    .collection("follow_requests")
                    .get().await()

                for (doc in receivedDocs.documents) {
                    val senderId = doc.id
                    // Busca los datos (nombre, foto) del usuario que envió la solicitud
                    val userData = firestore.collection("usuarios").document(senderId).get().await()
                    val user = userData.toObject(User::class.java)
                    if (user != null) {
                        hasUnread = true // Si hay al menos una recibida, activamos el indicador
                        requests.add(NotificacionSeguimiento(
                            uid = senderId,
                            nombre = user.nombre,
                            fotoUrl = user.fotoUrl,
                            tipo = TipoSolicitud.RECIBIDA,
                            timestamp = doc.getLong("timestamp") ?: 0,
                            esLeida = false
                        ))
                    }
                }

                // 2. Obtener solicitudes que yo he enviado a otros (ENVIADAS)
                val sentDocs = firestore.collection("usuarios").document(currentUserId)
                    .collection("follow_requests_sent")
                    .get().await()

                for (doc in sentDocs.documents) {
                    val receiverId = doc.id
                    val userData = firestore.collection("usuarios").document(receiverId).get().await()
                    val user = userData.toObject(User::class.java)
                    if (user != null) {
                        requests.add(NotificacionSeguimiento(
                            uid = receiverId,
                            nombre = user.nombre,
                            fotoUrl = user.fotoUrl,
                            tipo = TipoSolicitud.ENVIADA,
                            timestamp = doc.getLong("timestamp") ?: 0,
                            esLeida = true
                        ))
                    }
                }

                // Ordena la lista por tiempo (la más reciente primero)
                requests.sortByDescending { it.timestamp }
                allRequests = requests

                // Vuelve al hilo principal (Main) para actualizar la UI
                withContext(Dispatchers.Main) {
                    updateUnreadIndicator(hasUnread)
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error al cargar solicitudes: ", e)
            }
        }
    }

    // Muestra u oculta el punto rojo de notificaciones
    private fun updateUnreadIndicator(hasUnread: Boolean) {
        if (::unreadIndicator.isInitialized) {
            unreadIndicator.visibility = if (hasUnread) View.VISIBLE else View.GONE
        }
    }

    // Muestra una ventana emergente (Dialog) con la lista de solicitudes
    private fun showRequestsDialog() {
        val context = context ?: return

        // Si el usuario abre el diálogo, consideramos que "vio" las notificaciones
        if (allRequests.any { it.tipo == TipoSolicitud.RECIBIDA && !it.esLeida }) {
            updateUnreadIndicator(false)
        }

        // Infla el diseño del diálogo
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_requests_list, null)
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.requestsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Crea el AlertDialog
        val dialog = AlertDialog.Builder(context)
            .setTitle("Solicitudes y Notificaciones")
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .create()

        // Configura el adaptador del diálogo con la lista de solicitudes cargadas
        val adapter = RequestAdapter(allRequests.toMutableList()) { request, action ->
            handleRequestAction(request, action) // Procesa Aceptar o Rechazar
            dialog.dismiss() // Cierra el diálogo después de la acción
        }
        recyclerView.adapter = adapter

        dialog.show()
    }

    // Maneja qué hacer cuando se pulsa "Aceptar" o "Rechazar/Cancelar"
    private fun handleRequestAction(request: NotificacionSeguimiento, action: RequestAdapter.RequestAction) {
        val currentUserId = auth.currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val batch = firestore.batch() // Usa un batch para hacer varios cambios a la vez

                if (action == RequestAdapter.RequestAction.ACCEPT) {
                    // Acción: ACEPTAR solicitud
                    // 1. Añadir al otro usuario como mi amigo
                    val currentUserFriendsRef = firestore.collection("usuarios").document(currentUserId)
                        .collection("friends").document(request.uid)
                    batch.set(currentUserFriendsRef, mapOf("timestamp" to System.currentTimeMillis()))

                    // 2. Añadirme a mí como amigo del otro usuario
                    val otherUserFriendsRef = firestore.collection("usuarios").document(request.uid)
                        .collection("friends").document(currentUserId)
                    batch.set(otherUserFriendsRef, mapOf("timestamp" to System.currentTimeMillis()))

                    // 3. Borrar la solicitud de mi colección "recibidas"
                    val receivedRequestRef = firestore.collection("usuarios").document(currentUserId)
                        .collection("follow_requests").document(request.uid)
                    batch.delete(receivedRequestRef)

                    // 4. Borrar la solicitud de la colección "enviadas" del otro usuario
                    val sentRequestRef = firestore.collection("usuarios").document(request.uid)
                        .collection("follow_requests_sent").document(currentUserId)
                    batch.delete(sentRequestRef)

                    batch.commit().await() // Ejecuta todos los cambios anteriores

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "¡Ahora sois amigos!", Toast.LENGTH_SHORT).show()
                        loadFollowRequests() // Refresca las listas
                        loadUsersWithFriendStatus()
                    }
                } else if (action == RequestAdapter.RequestAction.CANCEL_OR_REJECT) {
                    // Acción: RECHAZAR o CANCELAR solicitud
                    // Simplemente borra los documentos de solicitudes de ambos lados
                    val receivedRequestRef = firestore.collection("usuarios").document(currentUserId)
                        .collection("follow_requests").document(request.uid)
                    batch.delete(receivedRequestRef)

                    val sentRequestRef = firestore.collection("usuarios").document(request.uid)
                        .collection("follow_requests_sent").document(currentUserId)
                    batch.delete(sentRequestRef)

                    batch.commit().await()

                    withContext(Dispatchers.Main) {
                        loadFollowRequests()
                        loadUsersWithFriendStatus()
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error: ${e.message}")
            }
        }
    }

    // ==========================================================
    // CARGA DE USUARIOS PARA BÚSQUEDA
    // ==========================================================

    // Carga usuarios de la base de datos y determina si son amigos, si hay solicitudes, etc.
    private fun loadUsersWithFriendStatus() {
        val currentUserId = auth.currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtiene todos los usuarios de la app
                val usersResult = firestore.collection("usuarios").get().await()

                // Filtra para no mostrarme a mí mismo, mezcla y toma 10 usuarios al azar
                val limitedUserDocs = usersResult.documents
                    .filter { it.id != currentUserId }
                    .shuffled()
                    .take(10)

                // Obtiene mi lista de amigos actuales
                val currentUserFriendsResult = firestore.collection("usuarios")
                    .document(currentUserId)
                    .collection("friends")
                    .get().await()
                val currentUserFriendIds = currentUserFriendsResult.map { it.id }.toSet()

                // Obtiene IDs de usuarios a los que les he enviado solicitud
                val requestsSentResult = firestore.collection("usuarios")
                    .document(currentUserId)
                    .collection("follow_requests_sent")
                    .get().await()
                val requestsSentIdSet = requestsSentResult.map { it.id }.toSet()

                // Obtiene IDs de usuarios que me han enviado solicitud a mí
                val requestsReceivedResult = firestore.collection("usuarios")
                    .document(currentUserId)
                    .collection("follow_requests")
                    .get().await()
                val requestsReceivedIdSet = requestsReceivedResult.map { it.id }.toSet()

                val userList = mutableListOf<User>()

                // Itera sobre los 10 usuarios seleccionados para calcular amigos comunes y estados
                for (document in limitedUserDocs) {
                    val user = document.toObject(User::class.java)?.copy(uid = document.id) ?: continue

                    // Obtiene amigos de ese usuario para calcular amigos en común
                    val otherUserFriendsResult = firestore.collection("usuarios")
                        .document(user.uid)
                        .collection("friends")
                        .get().await()
                    val otherUserFriendIds = otherUserFriendsResult.map { it.id }.toSet()

                    // Intersección de sets para contar amigos comunes
                    val mutualFriendsCount = currentUserFriendIds.intersect(otherUserFriendIds).size

                    // Asignación de estados al objeto usuario
                    user.mutualFriendsCount = mutualFriendsCount
                    user.isFriend = currentUserFriendIds.contains(user.uid)
                    user.hasSentRequest = requestsSentIdSet.contains(user.uid)
                    user.hasReceivedRequest = requestsReceivedIdSet.contains(user.uid)

                    userList.add(user)
                }

                // Actualiza el adaptador en el hilo principal
                withContext(Dispatchers.Main) {
                    allUsersList = userList
                    adapter = UsersAdapter(allUsersList)
                    usersRecyclerView.adapter = adapter
                    contentContainer.visibility = View.VISIBLE // Muestra la lista cargada
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    contentContainer.visibility = View.VISIBLE
                }
            }
        }
    }
}