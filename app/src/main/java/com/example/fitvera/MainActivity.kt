package com.example.fitvera

// Importaciones necesarias para UI, Firebase, Google y Biometría
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth // Instancia para autenticación de Firebase
    private lateinit var googleSignInClient: GoogleSignInClient // Cliente para login con Google
    private val RC_SIGN_IN = 100 // Código de petición para el resultado de Google

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // 1. VERIFICACIÓN DE SESIÓN EXISTENTE:
        // Si el usuario ya se logueó antes, saltamos directamente al menú principal.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            irAMainMenu()
            return
        }

        // 2. CARGA DE INTERFAZ: Si no hay sesión, mostramos el layout de login.
        setContentView(R.layout.login)
        inicializarUI()

        // 3. PREPARACIÓN DE BIOMETRÍA:
        // Buscamos si el usuario marcó "Recordar" anteriormente en SharedPreferences.
        val prefs = getSharedPreferences("fitvera", MODE_PRIVATE)
        val emailGuardado = prefs.getString("email", null)
        val passwordGuardado = prefs.getString("password", null)

        // Si existen credenciales guardadas, lanzamos el lector de huellas automáticamente.
        if (!emailGuardado.isNullOrEmpty() && !passwordGuardado.isNullOrEmpty()) {
            iniciarLoginBiometrico()
        }
    }

    // Función para navegar a la siguiente actividad y cerrar la actual
    private fun irAMainMenu() {
        startActivity(Intent(this, MainActivity2::class.java))
        finish()
    }

    private fun inicializarUI() {
        // Validación de seguridad: Comprueba que todos los IDs del XML existen para evitar crashes.
        try {
            val idsAComprobar = listOf(
                R.id.Logo, R.id.campoemail, R.id.campocontraseña, R.id.iniciodesesion2,
                R.id.botoniniciarsesion, R.id.registrarse, R.id.salir, R.id.text_olvido,
                R.id.progressBar, R.id.login_google, R.id.recordar_usuario, R.id.animacion_lottie
            )
            for (id in idsAComprobar) {
                val view = findViewById<View>(id)
                requireNotNull(view) { "FALTA un view con id: ${resources.getResourceEntryName(id)}" }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // CONFIGURACIÓN DE GOOGLE SIGN-IN: Pide el ID de Token y el Email.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Referencias a los componentes de la interfaz (Views)
        val campoUsuario: EditText = findViewById(R.id.campoemail)
        val campoContraseña: EditText = findViewById(R.id.campocontraseña)
        val textoEstado: TextView = findViewById(R.id.iniciodesesion2)
        val botonIniciarSesion: Button = findViewById(R.id.botoniniciarsesion)
        val botonCrearUsuario: Button = findViewById(R.id.registrarse)
        val botonSalir: Button = findViewById(R.id.salir)
        val botonRecuperar: TextView = findViewById(R.id.text_olvido)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        val googleLogin: Button = findViewById(R.id.login_google)
        val recordarCheck: CheckBox = findViewById(R.id.recordar_usuario)
        val animacionLottie: LottieAnimationView = findViewById(R.id.animacion_lottie)

        // Limpia los mensajes de error cuando el usuario empieza a escribir
        campoUsuario.addTextChangedListener(validarCampos(textoEstado))
        campoContraseña.addTextChangedListener(validarCampos(textoEstado))

        // CONFIGURACIÓN DE ANIMACIÓN LOTTIE (Un stickman corriendo)
        animacionLottie.setAnimation(R.raw.stickman_running_lottie)
        animacionLottie.repeatCount = LottieDrawable.INFINITE
        animacionLottie.playAnimation()

        // REGISTRO DE USUARIO NUEVO
        botonCrearUsuario.setOnClickListener {
            val username = campoUsuario.text.toString().trim()
            val password = campoContraseña.text.toString().trim()
            // Se genera un email falso basado en el nombre de usuario (@fitvera.com)
            val email = "$username@fitvera.com"

            if (username.isEmpty() || password.isEmpty()) {
                mostrarMensaje("Rellena todos los campos", textoEstado, false)
                return@setOnClickListener
            }
            if (password.length < 9) {
                mostrarMensaje("Contraseña debe tener al menos 9 caracteres", textoEstado, false)
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    // Si se crea en Auth, guardamos el nombre en la base de datos Firestore
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val userMap = mapOf("nombre" to username)
                    FirebaseFirestore.getInstance().collection("usuarios").document(uid).set(userMap)

                    mostrarMensaje("Usuario registrado exitosamente", textoEstado, true)
                } else {
                    val errorMsg = when (val e = task.exception) {
                        is FirebaseAuthWeakPasswordException -> "Contraseña débil"
                        is FirebaseAuthUserCollisionException -> "Usuario ya registrado"
                        else -> "Error: ${e?.localizedMessage}"
                    }
                    mostrarMensaje(errorMsg, textoEstado, false)
                }
            }
        }

        // INICIO DE SESIÓN MANUAL (LOGIN)
        botonIniciarSesion.setOnClickListener {
            val username = campoUsuario.text.toString().trim()
            val password = campoContraseña.text.toString().trim()
            val email = "$username@fitvera.com"

            if (username.isEmpty() || password.isEmpty()) {
                mostrarMensaje("Rellena todos los campos", textoEstado, false)
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    // GESTIÓN DE CREDENCIALES GUARDADAS:
                    val prefs = getSharedPreferences("fitvera", MODE_PRIVATE)
                    if (recordarCheck.isChecked) {
                        // Guardamos email y pass para el login biométrico futuro
                        prefs.edit().putString("email", email).putString("password", password).apply()
                    } else {
                        prefs.edit().clear().apply() // Limpiamos si no quiere ser recordado
                    }
                    irAMainMenu()
                } else {
                    val errorMsg = when (val e = task.exception) {
                        is FirebaseAuthInvalidUserException -> "El usuario no existe"
                        is FirebaseAuthInvalidCredentialsException -> "Contraseña incorrecta"
                        else -> "Error: ${e?.localizedMessage}"
                    }
                    mostrarMensaje(errorMsg, textoEstado, false)
                }
            }
        }

        // RECUPERACIÓN DE CONTRASEÑA
        botonRecuperar.setOnClickListener {
            val username = campoUsuario.text.toString().trim()
            val email = "$username@fitvera.com"
            if (username.isEmpty()) {
                mostrarMensaje("Introduce tu nombre de usuario", textoEstado, false)
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email).addOnSuccessListener {
                mostrarMensaje("Correo de recuperación enviado", textoEstado, true)
            }.addOnFailureListener {
                mostrarMensaje("Error al enviar recuperación: ${it.localizedMessage}", textoEstado, false)
            }
        }

        // LOGIN CON GOOGLE
        googleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // BOTÓN SALIR: Muestra un diálogo de confirmación antes de cerrar la app.
        botonSalir.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Salir")
                .setMessage("¿Deseas salir de la aplicación?")
                .setPositiveButton("Sí") { _, _ -> finishAffinity() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // Helper para mostrar Toasts y cambiar el color del texto de estado
    private fun mostrarMensaje(mensaje: String, textoEstado: TextView, esExito: Boolean) {
        textoEstado.text = mensaje
        textoEstado.setTextColor(Color.parseColor(if (esExito) "#27ae60" else "#e74c3c"))
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }

    // Escuchador para detectar cambios en los campos de texto
    private fun validarCampos(textoEstado: TextView): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textoEstado.text = "" // Borra el error cuando el usuario escribe
            }
            override fun afterTextChanged(s: Editable?) {}
        }
    }

    // Resultado de la ventana de selección de cuenta de Google
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Error en inicio de sesión con Google: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Vincula la cuenta de Google con Firebase
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                // Si es un usuario nuevo, creamos su perfil en Firestore
                if (task.result?.additionalUserInfo?.isNewUser == true) {
                    val userMap = mapOf("nombre" to (user?.displayName ?: "GoogleUser"))
                    FirebaseFirestore.getInstance().collection("usuarios").document(user?.uid ?: "").set(userMap)
                }
                irAMainMenu()
            } else {
                Toast.makeText(this, "Error autenticando con Firebase: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * AUTENTICACIÓN BIOMÉTRICA (HUELLA DACTILAR)
     * Solo disponible en Android Pie (API 28) o superior.
     */
    private fun iniciarLoginBiometrico() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val cancellationSignal = android.os.CancellationSignal()
            val executor: Executor = ContextCompat.getMainExecutor(this)

            // Configuramos el cuadro de diálogo del sistema para la huella
            val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
                .setTitle("Login rápido")
                .setSubtitle("Usa tu huella para entrar")
                .setNegativeButton("Usar otra cuenta", executor) { _, _ -> }
                .build()

            prompt.authenticate(
                cancellationSignal,
                executor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)

                        // Si la huella es correcta, recuperamos los datos guardados y logueamos en Firebase
                        val prefs = getSharedPreferences("fitvera", MODE_PRIVATE)
                        val email = prefs.getString("email", null)
                        val password = prefs.getString("password", null)

                        if (!email.isNullOrEmpty() && !password.isNullOrEmpty()) {
                            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
                                if (it.isSuccessful) {
                                    irAMainMenu()
                                } else {
                                    // Si el login falla (ej. contraseña cambiada), limpiamos la memoria
                                    prefs.edit().clear().apply()
                                    Toast.makeText(this@MainActivity, "Sesión guardada caducada", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                    }
                })
        }
    }
}