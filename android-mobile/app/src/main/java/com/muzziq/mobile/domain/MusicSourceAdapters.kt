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

class MusicSourcePlaylistAdapter(private val source: MusicSource) : PlaylistRepository {
    override suspend fun playlists(): Result<List<String>> = source.playlists()
}

/** Seule implémentation réelle de HistoryRepository aujourd'hui — le mode Lié n'envoie
 * aucun événement de lecture au serveur depuis Android (pas de route consommée pour ça). */
class StandaloneHistoryAdapter(private val standalone: StandaloneMusicSource) : HistoryRepository {
    override fun recordPlayback(track: Track, positionMs: Long, durationMs: Long) {
        standalone.recordPlayback(track, positionMs, durationMs)
    }
}

/** Câblé sur AppPrefs + CapabilityManager — même logique déjà utilisée dans AppViewModel,
 * exposée ici comme contrat indépendant pour un consommateur qui n'a pas besoin de tout
 * AppViewModel (ex. PlaybackService, Android Auto). */
class AppPrefsCapabilityProvider(
    private val prefs: AppPrefs,
    private val capabilityManager: CapabilityManager = CapabilityManager(),
) : ServerCapabilityProvider {
    override fun observeConnectionState(): Flow<ServerConnectionState> = prefs.serverConnectionState

    override fun observeCapabilities(): Flow<MuzziQCapabilities> =
        prefs.serverConnectionState.map { capabilityManager.forConnection(it) }
}
