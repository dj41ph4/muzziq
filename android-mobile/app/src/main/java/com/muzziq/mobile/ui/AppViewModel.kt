package com.muzziq.mobile.ui

import android.app.Application
import android.net.Uri
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
import com.muzziq.mobile.data.room.LinkedMusicAccountEntity
import com.muzziq.mobile.domain.DownloadRepository
import com.muzziq.mobile.domain.InMemoryProviderRegistry
import com.muzziq.mobile.domain.MusicProvider
import com.muzziq.mobile.domain.MusicProviderId
import com.muzziq.mobile.domain.MusicSourceCatalogueAdapter
import com.muzziq.mobile.domain.MusicSourceLibraryAdapter
import com.muzziq.mobile.domain.MusicSourceStreamResolverAdapter
import com.muzziq.mobile.domain.PlaylistRepository
import com.muzziq.mobile.domain.PlaylistSummary
import com.muzziq.mobile.domain.RoomFavoriteRepository
import com.muzziq.mobile.domain.RoomPlaylistRepository
import com.muzziq.mobile.domain.ServerDownloadRepository
import com.muzziq.mobile.domain.ServerPlaylistRepository
import com.muzziq.mobile.domain.StandaloneDownloadRepository
import com.muzziq.mobile.domain.StandaloneHistoryAdapter
import com.muzziq.mobile.playback.MusicSourceLocator
import com.muzziq.mobile.playback.PlayerController
import com.muzziq.mobile.playback.PlaylistRepositoryLocator
import com.muzziq.mobile.providers.spotify.SpotifyAuthManager
import com.muzziq.mobile.providers.spotify.SpotifyCredentialStore
import com.muzziq.mobile.providers.spotify.SpotifyProvider
import com.muzziq.mobile.security.AndroidKeystoreCredentialVault
import com.muzziq.mobile.standalone.HistoryEntry
import com.muzziq.mobile.standalone.MigrationManager
import com.muzziq.mobile.standalone.StandaloneMusicSource
import com.muzziq.mobile.standalone.StandaloneMusicSourceHolder
import java.util.UUID
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

/** État du compte Spotify pour l'écran Réglages (§67, priorité 5). NotConfigured
 * distinct de Disconnected : capacité absente (aucun Client ID dans
 * spotify.properties) ne doit jamais afficher un bouton "Connecter" qui
 * échouerait en silence — voir SpotifyAuthManager.isConfigured(). */
