package com.muzziq.mobile.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Contrats côté client Android — reflet volontairement fidèle de
 * src/lib/contracts/music.ts et des routes réelles sous src/app/api/ côté
 * serveur MuzziQ. Ne jamais inventer un champ qui n'existe pas côté serveur ;
 * étendre le serveur d'abord si un besoin apparaît (plan §64).
 */

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
)

@JsonClass(generateAdapter = true)
data class PublicUser(
    val id: String,
    val username: String,
    val role: String,
)

@JsonClass(generateAdapter = true)
data class MeResponse(
    val user: PublicUser?,
    val setupRequired: Boolean,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val user: PublicUser,
)

@JsonClass(generateAdapter = true)
data class ExternalTrack(
    val providerTrackId: String,
    val provider: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Double? = null,
    val thumbnailUrl: String? = null,
    val localMatch: LocalMatch? = null,
)

@JsonClass(generateAdapter = true)
data class LocalMatch(
    val fileId: String,
    val confidence: Double,
)

@JsonClass(generateAdapter = true)
data class ExternalAlbum(
    val providerAlbumId: String,
    val provider: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class ExternalArtist(
    val providerArtistId: String,
    val provider: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class SearchResult(
    val tracks: List<ExternalTrack> = emptyList(),
    val albums: List<ExternalAlbum> = emptyList(),
    val artists: List<ExternalArtist> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class Recording(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Double? = null,
    val thumbnailUrl: String? = null,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class MediaFile(
    val id: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val trackNumber: Int? = null,
    val durationSeconds: Double? = null,
    val codec: String? = null,
)

@JsonClass(generateAdapter = true)
data class LibraryItem(
    val id: String,
    val recordingId: String,
    val addPolicy: String,
    val addedAt: String,
    val recording: Recording? = null,
)

@JsonClass(generateAdapter = true)
data class LibraryItemsResponse(val items: List<LibraryItem>)

@JsonClass(generateAdapter = true)
data class AddLibraryItemRequest(
    val provider: String,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Double? = null,
    val thumbnailUrl: String? = null,
    val addPolicy: String = "STREAM_ONLY",
)

@JsonClass(generateAdapter = true)
data class Playlist(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PlaylistsResponse(val playlists: List<Playlist>)

/** Réponse de GET /api/recordings/{id}/resolve — jamais un flux fabriqué côté client. */
@JsonClass(generateAdapter = true)
data class ResolvedPlayback(
    val kind: String, // "local" | "provider"
    val id: String,
)

/** Réponse de GET /api/play/{trackId} — contrat PlayableSource (music.ts). */
@JsonClass(generateAdapter = true)
data class PlayableSource(
    val type: String, // LOCAL | CACHE | PROVIDER
    val url: String,
    val expiresAt: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
)

/** GET /api/updates/android — mise à jour auto-hébergée (§56.3). */
@JsonClass(generateAdapter = true)
data class AndroidUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val changelog: String? = null,
)

/**
 * Modèle interne unifié — un morceau jouable, qu'il vienne du serveur (recordingId)
 * ou de la bibliothèque locale standalone (contentUri). C'est ce type que l'UI
 * consomme, jamais un type spécifique à une source (§56.4 : les deux modes doivent
 * être servis par les mêmes écrans).
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Double? = null,
    val artworkUrl: String? = null,
    /** null en mode standalone (pas de vignette réseau) — Coil retombe sur les tags embarqués. */
    val source: TrackSource,
)

sealed interface TrackSource {
    /** Mode Lié — recordingId MuzziQ, résolu au moment du play via /api/recordings/{id}/resolve. */
    data class Server(val recordingId: String) : TrackSource
    /** Mode Standalone — content:// URI MediaStore, jouable directement, aucun réseau. */
    data class Local(val contentUri: String) : TrackSource
}
