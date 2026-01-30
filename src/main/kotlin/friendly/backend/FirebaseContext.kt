package friendly.backend

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

data class FirebaseContext(val app: FirebaseApp) {
    val messaging: FirebaseMessaging
        get() = FirebaseMessaging.getInstance(app)
}
