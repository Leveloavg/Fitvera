package com.example.fitvera


import Ejercicio
import Rutina
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Fragmento encargado de la interfaz para crear una nueva rutina desde cero
 * o editar una existente cargada desde el ViewModel.
 */
class FragmentCrearRutina : Fragment(R.layout.crearrutina) {

    // Instancias de Firebase para Base de Datos y Autenticación
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ViewModel compartido para recibir datos si se decide "Editar" una rutina existente
    private val viewModel: RutinaViewModel by activityViewModels()

    // Lista local de ejercicios que se van añadiendo a la rutina actual
    private val listaEjercicios = mutableListOf<Ejercicio>()
    private lateinit var adapter: EjercicioAdapter

    // Variable de control: si es null crea una rutina nueva, si tiene ID actualiza la existente
    private var idRutinaActual: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Vinculación de componentes de la Interfaz (UI)
        val etNombre = view.findViewById<EditText>(R.id.etNombreRutina)
        val etTiempo = view.findViewById<EditText>(R.id.etTiempo)
        val spinner = view.findViewById<Spinner>(R.id.spinnerDificultad)
        val btnAdd = view.findViewById<ImageButton>(R.id.btnAddEjercicio)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarRutina)
        val rv = view.findViewById<RecyclerView>(R.id.rvEjercicios)

        // 2. Configuración del RecyclerView (la lista de ejercicios dentro de la rutina)
        adapter = EjercicioAdapter(listaEjercicios)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // 3. Configuración del Spinner (Menú desplegable de dificultad)
        val opciones = arrayOf("Baja", "Media", "Alta", "Muy intensa")
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, opciones)

        // 4. OBSERVADOR DE EDICIÓN:
        // Si el usuario seleccionó "Editar" en otro fragmento, este LiveData tendrá datos.
        viewModel.rutinaSeleccionada.observe(viewLifecycleOwner) { rutina ->
            if (rutina != null) {
                // Modo Edición: Rellenamos los campos con la información de la rutina
                idRutinaActual = rutina.id
                etNombre.setText(rutina.nombreRutina)
                etTiempo.setText(rutina.tiempoMin.toString())

                // Busca la posición del texto de dificultad para marcarlo en el spinner
                val pos = opciones.indexOf(rutina.dificultad)
                if (pos >= 0) spinner.setSelection(pos)

                // Carga los ejercicios que ya tenía la rutina
                listaEjercicios.clear()
                listaEjercicios.addAll(rutina.ejercicios)
                adapter.notifyDataSetChanged()

                // Cambia el texto del botón para indicar actualización
                btnGuardar.text = "ACTUALIZAR RUTINA"

                // Importante: Limpiamos el objeto seleccionado para evitar bucles de observación
                viewModel.rutinaSeleccionada.value = null
            }
        }

        // 5. EVENTO: Añadir nuevo ejercicio vacío a la lista
        btnAdd.setOnClickListener {
            listaEjercicios.add(Ejercicio()) // Crea un objeto Ejercicio por defecto
            adapter.notifyItemInserted(listaEjercicios.size - 1) // Notifica al adapter para refrescar
        }

        // 6. EVENTO: Guardar datos en Firebase
        btnGuardar.setOnClickListener {
            val nombre = etNombre.text.toString()
            val tiempo = etTiempo.text.toString().toIntOrNull() ?: 0
            val dif = spinner.selectedItem.toString()

            // Llama a la función de guardado
            guardarOActualizar(nombre, tiempo, dif)
        }
    }

    /**
     * Lógica para persistir los datos en Firestore.
     * Diferencia entre crear un documento nuevo (.add) o actualizar uno existente (.set).
     */
    private fun guardarOActualizar(nombre: String, tiempo: Int, dif: String) {
        val uid = auth.currentUser?.uid ?: return // Seguridad: Verifica que el usuario esté logueado

        // Validación básica
        if (nombre.isEmpty() || listaEjercicios.isEmpty()) {
            Toast.makeText(requireContext(), "Faltan datos", Toast.LENGTH_SHORT).show()
            return
        }

        // Crea el objeto de datos (Data Class Rutina)
        val rutinaData = Rutina(nombre, tiempo, dif, listaEjercicios, uid)

        // Decidimos la tarea de Firebase
        val task = if (idRutinaActual == null) {
            db.collection("rutinas").add(rutinaData) // Crea un ID automático nuevo
        } else {
            db.collection("rutinas").document(idRutinaActual!!).set(rutinaData) // Sobrescribe el documento con ese ID
        }

        task.addOnSuccessListener {
            Toast.makeText(requireContext(), "Guardado correctamente", Toast.LENGTH_SHORT).show()
            limpiarCampos() // Resetea la pantalla para una nueva rutina
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Resetea toda la interfaz de usuario a su estado original.
     */
    private fun limpiarCampos() {
        idRutinaActual = null
        view?.findViewById<EditText>(R.id.etNombreRutina)?.text?.clear()
        view?.findViewById<EditText>(R.id.etTiempo)?.text?.clear()
        view?.findViewById<Button>(R.id.btnGuardarRutina)?.text = "GUARDAR EN CLOUD"
        listaEjercicios.clear()
        adapter.notifyDataSetChanged()
    }
}