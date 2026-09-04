package com.muzziq.mobile.providers.spotify

import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import com.muzziq.mobile.domain.CatalogueProvider
import com.muzziq.mobile.domain.LibraryRepository
import com.muzziq.mobile.domain.MusicProviderId
import com.muzziq.mobile.domain.PlaylistRepository
import com.muzziq.mobile.domain.PlaylistSummary
import com.muzziq.mobile.domain.StreamResolver

/**
 * Provider Spotify (plan §67, priorité 5) — catalogue/bibliothèque/playlists via
 * la Web API officielle Spotify. Les écritures sont explicites, limitées aux
 * actions déclenchées par l'utilisateur, et jamais à un téléchargement de flux.
 * Assemblé en
 * `domain.MusicProvider`/enregistré dans `ProviderRegistry` par AppViewModel
 * (registerSpotifyProvider(), déclenché après un login réussi ou restauré au
 * démarrage si un compte est déjà lié) — voir AppViewModel.handleSpotifyCallback
 * pour le câblage écran Réglages → Custom Tab → callback → ce provider.
 *
 * [streamResolver] échoue TOUJOURS explicitement : la Web API Spotify ne
 * fournit à aucun tiers d'URL de flux audio jouable (contrairement à
 * l'InnerTube "privé" de YouTube, l'absence ici est une limite documentée et
 * intentionnelle de l'API officielle, pas un blocage technique à contourner).
 * Écouter un morceau Spotify réellement nécessiterait le Spotify App Remote
 * SDK (contrôle à distance de l'app Spotify installée, pas un flux MuzziQ/
 * Media3) — hors périmètre de cette V1, pas commencé.
 *
 * Sert aussi de "SpotifyPlaylistRepository" (plan §125, ordre de commits) :
 * plutôt qu'une classe séparée qui n'aurait fait qu'envelopper cette
 * implémentation déjà complète du contrat `PlaylistRepository`, AppViewModel
 * utilise directement cette instance comme source additive de playlists
 * (jamais exclusive à RoomPlaylistRepository/ServerPlaylistRepository — voir
 * AppViewModel.refreshPlaylists()/playlistRepositoryFor()).
 */
/** Identité minimale du compte Spotify lié — alimente `LinkedMusicAccountEntity`
 * (externalUserId/displayName/avatarUrl) au moment de la connexion, jamais fabriquée. */
data class SpotifyProfile(val id: String, val displayName: String?, val avatarUrl: String?)

