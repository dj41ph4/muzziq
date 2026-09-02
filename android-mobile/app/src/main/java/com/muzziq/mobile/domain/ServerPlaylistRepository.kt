package com.muzziq.mobile.domain

import com.muzziq.mobile.data.ApiClientFactory
import com.muzziq.mobile.data.model.AddPlaylistItemRequest
import com.muzziq.mobile.data.model.CreatePlaylistRequest
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource

/**
 * Playlists mode Lié — consomme les routes serveur réelles (src/app/api/playlists/*),
 * jamais un contrat inventé (§64) : GET/POST /api/playlists, GET/DELETE /api/playlists/{id},
 * POST /api/playlists/{id}/items, DELETE /api/playlists/{id}/items?itemId=.
 */
class ServerPlaylistRepository(baseUrl: String, private val cookie: String?) : PlaylistRepository {
    private val api = ApiClientFactory.create(baseUrl)

    override suspend fun playlists(): Result<List<PlaylistSummary>> = runCatching {
        val res = api.playlists(cookie)
        if (!res.isSuccessful) error("Playlists indisponibles (${res.code()})")
        res.body()?.playlists.orEmpty().map { PlaylistSummary(it.id, it.name, it.itemCount) }
    }

    override suspend fun createPlaylist(name: String): Result<PlaylistSummary> = runCatching {
        val res = api.createPlaylist(CreatePlaylistRequest(name), cookie)
        if (!res.isSuccessful) error("Création impossible (${res.code()})")
        val p = res.body() ?: error("Réponse vide")
        PlaylistSummary(p.id, p.name, 0)
    }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        val res = api.deletePlaylist(playlistId, cookie)
        if (!res.isSuccessful) error("Suppression impossible (${res.code()})")
    }

    override suspend fun playlistTracks(playlistId: String): Result<List<Track>> = runCatching {
        val res = api.playlistDetail(playlistId, cookie)
        if (!res.isSuccessful) error("Playlist indisponible (${res.code()})")
        val detail = res.body() ?: error("Réponse vide")
        detail.items.mapNotNull { item ->
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

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> = runCatching {
        val recordingId = (track.source as? TrackSource.Server)?.recordingId
            ?: error("Ajout à une playlist impossible pour un morceau local en mode Lié")
        val res = api.addPlaylistItem(playlistId, AddPlaylistItemRequest(recordingId), cookie)
        if (!res.isSuccessful) error("Ajout impossible (${res.code()})")
    }

    /** L'API serveur exige un itemId (DELETE /api/playlists/{id}/items?itemId=...), pas un
     * trackId/recordingId — on re-résout l'item correspondant avant de supprimer plutôt
     * que de garder un cache potentiellement périmé. Deux appels réseau, mais correct. */
    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> = runCatching {
        val detailRes = api.playlistDetail(playlistId, cookie)
        if (!detailRes.isSuccessful) error("Playlist indisponible (${detailRes.code()})")
        val detail = detailRes.body() ?: error("Réponse vide")
        val item = detail.items.firstOrNull { it.recordingId == trackId }
            ?: error("Morceau introuvable dans cette playlist")
        val res = api.deletePlaylistItem(playlistId, item.id, cookie)
        if (!res.isSuccessful) error("Suppression impossible (${res.code()})")
    }
}
