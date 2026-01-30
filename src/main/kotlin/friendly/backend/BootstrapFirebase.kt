package friendly.backend

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.FileInputStream

fun impureBootstrapFirebase(): FirebaseContext? {
    val fileSystem = SystemFileSystem
    val googleServices = System.getenv("FRIENDLY_GOOGLE_SERVICES")
    if (googleServices == null) {
        return null
    }
    val googleServicesPath = Path(googleServices)
    if (!fileSystem.exists(googleServicesPath)) {
        return null
    }
    val fileInputStream = FileInputStream(googleServices)
    val credentials = GoogleCredentials.fromStream(fileInputStream)
    val options = FirebaseOptions.builder()
        .setCredentials(credentials)
        .build()
    val app = FirebaseApp.initializeApp(options)
    return FirebaseContext(app)
}
