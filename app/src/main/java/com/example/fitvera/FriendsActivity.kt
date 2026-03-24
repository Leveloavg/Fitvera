package com.example.fitvera


import User
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Actividad que gestiona y muestra la lista de amigos (usuarios seguidos) del usuario actual.
 */
class FriendsActivity : AppCompatActivity() {

    // Componentes de la interfaz de usuario
    private lateinit var friendsRecyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var adapter: FriendsAdapter
    private var allFriendsList: List<User> = ArrayList() // Lista maestra de amigos cargados

    // Instancias de Firebase
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.usuariosamigosbarrabusqueda) // Carga el diseño XML

        // Inicialización de componentes
        friendsRecyclerView = findViewById(R.id.friendsRecyclerView)
        searchEditText = findViewById(R.id.searchEditText)
        val backButton: ImageButton = findViewById(R.id.backButton)

        // Botón para cerrar la actividad actual y volver atrás
        backButton.setOnClickListener {
            finish()
        }

        // Configuración visual de la lista (Vertical)
        friendsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Carga inicial de amigos desde la base de datos
        loadFriends()

        // Lógica del buscador: Filtra la lista en tiempo real mientras el usuario escribe
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Si el adaptador ya existe, le pasamos el texto para filtrar
                if (::adapter.isInitialized) {
                    adapter.filter.filter(s.toString())
                }
            }
        })
    }

    /**
     * Obtiene la lista de amigos de Firestore usando Corrutinas para no congelar la pantalla.
     */
    private fun loadFriends() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Dispatchers.IO se usa para operaciones de red/base de datos
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Obtenemos los IDs de la subcolección "friends" del usuario actual
                val friendsResult = firestore.collection("usuarios")
                    .document(currentUserId)
                    .collection("friends")
                    .get().await()
                val friendIds = friendsResult.map { it.id }

                val friendsList = mutableListOf<User>()

                // 2. Por cada ID, buscamos el perfil completo del usuario en la colección principal
                for (friendId in friendIds) {
                    val friendDoc = firestore.collection("usuarios")
                        .document(friendId)
                        .get().await()

                    if (friendDoc.exists()) {
                        // Convertimos el documento de Firebase al objeto User de Kotlin
                        val friend = friendDoc.toObject(User::class.java)?.copy(uid = friendId)
                        if (friend != null) {
                            friendsList.add(friend)
                        }
                    }
                }

                // 3. Regresamos al hilo principal (Main) para actualizar la interfaz de usuario
                withContext(Dispatchers.Main) {
                    allFriendsList = friendsList
                    adapter = FriendsAdapter(
                        allFriendsList,
                        onUnfollowClick = { user ->
                            // Acción al pulsar el botón "Dejar de seguir"
                            showUnfollowConfirmationDialog(user)
                        },
                        onItemClick = { user ->
                            // Acción al pulsar sobre un amigo: ir a su perfil
                            val intent = Intent(this@FriendsActivity, OtroUsuarioPerfilActivity::class.java)
                            intent.putExtra("user_data", user)
                            startActivity(intent)
                        }
                    )
                    friendsRecyclerView.adapter = adapter
                }

            } catch (e: Exception) {
                // Manejo de errores en caso de fallo de conexión o permisos
                withContext(Dispatchers.Main) {
                    Log.e("FriendsActivity", "Error al cargar los amigos: ", e)
                    Toast.makeText(this@FriendsActivity, "Error al cargar la lista de amigos.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Muestra una ventana emergente de confirmación antes de eliminar a un amigo.
     */
    private fun showUnfollowConfirmationDialog(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Dejar de seguir")
            .setMessage("¿Estás seguro de que quieres dejar de seguir a ${user.nombre}?")
            .setPositiveButton("Sí") { _, _ ->
                unfollowUser(user) // Si confirma, ejecuta la eliminación
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Borra la relación de amistad en ambas direcciones en Firebase.
     */
    private fun unfollowUser(userToUnfollow: User) {
        val currentUserId = auth.currentUser?.uid ?: return

        // Usamos un WriteBatch para asegurar que ambas eliminaciones ocurran al mismo tiempo (atómico)
        val batch = firestore.batch()

        // Referencia 1: Eliminar al amigo de MI lista
        val currentUserFriendRef = firestore.collection("usuarios")
            .document(currentUserId)
            .collection("friends")
            .document(userToUnfollow.uid)
        batch.delete(currentUserFriendRef)

        // Referencia 2: Eliminarme a MÍ de la lista del OTRO usuario
        val otherUserFriendRef = firestore.collection("usuarios")
            .document(userToUnfollow.uid)
            .collection("friends")
            .document(currentUserId)
        batch.delete(otherUserFriendRef)

        // Ejecutar las operaciones
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Has dejado de seguir a ${userToUnfollow.nombre}.", Toast.LENGTH_SHORT).show()
                loadFriends() // Recargar la lista para reflejar los cambios
            }
            .addOnFailureListener { e ->
                Log.e("FriendsActivity", "Error al dejar de seguir: ", e)
                Toast.makeText(this, "Error al dejar de seguir.", Toast.LENGTH_SHORT).show()
            }
    }
}