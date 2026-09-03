package com.muzziq.mobile.standalone

import android.content.Context
import com.muzziq.mobile.data.MusicSource
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import com.muzziq.mobile.providers.youtube.YouTubeMusicStandaloneSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mode Standalone : bibliothèque locale et accès direct à YouTube Music depuis
 * Android. Le serveur MuzziQ n'est pas requis pour rechercher ou lire un flux.
 */
class StandaloneMusicSource(context: Context) : MusicSource {
    override val label: String = "Standalone"

    private val scanner = LocalLibraryScanner(context)
    private val db = LocalTasteDatabase(context)
    private val youtube = YouTubeMusicStandaloneSource()

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
        val local = runCatching { db.searchTracks(query).map { it.toTrack() } }.getOrDefault(emptyList())
        val online = youtube.search(query).getOrDefault(emptyList())
        Result.success(local + online)
    }

    override suspend fun resolvePlayableUri(track: Track): Result<String> {
        return when (val source = track.source) {
            is TrackSource.Local -> Result.success(source.contentUri)
            is TrackSource.YouTube -> youtube.resolvePlayableUri(track)
            else -> Result.failure(IllegalArgumentException("Source de piste invalide en standalone"))
        }
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
            "Le mode standalone peut rechercher et lire YouTube Music directement depuis Android."
    }
}

/** Une entrée d'historique — le morceau + le moment de lecture (plan §41). */
data class HistoryEntry(val track: Track, val playedAt: Long)
