package com.muzziq.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muzziq.mobile.data.ApiClientFactory
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.AppPrefs
import com.muzziq.mobile.data.MusicSource
import com.muzziq.mobile.data.ServerMusicSource
import com.muzziq.mobile.core.capabilities.CapabilityManager
import com.muzziq.mobile.core.capabilities.MuzziQCapabilities
import com.muzziq.mobile.core.capabilities.ServerConnectionState
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.playback.MusicSourceLocator
import com.muzziq.mobile.playback.PlayerController
import com.muzziq.mobile.standalone.MigrationManager
import com.muzziq.mobile.standalone.StandaloneMusicSource
import com.muzziq.mobile.standalone.StandaloneMusicSourceHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface RootUiState {
    data object Loading : RootUiState
    data object Onboarding : RootUiState
    data class Ready(val mode: AppMode) : RootUiState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPrefs(application)
    private val capabilityManager = CapabilityManager()
    val player = PlayerController(application)

    private val _state = MutableStateFlow<RootUiState>(RootUiState.Loading)
    val state: StateFlow<RootUiState> = _state.asStateFlow()

    private val _library = MutableStateFlow<List<Track>>(emptyList())
    val library: StateFlow<List<Track>> = _library.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _serverConnectionState = MutableStateFlow(ServerConnectionState.DISCONNECTED)
    val serverConnectionState: StateFlow<ServerConnectionState> = _serverConnectionState.asStateFlow()
    private val _capabilities = MutableStateFlow(capabilityManager.forConnection(ServerConnectionState.DISCONNECTED))
    val capabilities: StateFlow<MuzziQCapabilities> = _capabilities.asStateFlow()

    var musicSource: MusicSource? = null
        private set

    val standalone: StandaloneMusicSource by lazy { StandaloneMusicSource(application) }

    init {
        StandaloneMusicSourceHolder.instance = standalone
        player.connect()
        viewModelScope.launch {
            prefs.serverConnectionState.collect { updateConnectionState(it) }
        }
        viewModelScope.launch {
            val onboarded = prefs.onboarded.first()
            if (!onboarded) {
                _state.value = RootUiState.Onboarding
                return@launch
            }
            when (prefs.mode.first()) {
                AppMode.STANDALONE -> activateStandalone()
                AppMode.LINKED -> {
                    val url = prefs.currentServerUrlOrNull()
                    if (url == null) _state.value = RootUiState.Onboarding
                    else activateLinked(url)
                }
                AppMode.UNSET -> _state.value = RootUiState.Onboarding
            }
        }
    }

    fun chooseStandalone() {
        viewModelScope.launch {
            prefs.setStandalone()
            activateStandalone()
        }
    }

    /** §56.4 : test /api/health obligatoire avant d'accepter un serveur — jamais
     * de confiance aveugle dans l'URL saisie par l'utilisateur. */
    suspend fun testServer(url: String): Boolean {
        val candidate = ServerMusicSource(url, null)
        return candidate.health()
    }

    fun chooseLinked(url: String) {
        viewModelScope.launch {
            prefs.setServerConnectionState(ServerConnectionState.CONNECTING)
            _busy.value = true
            _error.value = null
            val ok = testServer(url)
            if (!ok) {
                prefs.setServerConnectionState(ServerConnectionState.ERROR)
                _error.value = "Ce serveur ne répond pas comme un serveur MuzziQ."
                _busy.value = false
                return@launch
            }
            prefs.setLinked(url)
            activateLinked(url)
            _busy.value = false
        }
    }

    private suspend fun activateStandalone() {
        musicSource = standalone
        MusicSourceLocator.set(standalone)
        _state.value = RootUiState.Ready(AppMode.STANDALONE)
        refreshLibrary()
    }

    private suspend fun activateLinked(url: String) {
        val cookie = prefs.sessionCookie.first()
        val source = ServerMusicSource(url, cookie)
        musicSource = source
        MusicSourceLocator.set(source)
        _state.value = RootUiState.Ready(AppMode.LINKED)
        refreshLibrary()
        refreshServerCapabilities(url)
    }

    /** Interroge /api/capabilities plutôt que de supposer qu'un serveur connecté possède
     * toutes les capacités (l'ancien comportement de CapabilityManager.forConnection —
     * gardé comme repli si l'appel échoue, jamais comme vérité une fois le vrai serveur
     * interrogeable). Échec réseau ⇒ on garde les capacités par défaut (aucune capacité
     * "serveur" supposée), jamais une erreur qui bloque l'app. */
    private fun refreshServerCapabilities(url: String) {
        viewModelScope.launch {
            val api = ApiClientFactory.create(url)
            val result = runCatching { api.capabilities() }
            val payload = result.getOrNull()?.takeIf { it.isSuccessful }?.body()?.capabilities
            if (payload != null) {
                _capabilities.value = _capabilities.value.copy(
                    flacAcquisition = payload.flacAcquisition,
                    torrentAcquisition = payload.torrentAcquisition,
                    nasLibrary = payload.nasLibrary,
                    monitoring = payload.monitoring,
                    automaticUpgrade = payload.automaticUpgrade,
                    centralSync = payload.centralSync,
                    remoteJam = payload.remoteJam,
                )
            }
        }
    }

    private fun updateConnectionState(state: ServerConnectionState) {
        _serverConnectionState.value = state
        // CONNECTED/DEGRADED : ne pas écraser avec le repli "tout activé" de
        // CapabilityManager ici — refreshServerCapabilities() (appelé par
        // activateLinked) est la seule source de vérité une fois un serveur
        // réellement interrogé via /api/capabilities. Sans ce garde-fou, cette
        // collecte (déclenchée par tout changement de ServerConnectionState,
        // y compris après que refreshServerCapabilities ait déjà répondu)
        // pouvait réécraser les vraies capacités avec le placeholder statique
        // "tout vrai" — bug réel repéré en écrivant ce commentaire, corrigé
        // avant d'être poussé.
        if (state == ServerConnectionState.DISCONNECTED ||
            state == ServerConnectionState.CONNECTING ||
            state == ServerConnectionState.ERROR
        ) {
            _capabilities.value = capabilityManager.forConnection(state)
        }
    }

    fun rescanStandaloneLibrary() {
        viewModelScope.launch {
            _busy.value = true
            standalone.rescan()
            refreshLibrary()
            _busy.value = false
        }
    }

    fun refreshLibrary() {
        val source = musicSource ?: return
        viewModelScope.launch {
            source.library().onSuccess { _library.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun search(query: String) {
        val source = musicSource ?: return
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            source.search(query).onSuccess { _searchResults.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun play(track: Track) {
        val source = musicSource ?: return
        player.play(track, source)
    }

    /** À utiliser depuis les écrans de liste (Home/Recherche/Bibliothèque) : [tracks] est
     * le contexte visible au moment du tap, [clicked] le morceau choisi — alimente la
     * file d'attente réelle (§40) pour que Suivant/Précédent fonctionnent depuis le
     * plein écran, plutôt qu'un play() isolé sans contexte. */
    fun playFrom(tracks: List<Track>, clicked: Track) {
        val source = musicSource ?: return
        val startIndex = tracks.indexOfFirst { it.id == clicked.id }.let { if (it >= 0) it else 0 }
        player.playQueue(tracks, startIndex, source)
    }

    /** Bascule mode Lié → autre serveur, ou retour au choix (§56.4). Ne supprime jamais
     * la bibliothèque locale standalone en repassant en mode Lié. */
    fun backToOnboarding() {
        viewModelScope.launch {
            prefs.resetMode()
            _state.value = RootUiState.Onboarding
        }
    }

    /** Migration Standalone → Lié (§56.4) — événement ponctuel, jamais silencieux. */
    suspend fun migrateStandaloneTo(url: String): MigrationManager.SyncReport {
        val cookie = prefs.sessionCookie.first()
        return MigrationManager(standalone).migrateTo(url, cookie)
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
