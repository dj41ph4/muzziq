package com.muzziq.mobile.standalone

import android.content.Context
import com.muzziq.mobile.data.MusicSource
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mode Standalone (§56.4) — capacité première de l'app, pas un repli. Aucune
 * dépendance réseau : bibliothèque MediaStore, lecture directe des content://
 * URIs par ExoPlayer, moteur de goût local (LocalTasteDatabase). Aussi complet
 * que ServerMusicSource pour ce que le device peut faire seul — la seule
 * limite honnête est l'absence de recherche/streaming YouTube Music (bloqué
 * PoToken sans repli yt-dlp possible sur Android, voir §56.4 du plan et le
 * message affiché par isStreamingUnavailable()).
 */
class StandaloneMusicSource(context: Context) : MusicSource {
    override val label: String = "Bibliothèque locale"

    private val scanner = LocalLibraryScanner(context)
    private val db = LocalTasteDatabase(context)

    /** À appeler après l'octroi de la permission READ_MEDIA_AUDIO — remplit le cache SQLite
     * depuis MediaStore. Idempotent, peut être relancé (pull-to-refresh bibliothèque). */
    suspend fun rescan(): Int = withContext(Dispatchers.IO) {
        val rows = scanner.scan()
        db.replaceLibrary(rows)
        rows.size
    }

    override suspend fun health(): Boolean = true // standalone : toujours "en ligne", rien à joindre

    override suspend fun library(): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching { db.listTracks().map { it.toTrack() } }
    }

    override suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        runCatching { db.searchTracks(query).map { it.toTrack() } }
    }

    override suspend fun resolvePlayableUri(track: Track): Result<String> {
        val uri = (track.source as? TrackSource.Local)?.contentUri
            ?: return Result.failure(IllegalArgumentException("Source de piste invalide en standalone"))
        return Result.success(uri)
    }

    fun recordPlayback(track: Track, positionMs: Long, durationMs: Long) {
        val uri = (track.source as? TrackSource.Local)?.contentUri ?: return
        db.recordPlayEvent(uri, track.artist, positionMs, durationMs)
    }

    suspend fun topAffinityArtists(): List<String> = withContext(Dispatchers.IO) { db.topAffinityArtists() }

    /** Bibliothèque locale complète, exposée pour la synchronisation Standalone → Lié (§56.4). */
    suspend fun rawTracks(): List<LocalTrackRow> = withContext(Dispatchers.IO) { db.listTracks() }

    /** Historique d'écoute (plan §41) — standalone uniquement : [recordPlayback] n'écrit
     * rien pour un morceau serveur (TrackSource.Server), donc le mode Lié n'a aucune
     * entrée ici, jamais une liste fabriquée pour combler l'absence. */
    suspend fun recentHistory(limit: Int = 50): List<HistoryEntry> = withContext(Dispatchers.IO) {
        db.recentPlayEvents(limit).map { row ->
            HistoryEntry(
                track = Track(
                    id = row.contentUri,
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    durationSeconds = row.durationSeconds,
                    artworkUrl = row.albumId?.let { scanner.albumArtUri(it).toString() },
                    source = TrackSource.Local(row.contentUri),
                ),
                playedAt = row.playedAt,
            )
        }
    }

    private fun LocalTrackRow.toTrack() = Track(
        id = contentUri,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = albumId?.let { scanner.albumArtUri(it).toString() },
        source = TrackSource.Local(contentUri),
    )

    companion object {
        /** Message honnête affiché dans l'UI plutôt qu'un bouton Play mort (§56.4). */
        const val STREAMING_UNAVAILABLE_NOTICE =
            "En local, MuzziQ lit ta bibliothèque sur l'appareil. La recherche/lecture " +
                "YouTube Music nécessite un serveur MuzziQ connecté (yt-dlp n'est pas " +
                "disponible sur Android)."
    }
}

/** Une entrée d'historique — le morceau + le moment de lecture (plan §41). */
data class HistoryEntry(val track: Track, val playedAt: Long)
