package com.muzziq.mobile.domain

import com.muzziq.mobile.core.capabilities.MuzziQCapabilities
import com.muzziq.mobile.core.capabilities.ServerConnectionState
import com.muzziq.mobile.data.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Découpage de `MusicSource` (data/MusicSource.kt) en contrats séparés par
 * responsabilité, comme demandé pour préparer l'architecture "autonomous-first"
 * (téléphone capable seul, serveur = accélérateur optionnel de capacités).
 *
 * État réel : `AppViewModel.refreshLibrary()`/`search()` consomment maintenant
 * ces contrats via `ProviderRegistry` (domain/ProviderRegistry.kt) plutôt que
 * `MusicSource` directement — première migration réelle, pas seulement un
 * pont théorique. `PlaybackService` (résolution de flux ExoPlayer + recherche
 * vocale/browse Android Auto) continue en revanche d'utiliser `MusicSource`
 * via `MusicSourceLocator` tel quel : c'est la surface la plus risquée à
 * faire migrer sans appareil réel pour la vérifier (MediaLibraryService,
 * cycle de vie indépendant de l'Activity) — voir le commentaire de tête de
 * ProviderRegistry.kt. Migration progressive assumée, pas un remplacement
 * en un coup.
 */

/** Recherche/consultation du catalogue (§47) — catalogue serveur (YouTube Music + local)
 * en mode Lié, bibliothèque locale uniquement en standalone (pas de "catalogue" distinct
 * de la bibliothèque tant qu'aucune découverte en ligne n'existe hors serveur). Un
 * catalogue YouTube Music résolu directement sur l'appareil (sans serveur) n'existe pas
 * et n'est pas commencé — voir android-mobile/docs/online-streaming-status.md pour le
 * blocage réel (déchiffrement de signature non résolu, ni côté serveur ni donc ici). */
interface CatalogueProvider {
    suspend fun search(query: String): Result<List<Track>>
}

/** Résolution d'une source jouable réelle pour ExoPlayer (§12) — jamais un flux fabriqué. */
interface StreamResolver {
    suspend fun resolvePlayableUri(track: Track): Result<String>
}

/** Bibliothèque possédée/suivie par l'utilisateur (§18), distincte du catalogue. */
interface LibraryRepository {
    suspend fun library(): Result<List<Track>>
}

data class PlaylistSummary(val id: String, val name: String, val itemCount: Int)

/**
 * Playlists (plan §6/§66) — standalone (Room, RoomPlaylistRepository) et mode Lié
 * (serveur, ServerPlaylistRepository) sont deux implémentations réelles et complètes,
 * pas juste un contrat posé sans classe (contrairement à Lyrics/Recommendation
 * ci-dessous). Contrat volontairement plus riche qu'un simple `List<String>` de noms —
 * la version précédente ne permettait ni de lister le contenu d'une playlist, ni d'y
 * ajouter/retirer un morceau, donc rien d'utile à construire dessus.
 */
interface PlaylistRepository {
    suspend fun playlists(): Result<List<PlaylistSummary>>
    suspend fun createPlaylist(name: String): Result<PlaylistSummary>
    suspend fun deletePlaylist(playlistId: String): Result<Unit>
    suspend fun playlistTracks(playlistId: String): Result<List<Track>>
    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit>
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit>
}

/** Implémenté par RoomFavoriteRepository (Room, local à l'appareil, mêmes deux modes) —
 * pas de synchronisation serveur (le contrat serveur, `/api/library/items` + addPolicy,
 * n'est pas un concept de favori au sens strict). */
interface FavoriteRepository {
    suspend fun isFavorite(trackId: String): Boolean
    suspend fun setFavorite(trackId: String, favorite: Boolean)
    fun observeFavorites(): Flow<List<String>>
}

/** Historique d'écoute (§41) — seule implémentation réelle existante aujourd'hui est
 * `StandaloneMusicSource.recordPlayback` (SQLite local) ; voir MusicSourceHistoryAdapter
 * plus bas. Le serveur ne reçoit aucun événement d'écoute depuis Android pour l'instant
 * (pas de route dédiée consommée) — limite honnête, pas cachée. */
interface HistoryRepository {
    fun recordPlayback(track: Track, positionMs: Long, durationMs: Long)
}

/** Pas implémenté — ni le serveur (aucune route /api/lyrics) ni Android n'ont de
 * fournisseur de paroles aujourd'hui (plan §38, jamais commencé côté MuzziQ). */
interface LyricsProvider {
    suspend fun lyricsFor(track: Track): Result<String?>
}

/** Pas implémenté — le moteur de recommandation (déterministe ou IA, plan §44) n'existe
 * ni côté serveur ni côté Android à ce jour. */
interface RecommendationProvider {
    suspend fun recommendationsFor(seed: Track?): Result<List<Track>>
}

/** Téléchargement hors-ligne (plan §57, DeviceOfflineItem) — deux implémentations réelles :
 * StandaloneDownloadRepository (un morceau local EST déjà téléchargé par définition) et
 * ServerDownloadRepository (rapatrie réellement les octets d'un morceau serveur). */
interface DownloadRepository {
    suspend fun downloadedTrackIds(): Result<List<String>>
    suspend fun requestDownload(track: Track): Result<Unit>
}

/** Capacités négociées avec le serveur (voir core/capabilities/ServerCapabilities.kt) —
 * déjà réellement câblé dans AppViewModel via AppPrefs.serverConnectionState +
 * CapabilityManager ; cette interface expose juste ce même état sous forme de contrat
 * consommable indépendamment de AppViewModel. */
interface ServerCapabilityProvider {
    fun observeCapabilities(): Flow<MuzziQCapabilities>
    fun observeConnectionState(): Flow<ServerConnectionState>
}
