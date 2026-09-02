package com.muzziq.mobile.domain

import com.muzziq.mobile.core.capabilities.CapabilityManager
import com.muzziq.mobile.core.capabilities.MuzziQCapabilities
import com.muzziq.mobile.core.capabilities.ServerConnectionState
import com.muzziq.mobile.data.AppPrefs
import com.muzziq.mobile.data.MusicSource
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.standalone.StandaloneMusicSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Adaptateurs `MusicSource` → interfaces étroites (domain/Repositories.kt).
 * Délèguent entièrement à l'implémentation `MusicSource` existante (Server ou
 * Standalone) — aucune logique dupliquée, juste un contrat plus étroit pour
 * les consommateurs qui n'ont besoin que d'une partie de `MusicSource`.
 */
class MusicSourceCatalogueAdapter(private val source: MusicSource) : CatalogueProvider {
    override suspend fun search(query: String): Result<List<Track>> = source.search(query)
}

class MusicSourceStreamResolverAdapter(private val source: MusicSource) : StreamResolver {
    override suspend fun resolvePlayableUri(track: Track): Result<String> = source.resolvePlayableUri(track)
}

class MusicSourceLibraryAdapter(private val source: MusicSource) : LibraryRepository {
    override suspend fun library(): Result<List<Track>> = source.library()
}

// L'ancien MusicSourcePlaylistAdapter (délégant à MusicSource.playlists(): Result<List<String>>)
// a été retiré : ce contrat ne permettait ni de lister le contenu d'une playlist ni d'y
// ajouter/retirer un morceau. RoomPlaylistRepository/ServerPlaylistRepository (fichiers
// dédiés) implémentent maintenant PlaylistRepository directement et réellement.

/** Seule implémentation réelle de HistoryRepository aujourd'hui — le mode Lié n'envoie
 * aucun événement de lecture au serveur depuis Android (pas de route consommée pour ça). */
class StandaloneHistoryAdapter(private val standalone: StandaloneMusicSource) : HistoryRepository {
    override fun recordPlayback(track: Track, positionMs: Long, durationMs: Long) {
        standalone.recordPlayback(track, positionMs, durationMs)
    }
}

/** Câblé sur AppPrefs + CapabilityManager. Attention, limite réelle : contrairement à
 * `AppViewModel.capabilities` (qui interroge `/api/capabilities` via `refreshServerCapabilities()`),
 * cet adaptateur n'a accès qu'à `ServerConnectionState`, jamais aux vraies capacités
 * négociées — `CapabilityManager.forConnection()` renvoie donc toujours la valeur par
 * défaut (aucune capacité serveur) ici, quel que soit l'état de connexion. Utile pour un
 * consommateur qui n'a besoin que de l'état de connexion ; à ne PAS utiliser pour de
 * vraies capacités tant qu'il n'est pas branché sur la même réponse serveur qu'AppViewModel. */
class AppPrefsCapabilityProvider(
    private val prefs: AppPrefs,
    private val capabilityManager: CapabilityManager = CapabilityManager(),
) : ServerCapabilityProvider {
    override fun observeConnectionState(): Flow<ServerConnectionState> = prefs.serverConnectionState

    override fun observeCapabilities(): Flow<MuzziQCapabilities> =
        prefs.serverConnectionState.map { capabilityManager.forConnection(it) }
}
