package com.muzziq.mobile.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Identifiant de provider musical (plan §67, pivot multi-provider cumulatif :
 * Local + YouTube Music compte + Spotify compte + Serveur MuzziQ actifs
 * SIMULTANÉMENT, le serveur devenant un provider parmi d'autres). LOCAL et
 * SERVER sont les deux seuls réellement enregistrés aujourd'hui — AppViewModel
 * reste en mode exclusif hérité d'AppMode.STANDALONE/LINKED (activateStandalone
 * et activateLinked se désenregistrent mutuellement, un seul actif à la fois).
 * YOUTUBE_MUSIC et SPOTIFY sont réservés pour les priorités 3/4/5 du plan
 * (streaming direct puis compte lié) — aucune instance n'existe encore.
 */
enum class MusicProviderId { LOCAL, SERVER, YOUTUBE_MUSIC, SPOTIFY }

/**
 * Un provider musical actif, décomposé selon les contrats étroits de
 * Repositories.kt plutôt qu'un `MusicSource` monolithique — permet à un futur
 * second provider (YTM/Spotify, priorité 3+) de s'enregistrer à côté du
 * serveur/local sans dupliquer d'abstraction. [history] optionnel : seule
 * StandaloneMusicSource a une implémentation réelle aujourd'hui (voir
 * StandaloneHistoryAdapter dans MusicSourceAdapters.kt) — le mode Lié n'envoie
 * aucun événement d'écoute au serveur pour l'instant (limite déjà documentée
 * dans HistoryRepository).
 */
data class MusicProvider(
    val id: MusicProviderId,
    val label: String,
    val catalogue: CatalogueProvider,
    val library: LibraryRepository,
    val streamResolver: StreamResolver,
    val history: HistoryRepository? = null,
)

/**
 * Registre des providers actifs. Remplace progressivement `MusicSourceLocator`
 * (playback/MusicSourceLocator.kt) pour les consommateurs qui n'ont besoin que
 * de catalogue/bibliothèque (AppViewModel.refreshLibrary/search, migrés ici).
 *
 * `MusicSourceLocator` reste en place et continue d'être la seule source
 * consommée par `PlaybackService` (résolution de flux ExoPlayer + recherche
 * vocale/browse Android Auto) : c'est la surface la plus risquée de l'app
 * (MediaLibraryService, cycle de vie indépendant de l'Activity) et la seule
 * qu'aucun appareil réel ne permet de vérifier ici (règle §4 du dépôt — le
 * typecheck/CI ne prouve pas un comportement Android Auto réel). La faire
 * migrer sans pouvoir la tester en conditions réelles serait exactement le
 * genre de "succès fabriqué" interdit — chantier suivant, pas celui-ci.
 *
 * État réel aujourd'hui : au plus UN provider actif à la fois (mode exclusif
 * hérité). La structure en liste/Map prépare la cumulativité (§67) sans
 * prétendre qu'elle existe déjà côté lecture/Android Auto.
 */
interface ProviderRegistry {
    fun observeActive(): StateFlow<List<MusicProvider>>
    fun activeOrNull(id: MusicProviderId): MusicProvider?
    fun register(provider: MusicProvider)
    fun unregister(id: MusicProviderId)
    fun clear()
}

/**
 * Implémentation en mémoire, singleton process-wide — même raison structurelle
 * que MusicSourceLocator/PlaylistRepositoryLocator (pas de graphe Hilt dans
 * cette V1, PlaybackService instancié indépendamment de l'Activity).
 */
object InMemoryProviderRegistry : ProviderRegistry {
    private val _active = MutableStateFlow<List<MusicProvider>>(emptyList())

    override fun observeActive(): StateFlow<List<MusicProvider>> = _active

    override fun activeOrNull(id: MusicProviderId): MusicProvider? =
        _active.value.firstOrNull { it.id == id }

    override fun register(provider: MusicProvider) {
        _active.value = _active.value.filterNot { it.id == provider.id } + provider
    }

    override fun unregister(id: MusicProviderId) {
        _active.value = _active.value.filterNot { it.id == id }
    }

    override fun clear() {
        _active.value = emptyList()
    }
}
