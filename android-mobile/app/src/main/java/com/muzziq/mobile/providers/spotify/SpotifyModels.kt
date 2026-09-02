package com.muzziq.mobile.providers.spotify

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs Spotify Web API (documentée publiquement par Spotify pour les
 * développeurs tiers — https://developer.spotify.com/documentation/web-api,
 * contrairement à InnerTube YouTube qui n'a aucune documentation officielle).
 * Champs limités à ce que SpotifyProvider consomme réellement, pas une
 * reproduction complète du schéma Spotify.
 */

@JsonClass(generateAdapter = true)
data class SpotifyTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresInSeconds: Int,
    /** Absent sur une réponse de refresh si Spotify ne fait pas tourner le refresh token. */
    @Json(name = "refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
)

@JsonClass(generateAdapter = true)
data class SpotifyImage(val url: String, val height: Int? = null, val width: Int? = null)

@JsonClass(generateAdapter = true)
data class SpotifyArtistRef(val id: String, val name: String)

@JsonClass(generateAdapter = true)
data class SpotifyAlbumRef(
    val id: String,
    val name: String,
    val images: List<SpotifyImage> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistRef> = emptyList(),
    val album: SpotifyAlbumRef? = null,
    @Json(name = "duration_ms") val durationMs: Long = 0,
    val uri: String,
)

@JsonClass(generateAdapter = true)
data class SpotifyPagedTracks(val items: List<SpotifyTrack> = emptyList())

@JsonClass(generateAdapter = true)
data class SpotifySearchResponse(val tracks: SpotifyPagedTracks? = null)

@JsonClass(generateAdapter = true)
data class SpotifySavedTrackItem(val track: SpotifyTrack)

@JsonClass(generateAdapter = true)
data class SpotifySavedTracksResponse(
    val items: List<SpotifySavedTrackItem> = emptyList(),
    val next: String? = null,
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistTracksRef(val total: Int = 0)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistOwner(@Json(name = "display_name") val displayName: String? = null)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val owner: SpotifyPlaylistOwner? = null,
    val tracks: SpotifyPlaylistTracksRef = SpotifyPlaylistTracksRef(),
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylist> = emptyList(),
    val next: String? = null,
)

/** [track] nullable : Spotify renvoie un item null pour un morceau retiré du catalogue
 * (local file, morceau supprimé) — à ignorer, jamais planter dessus. */
@JsonClass(generateAdapter = true)
data class SpotifyPlaylistTrackItem(val track: SpotifyTrack? = null)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistTrackItem> = emptyList(),
    val next: String? = null,
)

@JsonClass(generateAdapter = true)
data class SpotifyMeResponse(val id: String, @Json(name = "display_name") val displayName: String? = null)
