package com.example.fitvera

// Importaciones de modelos, UI de Android y Firebase
import Rutina
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Fragmento encargado de listar las rutinas personales del usuario.
 * Permite gestionar las rutinas (Editar/Borrar) y usarlas para registrar un entrenamiento real.
 */
class FragmentListarRutinas : Fragment(R.layout.confirmarentrenamiento) {

    // Instancias de Firebase para acceder a la base de datos y al usuario actual
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ViewModel compartido para enviar la rutina seleccionada al fragmento de edición
    private val viewModel: RutinaViewModel by activityViewModels()

    private lateinit var adapter: RutinaAdapter
    private var listaRutinas = mutableListOf<Rutina>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Configuración del RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.rvMisRutinas)

        // Inicializamos el adaptador con tres funciones lambda (clics en botones)
        adapter = RutinaAdapter(listaRutinas,
            onEdit = { rutina -> editarRutina(rutina) },      // Acción al pulsar editar
            onDelete = { rutina -> borrarRutina(rutina) },    // Acción al pulsar borrar
            onConfirm = { rutina -> registrarEntrenamientoFuerza(rutina) } // Acción al completar rutina
        )

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // 2. Carga inicial de datos
        cargarRutinasDesdeFirebase()
    }

    /**
     * Se conecta a Firestore para traer las rutinas que pertenecen al usuario logueado.
     * Usa un snapshotListener para actualizar la lista automáticamente si algo cambia en la nube.
     */
    private fun cargarRutinasDesdeFirebase() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("rutinas")
            .whereEqualTo("userId", uid) // Filtra para que solo veas tus propias rutinas
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    // Mapeamos los documentos de Firebase a objetos de nuestra clase Rutina
                    val nuevasRutinas = snapshot.documents.mapNotNull { doc ->
                        val r = doc.toObject(Rutina::class.java)
                        r?.id = doc.id // Asignamos el ID interno de Firebase para futuras ediciones
                        r
                    }
                    listaRutinas.clear()
                    listaRutinas.addAll(nuevasRutinas)
                    adapter.notifyDataSetChanged() // Refrescamos la lista en pantalla
                }
            }
    }

    /**
     * Elimina el documento de la rutina seleccionada de la colección de Firebase.
     */
    private fun borrarRutina(rutina: Rutina) {
        if (rutina.id.isEmpty()) return
        db.collection("rutinas").document(rutina.id).delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Rutina eliminada", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Envía la rutina al ViewModel y cambia la pestaña del ViewPager para abrir
     * el formulario de creación/edición.
     */
    private fun editarRutina(rutina: Rutina) {
        viewModel.rutinaSeleccionada.value = rutina
        // Accedemos al ViewPager de la actividad principal para movernos a la pestaña 0 (Crear)
        val viewPager = activity?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewpager)
        viewPager?.currentItem = 0
    }

    /**
     * Convierte una Rutina estática en un Entrenamiento realizado y lo guarda
     * en el historial de actividades del usuario.
     */
    private fun registrarEntrenamientoFuerza(rutina: Rutina) {
        val uid = auth.currentUser?.uid ?: return

        // Generamos la fecha actual
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val fechaHoy = sdf.format(java.util.Date())

        // Creamos un mapa de datos para Firebase con la estructura de un 'Entrenamiento'
        val entrenamiento = hashMapOf(
            "tipo" to "Fuerza",
            "nombre" to rutina.nombreRutina,
            "fecha" to fechaHoy,
            "userId" to uid,
            "ejercicios" to rutina.ejercicios,

            // Convertimos los minutos de la rutina a milisegundos para ser consistente
            // con los entrenamientos de GPS (Running)
            "tiempo" to (rutina.tiempoMin.toLong() * 60000),

            "dificultad" to rutina.dificultad
        )

        // Guardamos el nuevo entrenamiento en la subcolección del usuario
        db.collection("usuarios").document(uid)
            .collection("entrenamientos").add(entrenamiento)
            .addOnSuccessListener {
                Toast.makeText(context, "¡Entrenamiento registrado con éxito!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al registrar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}