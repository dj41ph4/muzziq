package com.muzziq.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muzziq.mobile.data.ApiClientFactory
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.AppPrefs
import com.muzziq.mobile.data.MuzziqApi
import com.muzziq.mobile.data.MusicSource
import com.muzziq.mobile.data.QueueStateStore
import com.muzziq.mobile.data.QueueStateStore.Companion.toTrack
import com.muzziq.mobile.data.ServerMusicSource
import com.muzziq.mobile.core.capabilities.CapabilityManager
import com.muzziq.mobile.core.capabilities.MuzziQCapabilities
import com.muzziq.mobile.core.capabilities.ServerConnectionState
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import com.muzziq.mobile.data.room.MuzziQDatabase
import com.muzziq.mobile.domain.DownloadRepository
import com.muzziq.mobile.domain.PlaylistRepository
import com.muzziq.mobile.domain.PlaylistSummary
import com.muzziq.mobile.domain.RoomFavoriteRepository
import com.muzziq.mobile.domain.RoomPlaylistRepository
import com.muzziq.mobile.domain.ServerDownloadRepository
import com.muzziq.mobile.domain.ServerPlaylistRepository
import com.muzziq.mobile.domain.StandaloneDownloadRepository
import com.muzziq.mobile.playback.MusicSourceLocator
import com.muzziq.mobile.playback.PlayerController
import com.muzziq.mobile.playback.PlaylistRepositoryLocator
import com.muzziq.mobile.standalone.HistoryEntry
import com.muzziq.mobile.standalone.MigrationManager
import com.muzziq.mobile.standalone.StandaloneMusicSource
import com.muzziq.mobile.standalone.StandaloneMusicSourceHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RootUiState {
    data object Loading : RootUiState
    data object Onboarding : RootUiState
    data class Ready(val mode: AppMode) : RootUiState
}

/** Rangée Home déjà résolue en Track (voir HomeRowDto, data/model/Models.kt) — le
 * mapping DTO serveur vers Track vit dans AppViewModel, pas dans l'écran. */
data class HomeRowUi(val id: String, val title: String, val tracks: List<Track>)

/** Browse artiste/album (§17, plan) — ne couvre QUE la bibliothèque locale déjà
 * scannée (serveur ou standalone), aucune agrégation inventée. En mode Lié, id/
 * champs viennent de /api/artists /api/albums (voir Models.kt) ; en standalone,
 * calculés côté client par groupement du Track déjà chargé — même id de secours
 * (artiste en minuscule, "artiste::album" en minuscule) pour que le code de
 * navigation (openArtist/openAlbum) reste identique dans les deux modes. */
