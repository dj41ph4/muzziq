package com.muzziq.mobile.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.muzziq.mobile.BuildConfig
import com.muzziq.mobile.data.ApiClientFactory
import com.muzziq.mobile.data.model.AndroidUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Mise à jour automatique auto-hébergée (§56.3) — MuzziQ n'est pas distribué
 * sur le Play Store. Vérification périodique/au lancement d'une version
 * distante, jamais de blocage forcé, jamais d'installation silencieuse
 * (Android l'interdit de toute façon sans REQUEST_INSTALL_PACKAGES accordé
 * explicitement par l'utilisateur à l'installation).
 *
 * Ne s'applique qu'en mode Lié : en standalone il n'y a pas de serveur MuzziQ
 * pour servir /api/updates/android — l'app reste sur son mécanisme de mise à
 * jour manuelle (réinstallation d'un APK récupéré ailleurs par l'utilisateur).
 */
class UpdateChecker(private val context: Context) {

    suspend fun checkForUpdate(baseUrl: String): AndroidUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val api = ApiClientFactory.create(baseUrl)
            val res = api.androidUpdate()
            if (!res.isSuccessful) return@withContext null
            val info = res.body() ?: return@withContext null
            if (info.latestVersionCode > BuildConfig.VERSION_CODE) info else null
        }.getOrNull()
    }

    /** Télécharge l'APK en arrière-plan vers le cache appli, retourne le fichier local. */
    suspend fun download(apkUrl: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "muzziq-update.apk")
        val client = OkHttpClient()
        val request = Request.Builder().url(apkUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Téléchargement échoué (${response.code})")
            val body = response.body ?: error("Réponse vide")
            val total = body.contentLength()
            var written = 0L
            target.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress(written.toFloat() / total.toFloat())
                    }
                }
            }
        }
        target
    }

    /** Lance l'écran d'installation système — confirmation utilisateur obligatoire,
     * jamais une installation silencieuse (règle "explicit permission required"). */
    fun promptInstall(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