sealed interface SpotifyAccountUiState {
    data object NotConfigured : SpotifyAccountUiState
    data object Disconnected : SpotifyAccountUiState
    data class Connected(val displayName: String?, val avatarUrl: String?) : SpotifyAccountUiState
}

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

    /** Affiché dans l'écran Réglages (§56.4) — l'utilisateur doit pouvoir voir à quel
     * serveur il est connecté, et en changer, sans réinstaller l'app. */
    val serverUrl: StateFlow<String?> = prefs.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val savedServerUrls: StateFlow<List<String>> = prefs.savedServerUrls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _capabilities = MutableStateFlow(capabilityManager.forConnection(ServerConnectionState.DISCONNECTED))
    val capabilities: StateFlow<MuzziQCapabilities> = _capabilities.asStateFlow()

    /** N'est plus consommé que pour la lecture (play/playFrom → PlayerController →
     * PlaybackService) : c'est la seule surface encore couplée à `MusicSource` — voir
     * le commentaire de tête de domain/ProviderRegistry.kt pour la raison (Android Auto/
     * MediaLibraryService non vérifiable sans appareil réel, migration volontairement
     * repoussée). refreshLibrary()/search() sont passés à ProviderRegistry ci-dessous. */
    var musicSource: MusicSource? = null
        private set

    val standalone: StandaloneMusicSource by lazy { StandaloneMusicSource(application) }
    private val queueStateStore by lazy { QueueStateStore(application) }

    /** Paroles (onglet plein écran) — [com.muzziq.mobile.domain.NullLyricsProvider] est la
     * seule implémentation existante de LyricsProvider aujourd'hui (aucune route serveur
     * `/api/lyrics`, aucun fournisseur tiers branché, plan §38 jamais commencé) : LyricsPanel
     * affiche donc honnêtement "non disponible" plutôt qu'un texte inventé. Remplacer cette
     * ligne suffira le jour où un vrai fournisseur existe. */
    val lyricsProvider: com.muzziq.mobile.domain.LyricsProvider by lazy { com.muzziq.mobile.domain.NullLyricsProvider() }

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

    /** Playlists (plan §6/§66/§67) — RoomPlaylistRepository en standalone,
     * ServerPlaylistRepository en mode Lié, choisi/instancié dans activateStandalone/
     * activateLinked comme les téléchargements ([playlistRepository], exclusif LOCAL/
     * SERVER, seul backend capable de créer/modifier). Spotify (spotifyProvider,
     * lecture seule) s'ajoute par-dessus quand un compte est connecté — cumulatif
     * (§67), jamais fusionné avec une playlist de même nom (§58, voir
     * PlaylistSummary.provider). [playlistRepositoryFor] route chaque action vers le
     * bon backend selon la provenance réelle de la playlist ciblée. */
    private var playlistRepository: PlaylistRepository? = null
    private val _playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = _playlists.asStateFlow()
    private val _playlistTracks = MutableStateFlow<List<Track>>(emptyList())
    val playlistTracks: StateFlow<List<Track>> = _playlistTracks.asStateFlow()
    private val _openPlaylistId = MutableStateFlow<String?>(null)
    val openPlaylistId: StateFlow<String?> = _openPlaylistId.asStateFlow()

    /** Backend réel pour une playlist déjà listée dans `_playlists` — Spotify si son
     * provider est SPOTIFY, le repo primaire (Room/serveur) sinon. Un id inconnu de
     * `_playlists` (jamais rafraîchi, ou déjà supprimé) retombe sur le repo primaire :
     * comportement identique à avant cette agrégation plutôt qu'un échec silencieux. */
    private fun playlistRepositoryFor(playlistId: String): PlaylistRepository? {
        val provider = _playlists.value.firstOrNull { it.id == playlistId }?.provider
        return if (provider == MusicProviderId.SPOTIFY) spotifyProvider else playlistRepository
    }

    fun refreshPlaylists() {
        val repo = playlistRepository ?: return
        viewModelScope.launch {
            repo.playlists()
                .onSuccess { primary ->
                    val spotify = if (_spotifyAccount.value is SpotifyAccountUiState.Connected) {
                        spotifyProvider.playlists().getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    _playlists.value = primary + spotify
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun createPlaylist(name: String) {
        // Spotify ne peut jamais créer (lecture seule) — toujours le repo primaire.
        val repo = playlistRepository ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.createPlaylist(name.trim())
                .onSuccess { refreshPlaylists() }
                .onFailure { _error.value = it.message }
        }
    }

    fun deletePlaylist(playlistId: String) {
        val repo = playlistRepositoryFor(playlistId) ?: return
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
        val repo = playlistRepositoryFor(playlistId) ?: return
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
        val repo = playlistRepositoryFor(playlistId) ?: return
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
        val repo = playlistRepositoryFor(playlistId) ?: return
        viewModelScope.launch {
            repo.removeTrackFromPlaylist(playlistId, trackId)
                .onSuccess {
                    if (_openPlaylistId.value == playlistId) openPlaylist(playlistId)
                    refreshPlaylists()
                }
                .onFailure { _error.value = it.message }
        }
    }

    /** Compte Spotify (§67, priorité 5) — coffre chiffré + Web API en lecture seule,
     * voir providers/spotify/. Instanciation manuelle (même pattern que `standalone`/
     * `favorites` ci-dessus, pas de graphe DI dans cette V1). */
    private val spotifyCredentialStore by lazy { SpotifyCredentialStore(AndroidKeystoreCredentialVault(appContext)) }
    private val spotifyAuthManager by lazy { SpotifyAuthManager(spotifyCredentialStore) }
    private val spotifyProvider by lazy { SpotifyProvider(spotifyAuthManager) }
    private val linkedAccountDao by lazy { MuzziQDatabase.get(appContext).linkedMusicAccountDao() }

    private val _spotifyAccount = MutableStateFlow<SpotifyAccountUiState>(
        if (spotifyAuthManager.isConfigured()) SpotifyAccountUiState.Disconnected else SpotifyAccountUiState.NotConfigured,
    )
    val spotifyAccount: StateFlow<SpotifyAccountUiState> = _spotifyAccount.asStateFlow()
    private val _spotifyBusy = MutableStateFlow(false)
    val spotifyBusy: StateFlow<Boolean> = _spotifyBusy.asStateFlow()
    private val _spotifyError = MutableStateFlow<String?>(null)
    val spotifyError: StateFlow<String?> = _spotifyError.asStateFlow()

    /** `code_verifier`/`state` PKCE de la tentative en cours — vit en mémoire process
     * (pas Room, pas DataStore : jamais un secret persistant, jamais utile après le
     * round-trip Custom Tab). Si le process est tué pendant le trajet Custom Tab
     * (mémoire système basse), la tentative est simplement perdue et l'utilisateur
     * retape "Connecter" — pas de crash, pas de faux succès. Non vérifiable sans
     * appareil réel (voir commentaire de tête de SpotifyAuthManager.kt). */
    private data class PendingSpotifyAuth(val verifier: String, val state: String)
    private var pendingSpotifyAuth: PendingSpotifyAuth? = null

    /** URL Custom Tab à ouvrir — null si aucun Client ID configuré (SettingsScreen ne
     * doit jamais appeler ceci sans vérifier `spotifyAccount != NotConfigured` avant). */
    fun spotifyLoginUri(): Uri? {
        if (!spotifyAuthManager.isConfigured()) return null
        _spotifyError.value = null
        val verifier = spotifyAuthManager.generateCodeVerifier()
        val challenge = spotifyAuthManager.codeChallengeFor(verifier)
        val state = UUID.randomUUID().toString()
        pendingSpotifyAuth = PendingSpotifyAuth(verifier, state)
        return spotifyAuthManager.buildAuthorizationUri(challenge, state)
    }

    /** Reçoit l'URI `muzziq://spotify-callback?...` capturée par MainActivity. Ignore
     * silencieusement tout appel sans tentative en cours (pendingSpotifyAuth == null) —
     * un deep link rejoué ou un intent parasite ne doit jamais déclencher d'échange. */
    fun handleSpotifyCallback(uri: Uri) {
        val pending = pendingSpotifyAuth ?: return
        pendingSpotifyAuth = null

        val authError = uri.getQueryParameter("error")
        if (authError != null) {
            _spotifyError.value = if (authError == "access_denied") {
                "Connexion Spotify annulée."
            } else {
                "Spotify a refusé la connexion ($authError)."
            }
            return
        }

        val returnedState = uri.getQueryParameter("state")
        if (returnedState != pending.state) {
            _spotifyError.value = "Réponse Spotify invalide (state incohérent) — réessaie."
            return
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            _spotifyError.value = "Réponse Spotify incomplète (aucun code)."
            return
        }

        viewModelScope.launch {
            _spotifyBusy.value = true
            _spotifyError.value = null

            val exchange = spotifyAuthManager.exchangeCode(code, pending.verifier)
            if (exchange.isFailure) {
                _spotifyError.value = "Échange du jeton Spotify échoué : ${exchange.exceptionOrNull()?.message}"
                _spotifyBusy.value = false
                return@launch
            }

            val profile = spotifyProvider.profile()
            val identity = profile.getOrNull()
            if (identity == null) {
                _spotifyError.value = "Connecté mais profil Spotify illisible : ${profile.exceptionOrNull()?.message}"
                _spotifyBusy.value = false
                return@launch
            }

            val existing = linkedAccountDao.byProvider("SPOTIFY")
            linkedAccountDao.upsert(
                LinkedMusicAccountEntity(
                    id = existing?.id ?: "spotify:${identity.id}",
                    provider = "SPOTIFY",
                    externalUserId = identity.id,
                    displayName = identity.displayName,
                    avatarUrl = identity.avatarUrl,
                    isPrimary = existing?.isPrimary ?: false,
                    syncEnabled = true,
                    connectedAt = existing?.connectedAt ?: System.currentTimeMillis(),
                    lastSyncAt = System.currentTimeMillis(),
                    status = "CONNECTED",
                ),
            )
            registerSpotifyProvider()
            _spotifyAccount.value = SpotifyAccountUiState.Connected(identity.displayName, identity.avatarUrl)
            _spotifyBusy.value = false
            refreshLibrary()
            refreshPlaylists()
        }
    }

    /** Déconnexion (règle absolue du plan) : efface les jetons + la ligne
     * `linked_music_accounts`, jamais les favoris/playlists/historique/downloads —
     * aucune de ces tables ne référence le compte Spotify. Les playlists Spotify
     * disparaissent de `_playlists` au prochain refreshPlaylists() (leur backend
     * n'existe plus) — jamais une suppression de données MuzziQ. */
    fun disconnectSpotify() {
        viewModelScope.launch {
            spotifyAuthManager.disconnect()
            linkedAccountDao.byProvider("SPOTIFY")?.let { linkedAccountDao.delete(it.id) }
            InMemoryProviderRegistry.unregister(MusicProviderId.SPOTIFY)
            _spotifyAccount.value = SpotifyAccountUiState.Disconnected
            _spotifyError.value = null
            if (_openPlaylistId.value != null && _playlists.value.firstOrNull { it.id == _openPlaylistId.value }?.provider == MusicProviderId.SPOTIFY) {
                closePlaylist()
            }
            refreshLibrary()
            refreshPlaylists()
        }
    }

    private fun registerSpotifyProvider() {
        InMemoryProviderRegistry.register(
            MusicProvider(
                id = MusicProviderId.SPOTIFY,
                label = "Spotify",
                catalogue = spotifyProvider,
                library = spotifyProvider,
                streamResolver = spotifyProvider,
            ),
        )
    }

    /** Restaure l'état Spotify au démarrage (compte déjà lié lors d'une session
     * précédente) — LOCAL/SERVER restent en mode exclusif (activateStandalone/
     * activateLinked), mais Spotify est cumulatif (§67) : ne dépend d'aucun des deux,
     * s'enregistre indépendamment dans ProviderRegistry si un coffre valide existe. */
    private suspend fun restoreSpotifyAccountState() {
        if (!spotifyAuthManager.isConnected()) {
            _spotifyAccount.value = SpotifyAccountUiState.Disconnected
            return
        }
        val account = linkedAccountDao.byProvider("SPOTIFY")
        _spotifyAccount.value = SpotifyAccountUiState.Connected(account?.displayName, account?.avatarUrl)
        registerSpotifyProvider()
        refreshLibrary()
        refreshPlaylists()
    }

    init {
        StandaloneMusicSourceHolder.instance = standalone
        if (spotifyAuthManager.isConfigured()) {
            viewModelScope.launch { restoreSpotifyAccountState() }
        }
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
            val normalizedUrl = AppPrefs.normalizeServerUrl(url)
            if (normalizedUrl.isBlank()) return@launch
            prefs.setServerConnectionState(ServerConnectionState.CONNECTING)
            _busy.value = true
            _error.value = null
            val ok = testServer(normalizedUrl)
            if (!ok) {
                prefs.setServerConnectionState(ServerConnectionState.ERROR)
                _error.value = "Ce serveur ne répond pas comme un serveur MuzziQ."
                _busy.value = false
                return@launch
            }
            prefs.setLinked(normalizedUrl)
            activateLinked(normalizedUrl)
            _busy.value = false
        }
    }

    /** Sélectionne un serveur déjà validé depuis les réglages ou l'écran d'accueil. */
    fun selectSavedServer(url: String) = chooseLinked(url)

    /** Supprime un raccourci serveur. Si c'est le serveur actif, on revient au choix
     * des sources sans toucher à la bibliothèque standalone ni aux autres profils. */
    fun removeSavedServer(url: String) {
        viewModelScope.launch {
            val normalized = AppPrefs.normalizeServerUrl(url)
            val active = prefs.currentServerUrlOrNull()
            prefs.forgetServer(normalized)
            if (active == normalized) {
                prefs.resetMode()
                InMemoryProviderRegistry.clear()
                _serverConnectionState.value = ServerConnectionState.DISCONNECTED
                _state.value = RootUiState.Onboarding
            }
        }
    }

    private suspend fun activateStandalone() {
        musicSource = standalone
        MusicSourceLocator.set(standalone)
        InMemoryProviderRegistry.unregister(MusicProviderId.SERVER)
        InMemoryProviderRegistry.register(
            MusicProvider(
                id = MusicProviderId.LOCAL,
                label = standalone.label,
                catalogue = MusicSourceCatalogueAdapter(standalone),
                library = MusicSourceLibraryAdapter(standalone),
                streamResolver = MusicSourceStreamResolverAdapter(standalone),
                history = StandaloneHistoryAdapter(standalone),
            ),
        )
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
        InMemoryProviderRegistry.unregister(MusicProviderId.LOCAL)
        InMemoryProviderRegistry.register(
            MusicProvider(
                id = MusicProviderId.SERVER,
                label = source.label,
                catalogue = MusicSourceCatalogueAdapter(source),
                library = MusicSourceLibraryAdapter(source),
                streamResolver = MusicSourceStreamResolverAdapter(source),
                // Pas de HistoryRepository serveur : aucune route consommée pour pousser
                // un événement d'écoute côté Android en mode Lié (limite déjà documentée
                // dans domain/Repositories.kt, HistoryRepository).
            ),
        )
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

    /** Bibliothèque — passe par ProviderRegistry plutôt que par `musicSource` directement
     * (première migration réelle du plan §67 : le mécanisme cumulatif existe même si un
     * seul provider est actif aujourd'hui, mode exclusif hérité — voir ProviderRegistry.kt).
     * Union des bibliothèques de tous les providers actifs ; avec un seul provider actif,
     * résultat strictement identique à l'ancien `source.library()`. */
    fun refreshLibrary() {
        val providers = InMemoryProviderRegistry.observeActive().value
        if (providers.isEmpty()) return
        viewModelScope.launch {
            val results = providers.map { it.library.library() }
            if (results.all { it.isFailure }) {
                _error.value = results.first().exceptionOrNull()?.message
                return@launch
            }
            _library.value = results.mapNotNull { it.getOrNull() }.flatten()
        }
    }

    fun search(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        val providers = InMemoryProviderRegistry.observeActive().value
        if (providers.isEmpty()) return
        viewModelScope.launch {
            val results = providers.map { it.catalogue.search(query) }
            if (results.all { it.isFailure }) {
                _error.value = results.first().exceptionOrNull()?.message
                return@launch
            }
            _searchResults.value = results.mapNotNull { it.getOrNull() }.flatten()
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
            InMemoryProviderRegistry.clear()
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
