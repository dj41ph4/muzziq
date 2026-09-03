package com.muzziq.mobile.domain

import android.content.Context
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import com.muzziq.mobile.data.room.MuzziQDatabase
import com.muzziq.mobile.data.room.PlaylistEntity
import com.muzziq.mobile.data.room.PlaylistItemEntity
import java.util.UUID

/**
 * Playlists standalone (§56.4) — deuxième vrai consommateur du schéma Room posé
 * plus tôt (après RoomFavoriteRepository), fonctionne identiquement en mode
 * Lié et standalone du point de vue de l'utilisateur : créer/nommer une
 * playlist, y ajouter/retirer n'importe quel Track déjà affiché à l'écran,
 * indépendant du catalogue en ligne et du blocage cipher YouTube.
 */
class RoomPlaylistRepository(context: Context) : PlaylistRepository {
    private val dao = MuzziQDatabase.get(context).playlistDao()

    override suspend fun playlists(): Result<List<PlaylistSummary>> = runCatching {
        dao.getAllOnce().map { PlaylistSummary(it.id, it.name, dao.itemCount(it.id), MusicProviderId.LOCAL) }
    }

    override suspend fun createPlaylist(name: String): Result<PlaylistSummary> = runCatching {
        val playlist = PlaylistEntity(id = UUID.randomUUID().toString(), name = name, createdAt = System.currentTimeMillis())
        dao.upsert(playlist)
        PlaylistSummary(playlist.id, playlist.name, 0, MusicProviderId.LOCAL)
    }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        dao.clearItems(playlistId)
        dao.deleteById(playlistId)
    }

    override suspend fun playlistTracks(playlistId: String): Result<List<Track>> = runCatching {
        dao.getItemsOnce(playlistId).map { it.toTrack() }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> = runCatching {
        val position = dao.itemCount(playlistId)
        dao.insertItem(track.toPlaylistItemEntity(playlistId, position))
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> = runCatching {
        dao.removeItem(playlistId, trackId)
    }

    private fun PlaylistItemEntity.toTrack() = Track(
        id = trackId,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        source = when (sourceKind) {
            "LOCAL" -> TrackSource.Local(sourceRef)
            "YOUTUBE" -> TrackSource.YouTube(sourceRef)
            "SPOTIFY" -> TrackSource.Spotify(sourceRef)
            else -> TrackSource.Server(sourceRef)
        },
    )

    private fun Track.toPlaylistItemEntity(playlistId: String, position: Int) = PlaylistItemEntity(
        playlistId = playlistId,
        trackId = id,
        position = position,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        artworkUrl = artworkUrl,
        sourceKind = when (source) {
            is TrackSource.Server -> "SERVER"
            is TrackSource.Local -> "LOCAL"
            is TrackSource.YouTube -> "YOUTUBE"
            is TrackSource.Spotify -> "SPOTIFY"
        },
        sourceRef = when (val s = source) {
            is TrackSource.Server -> s.recordingId
            is TrackSource.Local -> s.contentUri
            is TrackSource.YouTube -> s.videoId
            is TrackSource.Spotify -> s.spotifyTrackId
        },
    )
}