class SpotifyProvider(
    private val authManager: SpotifyAuthManager,
) : CatalogueProvider, LibraryRepository, StreamResolver, PlaylistRepository {

    private suspend fun <T> withBearer(block: suspend (String) -> Result<T>): Result<T> {
        val token = authManager.validAccessToken().getOrElse { return Result.failure(it) }
        return block("Bearer $token")
    }

    /** Appelé une seule fois juste après l'échange de code réussi (voir AppViewModel.
     * handleSpotifyCallback) : c'est le seul moyen d'obtenir l'identité réelle du compte
     * lié (id/nom/avatar) à écrire dans LinkedMusicAccountEntity — jamais dérivée du
     * seul access token. */
    suspend fun profile(): Result<SpotifyProfile> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.me(bearer)
            if (!res.isSuccessful) error("Profil Spotify indisponible (${res.code()})")
            val body = res.body() ?: error("Réponse Spotify vide")
            SpotifyProfile(
                id = body.id,
                displayName = body.displayName,
                avatarUrl = body.images.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url,
            )
        }
    }

    override suspend fun search(query: String): Result<List<Track>> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.search(query, bearer)
            if (!res.isSuccessful) error("Recherche Spotify indisponible (${res.code()})")
            res.body()?.tracks?.items.orEmpty().map { it.toTrack() }
        }
    }

    /** "Bibliothèque" Spotify = morceaux mis en Favoris ("Titres likés"). */
    override suspend fun library(): Result<List<Track>> = withBearer { bearer ->
        runCatching {
            val tracks = mutableListOf<Track>()
            var offset = 0
            do {
                val res = SpotifyApiClientFactory.web.savedLibrary(bearer, limit = 50, offset = offset)
                if (!res.isSuccessful) error("Bibliothèque Spotify indisponible (${res.code()})")
                val page = res.body()?.items.orEmpty().mapNotNull { it.item?.toTrack() }
                tracks += page
                offset += page.size
            } while (page.size == 50)
            tracks
        }
    }

    suspend fun saveTracks(trackIds: List<String>): Result<Unit> = withBearer { bearer ->
        runCatching {
            trackIds.chunked(40).forEach { chunk ->
                if (chunk.isEmpty()) return@forEach
                val res = SpotifyApiClientFactory.web.saveLibraryItems(bearer, chunk.joinToString(",") { "spotify:track:$it" })
                if (!res.isSuccessful) error("Ajout des favoris Spotify échoué (${res.code()})")
            }
        }
    }

    suspend fun removeTracks(trackIds: List<String>): Result<Unit> = withBearer { bearer ->
        runCatching {
            trackIds.chunked(40).forEach { chunk ->
                if (chunk.isEmpty()) return@forEach
                val res = SpotifyApiClientFactory.web.removeLibraryItems(bearer, chunk.joinToString(",") { "spotify:track:$it" })
                if (!res.isSuccessful) error("Retrait des favoris Spotify échoué (${res.code()})")
            }
        }
    }

    /** Résout prudemment un morceau MuzziQ vers Spotify pour la synchronisation.
     * Une absence de correspondance reste une absence : aucun premier résultat vague
     * n'est ajouté aux favoris ou aux playlists de l'utilisateur. */
    suspend fun resolveSpotifyTrackId(track: Track): Result<String?> = withBearer { bearer ->
        runCatching {
            (track.source as? TrackSource.Spotify)?.spotifyTrackId ?: run {
                val query = "track:${track.title} artist:${track.artist}"
                val res = SpotifyApiClientFactory.web.search(query, bearer, limit = 10)
                if (!res.isSuccessful) error("Recherche Spotify indisponible (${res.code()})")
                val wantedTitle = normalize(track.title)
                val wantedArtist = normalize(track.artist)
                res.body()?.tracks?.items.orEmpty()
                    .firstOrNull { candidate ->
                        normalize(candidate.name) == wantedTitle &&
                            candidate.artists.any { normalize(it.name) == wantedArtist }
                    }
                    ?.id
            }
        }
    }

    override suspend fun resolvePlayableUri(track: Track): Result<String> =
        Result.failure(
            UnsupportedOperationException(
                "La Web API Spotify ne fournit pas de flux audio jouable à une app tierce " +
                    "(le SDK App Remote serait nécessaire, pas implémenté)",
            ),
        )

    /** [PlaylistSummary.provider] = SPOTIFY (§58) : ces playlists ne sont JAMAIS fusionnées
     * avec une playlist Room/serveur de même nom, même approximativement — l'UI
     * (PlaylistsScreen) les affiche distinctement avec un badge de provenance. */
    override suspend fun playlists(): Result<List<PlaylistSummary>> = withBearer { bearer ->
        runCatching {
            val playlists = mutableListOf<PlaylistSummary>()
            var offset = 0
            do {
                val res = SpotifyApiClientFactory.web.myPlaylists(bearer, limit = 50, offset = offset)
                if (!res.isSuccessful) error("Playlists Spotify indisponibles (${res.code()})")
                val page = res.body()?.items.orEmpty().map { PlaylistSummary(it.id, it.name, it.items?.total ?: it.tracks.total, MusicProviderId.SPOTIFY) }
                playlists += page
                offset += page.size
            } while (page.size == 50)
            playlists
        }
    }

    override suspend fun playlistTracks(playlistId: String): Result<List<Track>> = withBearer { bearer ->
        runCatching {
            val tracks = mutableListOf<Track>()
            var offset = 0
            do {
                val res = SpotifyApiClientFactory.web.playlistItems(playlistId, bearer, limit = 50, offset = offset)
                if (!res.isSuccessful) error("Contenu de playlist Spotify indisponible (${res.code()})")
                val page = res.body()?.items.orEmpty().mapNotNull { (it.item ?: it.track)?.toTrack() }
                tracks += page
                offset += page.size
            } while (page.size == 50)
            tracks
        }
    }

    /** Identifiants des playlists réellement possédées par le compte lié. Les
     * playlists simplement suivies ne doivent jamais être modifiées par une sync. */
    suspend fun ownedPlaylistIds(): Result<Set<String>> = withBearer { bearer ->
        runCatching {
            val me = SpotifyApiClientFactory.web.me(bearer).body()?.id
                ?: error("Profil Spotify indisponible")
            val ownedIds = mutableSetOf<String>()
            var offset = 0
            do {
                val res = SpotifyApiClientFactory.web.myPlaylists(bearer, limit = 50, offset = offset)
                if (!res.isSuccessful) error("Playlists Spotify indisponibles (${res.code()})")
                val page = res.body()?.items.orEmpty()
                ownedIds += page.filter { it.owner?.id == me }.map { it.id }
                offset += page.size
            } while (page.size == 50)
            ownedIds
        }
    }

    override suspend fun createPlaylist(name: String): Result<PlaylistSummary> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.createPlaylist(bearer, SpotifyCreatePlaylistRequest(name.trim()))
            if (!res.isSuccessful) error("Création de playlist Spotify échouée (${res.code()})")
            val playlist = res.body() ?: error("Réponse Spotify vide")
            PlaylistSummary(playlist.id, playlist.name, playlist.items?.total ?: playlist.tracks.total, MusicProviderId.SPOTIFY)
        }
    }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.deletePlaylist(playlistId, bearer)
            if (!res.isSuccessful) error("Suppression de playlist Spotify échouée (${res.code()})")
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> = withBearer { bearer ->
        runCatching {
            val spotifyId = (track.source as? TrackSource.Spotify)?.spotifyTrackId
                ?: throw UnsupportedOperationException("Ce morceau n'est pas identifié dans Spotify")
            val res = SpotifyApiClientFactory.web.addPlaylistItems(playlistId, bearer, SpotifyUrisRequest(listOf("spotify:track:$spotifyId")))
            if (!res.isSuccessful) error("Ajout à la playlist Spotify échoué (${res.code()})")
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> = withBearer { bearer ->
        runCatching {
            val res = SpotifyApiClientFactory.web.removePlaylistItems(playlistId, bearer, SpotifyRemoveItemsRequest(listOf(SpotifyPlaylistRemoveItem("spotify:track:$trackId"))))
            if (!res.isSuccessful) error("Retrait de la playlist Spotify échoué (${res.code()})")
        }
    }

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

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
