// User.kt

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val uid: String = "",
    val nombre: String = "",
    val email: String = "",
    val sexo: String? = null,
    val edad: String? = null,
    val fotoUrl: String? = null,
    val pais: String = "",
    val amigos: Int = 0,
    var isFriend: Boolean = false,
    var hasSentRequest: Boolean = false,
    var hasReceivedRequest: Boolean = false,
    var mutualFriendsCount: Int = 0
) : Parcelable