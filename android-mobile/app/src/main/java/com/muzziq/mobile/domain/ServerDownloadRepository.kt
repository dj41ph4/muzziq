package com.muzziq.mobile.domain

import android.content.Context
import com.muzziq.mobile.data.MusicSource
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.room.DownloadEntity
import com.muzziq.mobile.data.room.MuzziQDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Téléchargement hors-ligne d'un morceau serveur (plan §57, DeviceOfflineItem) —
 * la moitié manquante que StandaloneDownloadRepository documentait explicitement
 * comme non faite. Résolution normale via [source] (Playback Resolver serveur,
 * §12 — jamais un flux fabriqué ici), puis rapatriement réel des octets vers le
 * stockage privé de l'app (sandbox standard `filesDir/downloads/`, jamais un
 * chemin arbitraire dérivé d'une entrée non fiable).
 *
 * Une fois un morceau téléchargé, `ServerMusicSource.resolvePlayableUri` (voir
 * ce fichier) le sert directement depuis le disque au lieu de re-résoudre en
 * réseau — c'est ce qui rend un morceau téléchargé réellement lisible hors
 * connexion en mode Lié, pas juste "marqué téléchargé" sans effet.
 */
class ServerDownloadRepository(
    context: Context,
    private val source: MusicSource,
) : DownloadRepository {
    private val dao = MuzziQDatabase.get(context).downloadDao()
    private val filesDir = context.filesDir
    private val http = OkHttpClient()

    override suspend fun downloadedTrackIds(): Result<List<String>> = runCatching {
        dao.getAllOnce().map { it.trackId }
    }

    override suspend fun requestDownload(track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = dao.getForTrack(track.id)
            if (existing != null && File(existing.localPath).exists()) return@runCatching

            val url = source.resolvePlayableUri(track).getOrElse {
                error("Résolution impossible avant téléchargement : ${it.message}")
            }

            val dir = File(filesDir, "downloads").apply { mkdirs() }
            // Nom de fichier dérivé d'un hash de l'id, jamais de l'id/titre bruts comme
            // segment de chemin (même règle de sécurité que le serveur — assainir tout nom
            // dérivé d'une donnée externe avant usage comme chemin).
            val target = File(dir, "${track.id.hashCode()}.audio")

            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Téléchargement échoué (${response.code})")
                val body = response.body ?: error("Réponse vide")
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
            }

            dao.upsert(
                DownloadEntity(
                    trackId = track.id,
                    downloadedAt = System.currentTimeMillis(),
                    localPath = target.absolutePath,
                    sizeBytes = target.length(),
                )
            )
        }
    }
}