data class ArtistUi(val id: String, val name: String, val trackCount: Int, val albumCount: Int)
data class AlbumUi(val id: String, val title: String, val artist: String, val trackCount: Int)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    // Le paramètre de constructeur `application` (sans val) n'est capturé que dans les
    // initializers de propriété/blocs `by lazy` — pas dans le corps des fonctions membres
    // (activateLinked, etc.), où l'identifiant se résout silencieusement vers le champ
    // privé `application` hérité d'AndroidViewModel → erreur d'accès. Bug réel trouvé par
    // le run CI (compileReleaseKotlin, "it is private in androidx/lifecycle/AndroidViewModel")
    // avant ce correctif — d'où cette propriété explicite, utilisable partout dans la classe.
    private val appContext: Application = application
    private val prefs = AppPrefs(application)
    private val capabilityManager = CapabilityManager()
    val player = PlayerController(application)

    private val _state = MutableStateFlow<RootUiState>(RootUiState.Loading)
    val state: StateFlow<RootUiState> = _state.asStateFlow()

    private val _library = MutableStateFlow<List<Track>>(emptyList())
    val library: StateFlow<List<Track>> = _library.asStateFlow()

    /** Rangées Home (§46) — GET /api/home, mode Lié uniquement (moteur de recommandation
     * déterministe serveur, aucun équivalent standalone aujourd'hui). Vide en standalone
     * ou si le serveur n'a encore aucune rangée à proposer — HomeScreen retombe alors sur
     * la liste "Bibliothèque" à plat, jamais un carrousel vide affiché pour faire joli. */
    private val _homeRows = MutableStateFlow<List<HomeRowUi>>(emptyList())
    val homeRows: StateFlow<List<HomeRowUi>> = _homeRows.asStateFlow()

    /** Historique d'écoute (plan §41) — standalone uniquement aujourd'hui : PlaybackService
     * n'enregistre un événement que pour un morceau local (TrackSource.Local), le mode
     * Lié ne pousse rien vers le serveur pour l'instant. Vide en Lié, jamais fabriqué. */
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    fun refreshHistory() {
        viewModelScope.launch {
            _history.value = standalone.recentHistory()
        }
    }

    /** Browse artiste/album — /api/artists /api/albums en mode Lié (bibliothèque locale
     * déjà scannée côté serveur, aucun browse du catalogue YouTube Music), groupement
     * client de la bibliothèque locale en standalone. [browseApi] non nul uniquement
     * après activateLinked(). */
    private var browseApi: MuzziqApi? = null
    private val _artists = MutableStateFlow<List<ArtistUi>>(emptyList())
    val artists: StateFlow<List<ArtistUi>> = _artists.asStateFlow()
    private val _albums = MutableStateFlow<List<AlbumUi>>(emptyList())
    val albums: StateFlow<List<AlbumUi>> = _albums.asStateFlow()
    private val _browseTracks = MutableStateFlow<List<Track>>(emptyList())
    val browseTracks: StateFlow<List<Track>> = _browseTracks.asStateFlow()

    fun refreshArtistsAlbums() {
        viewModelScope.launch {
            val api = browseApi
            if (api != null) {
                val artistsBody = runCatching { api.artists() }.getOrNull()?.takeIf { it.isSuccessful }?.body()
                _artists.value = artistsBody?.artists.orEmpty().map { ArtistUi(it.id, it.name, it.trackCount, it.albumCount) }
                val albumsBody = runCatching { api.albums() }.getOrNull()?.takeIf { it.isSuccessful }?.body()
                _albums.value = albumsBody?.albums.orEmpty().map { AlbumUi(it.id, it.title, it.artist, it.trackCount) }
            } else {
                val tracks = standalone.library().getOrNull().orEmpty()
                _artists.value = tracks.groupBy { it.artist }
                    .map { (artist, list) ->
                        ArtistUi(
                            id = artist.lowercase(),
                            name = artist,
                            trackCount = list.size,
                            albumCount = list.mapNotNull { it.album }.distinct().size,
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                _albums.value = tracks.filter { it.album != null }
                    .groupBy { "${it.artist}::${it.album}".lowercase() }
                    .map { (groupId, list) -> AlbumUi(id = groupId, title = list.first().album.orEmpty(), artist = list.first().artist, trackCount = list.size) }
                    .sortedBy { it.title.lowercase() }
            }
        }
    }

    fun openArtist(id: String) {
        viewModelScope.launch {
            val api = browseApi
            if (api != null) {
                val body = runCatching { api.artistDetail(id) }.getOrNull()?.takeIf { it.isSuccessful }?.body()
                _browseTracks.value = body?.tracks.orEmpty().map { t ->
                    Track(
                        id = t.id,
                        title = t.title,
                        artist = body?.name.orEmpty(),
                        album = t.album,
                        durationSeconds = t.durationSeconds,
                        artworkUrl = null,
                        source = TrackSource.Server(t.id),
                    )
                }
            } else {
                _browseTracks.value = standalone.library().getOrNull().orEmpty().filter { it.artist.lowercase() == id }
            }
        }
    }

    fun openAlbum(id: String) {
        viewModelScope.launch {
            val api = browseApi
            if (api != null) {
                val body = runCatching { api.albumDetail(id) }.getOrNull()?.takeIf { it.isSuccessful }?.body()
                _browseTracks.value = body?.tracks.orEmpty().map { t ->
                    Track(
                        id = t.id,
                        title = t.title,
                        artist = body?.artist.orEmpty(),
                        album = body?.title,
                        durationSeconds = t.durationSeconds,
                        artworkUrl = null,
                        source = TrackSource.Server(t.id),
                    )
                }
            } else {
                _browseTracks.value = standalone.library().getOrNull().orEmpty()
                    .filter { "${it.artist}::${it.album}".lowercase() == id }
            }
        }
    }

    fun closeBrowseDetail() {
        _browseTracks.value = emptyList()
    }

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
    private val queueStateStore by lazy { QueueStateStore(application) }

    /** Favoris — Room, indépendants du mode (§56.4) et du serveur. Premier vrai
     * consommateur du schéma Room posé plus tôt (data/room/). */
    private val favorites: RoomFavoriteRepository by lazy { RoomFavoriteRepository(application) }
    val favoriteTrackIds: StateFlow<Set<String>> = favorites.observeFavorites()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val isFav = favorites.isFavorite(track.id)
            favorites.setFavorite(track.id, !isFav)
        }
    }

    /** Téléchargements hors-ligne (plan §57) — StandaloneDownloadRepository en standalone
     * (déjà local par définition), ServerDownloadRepository en mode Lié (rapatrie
     * réellement les octets). Choisi/instancié dans activateStandalone/activateLinked,
     * jamais avant qu'une source ne soit active. */
    private var downloadRepository: DownloadRepository? = null
    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()
    private val _downloadingTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<String>> = _downloadingTrackIds.asStateFlow()

    fun requestDownload(track: Track) {
        val repo = downloadRepository ?: return
        if (track.id in _downloadingTrackIds.value) return
        viewModelScope.launch {
            _downloadingTrackIds.value = _downloadingTrackIds.value + track.id
            repo.requestDownload(track).onFailure { _error.value = "Téléchargement échoué : ${it.message}" }
            _downloadingTrackIds.value = _downloadingTrackIds.value - track.id
            refreshDownloads()
        }
    }

    private fun refreshDownloads() {
        val repo = downloadRepository ?: return
        viewModelScope.launch {
            repo.downloadedTrackIds().onSuccess { _downloadedTrackIds.value = it.toSet() }
        }
    }

    /** Playlists (plan §6/§66) — RoomPlaylistRepository en standalone, ServerPlaylistRepository
     * en mode Lié, choisi/instancié dans activateStandalone/activateLinked comme les
     * téléchargements. */
    private var playlistRepository: PlaylistRepository? = null
    private val _playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = _playlists.asStateFlow()
    private val _playlistTracks = MutableStateFlow<List<Track>>(emptyList())
    val playlistTracks: StateFlow<List<Track>> = _playlistTracks.asStateFlow()
    private val _openPlaylistId = MutableStateFlow<String?>(null)
    val openPlaylistId: StateFlow<String?> = _openPlaylistId.asStateFlow()

    fun refreshPlaylists() {
        val repo = playlistRepository ?: return
        viewModelScope.launch {
            repo.playlists().onSuccess { _playlists.value = it }.onFailure { _error.value = it.message }
        }
    }

    fun createPlaylist(name: String) {
        val repo = playlistRepository ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.createPlaylist(name.trim())
                .onSuccess { refreshPlaylists() }
                .onFailure { _error.value = it.message }
        }
    }

    fun deletePlaylist(playlistId: String) {
        val repo = playlistRepository ?: return
        viewModelScope.launch {
            repo.deletePlaylist(playlistId)
                .onSuccess {
                    if (_openPlaylistId.value == playlistId) closePlaylist()
                    refreshPlaylists()
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun openPlaylist(playlistId: String) {
        val repo = playlistRepository ?: return
        _openPlaylistId.value = playlistId
        viewModelScope.launch {
            repo.playlistTracks(playlistId)
                .onSuccess { _playlistTracks.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun closePlaylist() {
        _openPlaylistId.value = null
        _playlistTracks.value = emptyList()
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        val repo = playlistRepository ?: return
        viewModelScope.launch {
            repo.addTrackToPlaylist(playlistId, track)
                .onSuccess {
                    if (_openPlaylistId.value == playlistId) openPlaylist(playlistId)
                    refreshPlaylists()
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        val repo = playlistRepository ?: return
        viewModelScope.launch {
            repo.removeTrackFromPlaylist(playlistId, trackId)
                .onSuccess {
                    if (_openPlaylistId.value == playlistId) openPlaylist(playlistId)
                    refreshPlaylists()
                }
                .onFailure { _error.value = it.message }
        }
    }

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
        downloadRepository = StandaloneDownloadRepository(standalone)
        playlistRepository = RoomPlaylistRepository(appContext).also { PlaylistRepositoryLocator.set(it) }
        browseApi = null
        _homeRows.value = emptyList()
        _state.value = RootUiState.Ready(AppMode.STANDALONE)
        refreshLibrary()
        refreshDownloads()
        refreshPlaylists()
        refreshHistory()
        refreshArtistsAlbums()
        restorePersistedQueue()
    }

    private suspend fun activateLinked(url: String) {
        val cookie = prefs.sessionCookie.first()
        val downloadDao = MuzziQDatabase.get(appContext).downloadDao()
        val source = ServerMusicSource(url, cookie, downloadDao)
        musicSource = source
        MusicSourceLocator.set(source)
        downloadRepository = ServerDownloadRepository(appContext, source)
        playlistRepository = ServerPlaylistRepository(url, cookie).also { PlaylistRepositoryLocator.set(it) }
        browseApi = ApiClientFactory.create(url)
        _state.value = RootUiState.Ready(AppMode.LINKED)
        refreshLibrary()
        refreshServerCapabilities(url)
        refreshDownloads()
        refreshPlaylists()
        refreshHomeRows(url)
        refreshHistory()
        refreshArtistsAlbums()
        restorePersistedQueue()
    }

    /** GET /api/home (moteur de recommandation déterministe, déjà réel côté serveur —
     * src/lib/recommendations/deterministicEngine.ts). Échec réseau ⇒ rangées vides,
     * HomeScreen retombe sur la bibliothèque à plat, jamais une erreur bloquante. */
    private fun refreshHomeRows(url: String) {
        viewModelScope.launch {
            val api = ApiClientFactory.create(url)
            val result = runCatching { api.homeRows() }
            val rows = result.getOrNull()?.takeIf { it.isSuccessful }?.body()?.rows.orEmpty()
            _homeRows.value = rows.map { row ->
                HomeRowUi(
                    id = row.id,
                    title = row.title,
                    tracks = row.recordings.map { rec ->
                        Track(
                            id = rec.id,
                            title = rec.title,
                            artist = rec.artist,
                            album = rec.album,
                            durationSeconds = rec.durationSeconds,
                            artworkUrl = rec.thumbnailUrl,
                            source = TrackSource.Server(rec.id),
                        )
                    },
                )
            }
        }
    }

    /** Reprend l'affichage de la dernière file jouée (plan §57), sans relancer de
     * lecture tant que l'utilisateur n'a pas tapé play (voir PlayerController.restoreDisplay) —
     * marche identiquement dans les deux modes, la persistance ne dépend d'aucune
     * capacité serveur. */
    private suspend fun restorePersistedQueue() {
        val state = queueStateStore.load() ?: return
        if (state.tracks.isEmpty()) return
        player.restoreDisplay(state.tracks.map { it.toTrack() }, state.currentIndex, state.positionMs)
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
