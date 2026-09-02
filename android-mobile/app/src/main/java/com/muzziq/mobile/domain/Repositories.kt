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
 * État réel : ces interfaces sont neuves et ne remplacent PAS encore
 * `MusicSource` — `AppViewModel`/`PlaybackService` continuent d'utiliser
 * `MusicSource` tel quel, c'est ce qui compile et fonctionne aujourd'hui.
 * Les adaptateurs de ce fichier (`MusicSourceCatalogueAdapter`, etc.) montrent
 * comment consommer un `MusicSource` existant à travers ces contrats plus
 * étroits, sans dupliquer de logique — première étape d'une migration
 * progressive, pas un remplacement en un coup. La bascule complète de
 * AppViewModel/PlaybackService vers ces interfaces reste un chantier suivant.
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

interface PlaylistRepository {
    suspend fun playlists(): Result<List<String>>
}

/** Pas encore implémenté nulle part (ni Room ni serveur consommé côté Android) —
 * contrat posé pour la prochaine étape, aucune classe ne l'implémente ici. Le
 * server contract existe (`/api/library/items` avec addPolicy) mais Android ne
 * l'expose pas encore comme "favori" au sens strict. */
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

/** Pas implémenté — le concept de "téléchargement offline d'un morceau distant"
 * (plan §57, DeviceOfflineItem) n'existe pas encore ; en standalone, tout morceau de
 * la bibliothèque locale est déjà un fichier sur l'appareil (rien à télécharger). */
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
