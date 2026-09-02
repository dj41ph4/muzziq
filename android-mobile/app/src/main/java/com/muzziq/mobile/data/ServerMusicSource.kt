package com.muzziq.mobile.data

import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource

/**
 * Mode Lié (§56.4, §56) — parle uniquement à l'API MuzziQ, jamais directement à un
 * provider (INTERDIT 10 côté serveur, respecté ici en ne consommant que /api/*).
 * Le serveur reste maître du catalogue, de l'identité, des recommandations.
 */
class ServerMusicSource(
    private val baseUrl: String,
    private val cookie: String?,
) : MusicSource {
    override val label: String = "Serveur MuzziQ"
    private val api = ApiClientFactory.create(baseUrl)

    override suspend fun health(): Boolean = runCatching { api.health().isSuccessful }.getOrDefault(false)

    override suspend fun library(): Result<List<Track>> = runCatching {
        val res = api.libraryItems(cookie)
        if (!res.isSuccessful) error("Bibliothèque indisponible (${res.code()})")
        res.body()?.items.orEmpty().mapNotNull { item ->
            val rec = item.recording ?: return@mapNotNull null
            Track(
                id = rec.id,
                title = rec.title,
                artist = rec.artist,
                album = rec.album,
                durationSeconds = rec.durationSeconds,
                artworkUrl = rec.thumbnailUrl,
                source = TrackSource.Server(rec.id),
            )
        }
    }

    override suspend fun search(query: String): Result<List<Track>> = runCatching {
        val res = api.search(query, cookie)
        if (!res.isSuccessful) error("Recherche indisponible (${res.code()})")
        res.body()?.tracks.orEmpty().map { t ->
            Track(
                id = t.providerTrackId,
                title = t.title,
                artist = t.artist,
                album = t.album,
                durationSeconds = t.durationSeconds,
                artworkUrl = t.thumbnailUrl,
                // Résultat de recherche brut = providerTrackId, pas encore un recordingId
                // MuzziQ (§7) : la résolution passe par /api/play/{trackId} directement,
                // comme le fait le web (voir src/app/api/play/[trackId]/route.ts).
                source = TrackSource.Server(t.providerTrackId),
            )
        }
    }

    override suspend fun resolvePlayableUri(track: Track): Result<String> = runCatching {
        val recordingId = (track.source as? TrackSource.Server)?.recordingId
            ?: error("Source de piste invalide")

        // Playback Resolver serveur (§12) : le client demande "joue cet id",
        // jamais une URL de provider construite lui-même (§14).
        val resolveRes = runCatching { api.resolveRecording(recordingId) }.getOrNull()
        val resolved = resolveRes?.takeIf { it.isSuccessful }?.body()

        if (resolved != null) {
            return@runCatching when (resolved.kind) {
                "local" -> ApiClientFactory.streamUrl(baseUrl, resolved.id)
                else -> {
                    val playRes = api.resolvePlayback(resolved.id)
                    if (!playRes.isSuccessful) error("Lecture indisponible (${playRes.code()})")
                    playRes.body()?.url ?: error("Aucune source de lecture")
                }
            }
        }

        // Résultat de recherche direct (pas encore un Recording connu du serveur) :
        // recordingId est en réalité le providerTrackId brut ici.
        val playRes = api.resolvePlayback(recordingId)
        if (!playRes.isSuccessful) error("Lecture indisponible (${playRes.code()})")
        playRes.body()?.url ?: error("Aucune source de lecture")
    }

    override suspend fun playlists(): Result<List<String>> = runCatching {
        val res = api.playlists(cookie)
        if (!res.isSuccessful) error("Playlists indisponibles (${res.code()})")
        res.body()?.playlists.orEmpty().map { it.name }
    }
}
