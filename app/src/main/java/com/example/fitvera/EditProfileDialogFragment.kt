package com.example.fitvera

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hbb20.CountryCodePicker
import de.hdodenhof.circleimageview.CircleImageView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.*

/**
 * EditProfileDialogFragment: Un cuadro de diálogo (Pop-up) que permite al usuario
 * editar su información de perfil (nombre, sexo, edad, país y foto).
 */
class EditProfileDialogFragment : DialogFragment() {

    /**
     * Interfaz para comunicar los datos guardados de vuelta a la Activity o Fragment que llamó al diálogo.
     */
    interface EditProfileDialogListener {
        fun onProfileSaved(nombre: String, sexo: String, edad: String, pais: String, fotoUrl: String?)
    }

    // Variables globales para la lógica del fragmento
    var listener: EditProfileDialogListener? = null
    private var selectedImageUri: Uri? = null // Almacena la ruta local de la imagen seleccionada

    // Referencias a las vistas del archivo XML
    private lateinit var profilePicImageView: CircleImageView
    private lateinit var nombreEditText: EditText
    private lateinit var sexoToggleGroup: MaterialButtonToggleGroup
    private lateinit var edadEditText: EditText
    private lateinit var ccpPais: CountryCodePicker

    // Códigos y constantes para servicios externos
    private val PICK_IMAGE_REQUEST_CODE = 102
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Configuración del tamaño del diálogo al iniciarse.
     */
    override fun onStart() {
        super.onStart()
        // Ajusta el ancho al máximo de la pantalla y el alto según el contenido
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * Infla el diseño XML definido en dialog_edit_profile.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_edit_profile, container, false)
    }

    /**
     * Se ejecuta después de que la vista ha sido creada. Aquí se inicializan los listeners y datos.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Inicialización de componentes de la UI
        nombreEditText = view.findViewById(R.id.et_nombre)
        sexoToggleGroup = view.findViewById(R.id.toggle_group_sexo)
        edadEditText = view.findViewById(R.id.et_edad)
        ccpPais = view.findViewById(R.id.ccp_pais)

        val guardarButton = view.findViewById<Button>(R.id.btn_guardar)
        val cancelarButton = view.findViewById<Button>(R.id.btn_cancelar)
        profilePicImageView = view.findViewById(R.id.iv_profile_pic_edit)
        val editPicButton = view.findViewById<FloatingActionButton>(R.id.fab_edit_pic)

        // 2. Cargar los datos actuales del usuario desde Firestore para rellenar el formulario
        loadCurrentProfileData()

        // 3. Definición de la lógica para abrir la galería del teléfono
        val openGallery = {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST_CODE)
        }

        // Eventos para cambiar la foto (clic en la imagen o en el botón flotante)
        profilePicImageView.setOnClickListener { openGallery() }
        editPicButton.setOnClickListener { openGallery() }

        // 4. Lógica del botón GUARDAR
        guardarButton.setOnClickListener {
            val nombre = nombreEditText.text.toString().trim()
            val edad = edadEditText.text.toString().trim()
            val sexoId = sexoToggleGroup.checkedButtonId
            val pais = ccpPais.selectedCountryNameCode // Obtiene código ISO (Ej: ES, MX)

            // Validación: No permitir campos vacíos
            if (nombre.isEmpty() || edad.isEmpty() || sexoId == -1) {
                Toast.makeText(context, "Por favor, rellena todos los campos.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Obtener el texto del botón seleccionado (Hombre/Mujer)
            val sexo = view.findViewById<Button>(sexoId).text.toString()

            // Si el usuario eligió una foto nueva, primero se sube a Cloudinary
            if (selectedImageUri != null) {
                uploadImageToCloudinary(nombre, sexo, edad, pais)
            } else {
                // Si no hay foto nueva, se guardan los datos directamente mediante el listener
                listener?.onProfileSaved(nombre, sexo, edad, pais, null)
                dismiss() // Cierra el diálogo
            }
        }

        // Lógica del botón CANCELAR (simplemente cierra el pop-up)
        cancelarButton.setOnClickListener { dismiss() }
    }

    /**
     * Consulta Firestore para obtener la información actual y mostrarla en los campos del diálogo.
     */
    private fun loadCurrentProfileData() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val nombre = document.getString("nombre")
                    val edad = document.getString("edad")
                    val sexo = document.getString("sexo")
                    val pais = document.getString("pais")
                    val fotoUrl = document.getString("fotoUrl")

                    nombre?.let { nombreEditText.setText(it) }
                    edad?.let { edadEditText.setText(it) }

                    // Establece el país en el selector CountryCodePicker
                    pais?.let { ccpPais.setCountryForNameCode(it) }

                    // Marca el botón de género correspondiente
                    sexo?.let {
                        if (it == "Hombre") sexoToggleGroup.check(R.id.btn_masculino)
                        else if (it == "Mujer") sexoToggleGroup.check(R.id.btn_femenino)
                    }

                    // Carga la foto de perfil usando la librería Glide
                    if (fotoUrl != null && context != null) {
                        Glide.with(requireContext()).load(fotoUrl).into(profilePicImageView)
                    }
                }
            }
    }

    /**
     * Recibe el resultado de la galería cuando el usuario selecciona una imagen.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            // Muestra una vista previa de la imagen seleccionada localmente
            profilePicImageView.setImageURI(selectedImageUri)
        }
    }

    /**
     * Proceso de subida de imagen a Cloudinary mediante peticiones HTTP (OkHttp).
     */
    private fun uploadImageToCloudinary(nombre: String, sexo: String, edad: String, pais: String) {
        // Convierte el URI de la imagen en un flujo de bytes para enviarlo por red
        val inputStream = context?.contentResolver?.openInputStream(selectedImageUri!!)
        val requestBody = inputStream?.readBytes()?.let { bytes ->
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "profile_${auth.currentUser?.uid}.jpg",
                    RequestBody.create("image/*".toMediaTypeOrNull(), bytes)
                )
                .addFormDataPart("upload_preset", "android_uploads") // Preset configurado en Cloudinary
                .build()
        } ?: return

        // Construcción de la petición POST a la API de Cloudinary
        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/dcryq1boy/image/upload")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        // Ejecución asíncrona de la subida
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Manejo de error de red
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error al subir la foto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Si la subida es exitosa, obtenemos la URL pública (secure_url) del JSON de respuesta
                    val responseBody = response.body?.string()
                    val photoUrl = JSONObject(responseBody).getString("secure_url")

                    // Volvemos al hilo principal para avisar al listener y cerrar el diálogo
                    activity?.runOnUiThread {
                        listener?.onProfileSaved(nombre, sexo, edad, pais, photoUrl)
                        dismiss()
                    }
                }
            }
        })
    }
}