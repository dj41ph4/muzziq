package com.muzziq.mobile.providers.spotify

import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import com.muzziq.mobile.domain.CatalogueProvider
import com.muzziq.mobile.domain.LibraryRepository
import com.muzziq.mobile.domain.PlaylistRepository
import com.muzziq.mobile.domain.PlaylistSummary
import com.muzziq.mobile.domain.StreamResolver

/**
 * Provider Spotify (plan §67, priorité 5) — catalogue/bibliothèque/playlists en
 * LECTURE SEULE uniquement, via la Web API officielle Spotify. Pas encore
 * assemblé en `domain.MusicProvider`/enregistré dans `ProviderRegistry` ici :
 * ce chantier attend l'écran Réglages qui pilotera la connexion (bouton
 * "Connecter Spotify" → Custom Tab → callback), pas fait dans cette passe
 * (voir SpotifyAuthManager, commentaire de tête). Ce fichier est complet et
 * autonome, prêt à être branché une fois ce câblage écrit.
 *
 * [streamResolver] échoue TOUJOURS explicitement : la Web API Spotify ne
 * fournit à aucun tiers d'URL de flux audio jouable (contrairement à
 * l'InnerTube "privé" de YouTube, l'absence ici est une limite documentée et
 * intentionnelle de l'API officielle, pas un blocage technique à contourner).
 * Écouter un morceau Spotify réellement nécessiterait le Spotify App Remote
 * SDK (contrôle à distance de l'app Spotify installée, pas un flux MuzziQ/
 * Media3) — hors périmètre de cette V1, pas commencé.
 */
class SpotifyProvider(
    private val authManager: SpotifyAuthManager,
) : CatalogueProvider, LibraryRepository, StreamResolver, PlaylistRepository {

    private suspend fun <T> withBearer(block: suspend (String) -> Result<T>): Result<T> {
        val token = authManager.validAccessToken().getOrElse { return Result.failure(it) }
        return block("Bearer $token")
    }

    override suspend fun search(query: String): Result<List<Track>> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.search(query, bearer)
            if (!res.isSuccessful) error("Recherche Spotify indisponible (${res.code()})")
            res.body()?.tracks?.items.orEmpty().map { it.toTrack() }
        }
    }

    /** "Bibliothèque" Spotify = morceaux mis en Favoris ("Titres likés") — premier page
     * uniquement (50 morceaux, limite Spotify par appel) : pas de pagination complète
     * dans cette V1, limitation honnête plutôt qu'un appel bloquant en boucle non borné. */
    override suspend fun library(): Result<List<Track>> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.savedTracks(bearer)
            if (!res.isSuccessful) error("Bibliothèque Spotify indisponible (${res.code()})")
            res.body()?.items.orEmpty().map { it.track.toTrack() }
        }
    }

    override suspend fun resolvePlayableUri(track: Track): Result<String> =
        Result.failure(
            UnsupportedOperationException(
                "La Web API Spotify ne fournit pas de flux audio jouable à une app tierce " +
                    "(le SDK App Remote serait nécessaire, pas implémenté)",
            ),
        )

    override suspend fun playlists(): Result<List<PlaylistSummary>> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.myPlaylists(bearer)
            if (!res.isSuccessful) error("Playlists Spotify indisponibles (${res.code()})")
            res.body()?.items.orEmpty().map { PlaylistSummary(it.id, it.name, it.tracks.total) }
        }
    }

    override suspend fun playlistTracks(playlistId: String): Result<List<Track>> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.playlistTracks(playlistId, bearer)
            if (!res.isSuccessful) error("Contenu de playlist Spotify indisponible (${res.code()})")
            res.body()?.items.orEmpty().mapNotNull { it.track?.toTrack() }
        }
    }

    // Lecture seule assumée (règle du plan : "playlists en lecture au minimum" pour
    // cette V1) — échec explicite plutôt qu'un bouton qui prétendrait créer/modifier une
    // playlist Spotify sans jamais appeler la moindre route d'écriture.
    override suspend fun createPlaylist(name: String): Result<PlaylistSummary> =
        Result.failure(UnsupportedOperationException("Création de playlist Spotify non supportée (lecture seule)"))

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Suppression de playlist Spotify non supportée (lecture seule)"))

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> =
        Result.failure(UnsupportedOperationException("Modification de playlist Spotify non supportée (lecture seule)"))

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Modification de playlist Spotify non supportée (lecture seule)"))

    private fun SpotifyTrack.toTrack(): Track {
        val bestArtwork = album?.images?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
        return Track(
            id = id,
            title = name,
            artist = artists.joinToString(", ") { it.name }.ifBlank { "Artiste inconnu" },
            album = album?.name,
            durationSeconds = durationMs / 1000.0,
            artworkUrl = bestArtwork,
            source = TrackSource.Spotify(id),
        )
    }
}
