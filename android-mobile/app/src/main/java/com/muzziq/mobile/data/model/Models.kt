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

@JsonClass(generateAdapter = true)
data class CreatePlaylistRequest(val name: String)

/** Reflet de GET /api/playlists/{id} — src/app/api/playlists/[id]/route.ts. */
@JsonClass(generateAdapter = true)
data class PlaylistDetailResponse(
    val id: String,
    val name: String,
    val createdAt: String,
    val items: List<PlaylistItemDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PlaylistItemDto(
    val id: String,
    val playlistId: String,
    val recordingId: String,
    val position: Int,
    val addedAt: String,
    val recording: Recording? = null,
)

@JsonClass(generateAdapter = true)
data class AddPlaylistItemRequest(val recordingId: String)

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

/** GET /api/capabilities — négociation de capacités serveur (voir
 * core/capabilities/ServerCapabilities.kt et android-mobile/docs). Reflet
 * fidèle de src/app/api/capabilities/route.ts, jamais un champ inventé côté
 * client qui n'existe pas côté réponse serveur réelle. */
@JsonClass(generateAdapter = true)
data class ServerCapabilitiesResponse(
    val server: ServerInfo,
    val capabilities: ServerCapabilitiesPayload,
)

@JsonClass(generateAdapter = true)
data class ServerInfo(
    val name: String,
    val version: String,
)

@JsonClass(generateAdapter = true)
data class ServerCapabilitiesPayload(
    val flacAcquisition: Boolean = false,
    val torrentAcquisition: Boolean = false,
    val nasLibrary: Boolean = false,
    val monitoring: Boolean = false,
    val automaticUpgrade: Boolean = false,
    val centralSync: Boolean = false,
    val remoteJam: Boolean = false,
    val plexIntegration: Boolean = false,
)

/** Reflet de GET /api/home — src/lib/recommendations/deterministicEngine.ts.
 * Rangées contextuelles (plan §46), déjà côté serveur : Continuer l'écoute,
 * Récemment ajoutés, Parce que vous aimez X. Le serveur n'inclut que les
 * rangées non vides, jamais un placeholder client pour "masquer les
 * sections vides". */
@JsonClass(generateAdapter = true)
data class HomeRowDto(
    val id: String,
    val title: String,
    val recordings: List<Recording> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HomeRowsResponse(val rows: List<HomeRowDto> = emptyList())

/** Reflet de GET /api/artists /api/artists/{id} /api/albums /api/albums/{id} — ne
 * couvre QUE la bibliothèque locale déjà scannée côté serveur, aucune agrégation
 * inventée. id d'artiste = nom en minuscule ; id d'album = "artiste::album" en
 * minuscule (voir la documentation serveur pour le détail). Pas de browse du
 * catalogue YouTube Music (recherche uniquement, voir docs/reverse-engineering). */
@JsonClass(generateAdapter = true)
data class ArtistSummaryDto(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class ArtistsResponse(val artists: List<ArtistSummaryDto> = emptyList())

@JsonClass(generateAdapter = true)
data class ArtistAlbumDto(val title: String, val trackCount: Int = 0)

@JsonClass(generateAdapter = true)
data class ArtistTrackDto(val id: String, val title: String, val album: String? = null, val durationSeconds: Double? = null)

@JsonClass(generateAdapter = true)
data class ArtistDetailResponse(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
    val albums: List<ArtistAlbumDto> = emptyList(),
    val tracks: List<ArtistTrackDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AlbumSummaryDto(
    val id: String,
    val title: String,
    val artist: String,
    val trackCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AlbumsResponse(val albums: List<AlbumSummaryDto> = emptyList())

@JsonClass(generateAdapter = true)
data class AlbumTrackDto(
    val id: String,
    val title: String,
    val trackNumber: Int? = null,
    val durationSeconds: Double? = null,
)

@JsonClass(generateAdapter = true)
data class AlbumDetailResponse(
    val id: String,
    val title: String,
    val artist: String,
    val tracks: List<AlbumTrackDto> = emptyList(),
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

/** Persistance de la file d'attente (plan §40/§57, voir data/QueueStateStore.kt) — un
 * Track complet plutôt qu'un simple id, pour restaurer l'affichage (titre/artiste/
 * pochette) sans dépendre d'un appel réseau au redémarrage de l'app. */
@JsonClass(generateAdapter = true)
data class PersistedTrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Double? = null,
    val artworkUrl: String? = null,
    /** "SERVER" | "LOCAL" | "SPOTIFY" — reflet de TrackSource, jamais une 4e valeur inventée. */
    val sourceKind: String,
    val sourceRef: String,
)

@JsonClass(generateAdapter = true)
data class PersistedQueueState(
    val tracks: List<PersistedTrackDto>,
    val currentIndex: Int,
    val positionMs: Long,
)

sealed interface TrackSource {
    /** Mode Lié — recordingId MuzziQ, résolu au moment du play via /api/recordings/{id}/resolve. */
    data class Server(val recordingId: String) : TrackSource
    /** Mode Standalone — content:// URI MediaStore, jouable directement, aucun réseau. */
    data class Local(val contentUri: String) : TrackSource
    /** Compte Spotify lié (plan §67, priorité 5) — [spotifyTrackId] est l'id catalogue
     * Spotify brut, JAMAIS un id MuzziQ (règle absolue) : un ProviderMapping fait le
     * lien si/quand ce morceau est identifié comme le même Recording qu'une autre
     * source. StreamResolver renvoie un échec explicite pour cette source — la Web
     * API Spotify ne fournit aucune URL de flux audio à un tiers (voir
     * providers/spotify/SpotifyProvider.kt), seulement catalogue/bibliothèque/
     * playlists en lecture. */
    data class Spotify(val spotifyTrackId: String) : TrackSource
}
