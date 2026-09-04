package com.muzziq.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muzziq.mobile.BuildConfig
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.domain.MusicProviderId
import com.muzziq.mobile.ui.AppViewModel
import com.muzziq.mobile.ui.RootUiState
import com.muzziq.mobile.ui.history.HistoryScreen
import com.muzziq.mobile.ui.home.HomeScreen
import com.muzziq.mobile.ui.library.LibraryScreen
import com.muzziq.mobile.ui.onboarding.OnboardingScreen
import com.muzziq.mobile.ui.player.MiniPlayer
import com.muzziq.mobile.ui.player.PlayerScreen
import com.muzziq.mobile.ui.playlists.PlaylistsScreen
import com.muzziq.mobile.ui.search.SearchScreen
import com.muzziq.mobile.ui.settings.SettingsScreen
import com.muzziq.mobile.ui.theme.MuzziQColors
import com.muzziq.mobile.ui.theme.MuzziQTheme
import com.muzziq.mobile.playback.CastController
import kotlinx.coroutines.delay

/** Schéma/host du deep link de retour Spotify — doit rester identique à
 * SpotifyAuthManager.REDIRECT_URI ("muzziq://spotify-callback") et à
 * l'intent-filter d'AndroidManifest.xml. Dupliqué ici en constantes plutôt que
 * réimporté depuis providers/spotify pour ne pas coupler MainActivity au
 * module Spotify pour une simple comparaison de scheme/host ; les trois
 * emplacements sont commentés les uns vers les autres pour rester synchronisés. */
private const val SPOTIFY_CALLBACK_SCHEME = "muzziq"
private const val SPOTIFY_CALLBACK_HOST = "spotify-callback"

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.rescanStandaloneLibrary() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Démarrage à froid via le deep link (rare pour un retour PKCE — Custom Tabs
        // relance normalement la tâche existante via onNewIntent grâce à singleTop —
        // mais couvert quand même : un process tué pendant le trajet Custom Tab
        // relancerait l'Activity depuis zéro avec l'intent de retour comme intent
        // initial). vm est résolu ici (délégué `by viewModels()`), donc disponible.
        handleSpotifyCallbackIntent(intent)
        setContent {
            MuzziQTheme {
                MuzziQApp(
                    vm = vm,
                    onRequestAudioPermission = { requestAudioPermission.launch(audioPermission()) },
                )
            }
        }
    }

    /** Cas normal : `singleTop` + Custom Tabs relance la tâche existante, Android
     * appelle onNewIntent() plutôt que de recréer l'Activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyCallbackIntent(intent)
    }

    /** Filtre strictement sur le scheme/host du deep link Spotify (voir constantes en
     * tête de fichier) — tout autre intent (lancement normal depuis le launcher,
     * notification média, etc.) est ignoré ici, jamais transmis à handleSpotifyCallback. */
    private fun handleSpotifyCallbackIntent(intent: Intent) {
        val data: Uri = intent.data ?: return
        if (data.scheme == SPOTIFY_CALLBACK_SCHEME && data.host == SPOTIFY_CALLBACK_HOST) {
            vm.handleSpotifyCallback(data)
        }
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
}

private enum class Tab(val label: String) { HOME("Accueil"), SEARCH("Recherche"), LIBRARY("Bibliothèque"), PLAYLISTS("Playlists"), HISTORY("Historique") }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MuzziQApp(vm: AppViewModel, onRequestAudioPermission: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        RootUiState.Loading -> Box(Modifier.fillMaxSize())
        RootUiState.Onboarding -> {
            val busy by vm.busy.collectAsStateWithLifecycle()
            val error by vm.error.collectAsStateWithLifecycle()
            OnboardingScreen(
                busy = busy,
                error = error,
                onChooseStandalone = {
                    vm.chooseStandalone()
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
                        else Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.rescanStandaloneLibrary() else onRequestAudioPermission()
                },
                onChooseLinked = { url -> vm.chooseLinked(url) },
            )
        }
        is RootUiState.Ready -> {
            var tab by remember { mutableIntStateOf(0) }
            var query by remember { mutableStateOf("") }
            var expanded by remember { mutableStateOf(false) }

            val library by vm.library.collectAsStateWithLifecycle()
            val artists by vm.artists.collectAsStateWithLifecycle()
            val albums by vm.albums.collectAsStateWithLifecycle()
            val browseTracks by vm.browseTracks.collectAsStateWithLifecycle()
            val homeRows by vm.homeRows.collectAsStateWithLifecycle()
            val searchResults by vm.searchResults.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val currentTrack by vm.player.currentTrack.collectAsStateWithLifecycle()
            val isPlaying by vm.player.isPlaying.collectAsStateWithLifecycle()
            val positionMs by vm.player.positionMs.collectAsStateWithLifecycle()
            val durationMs by vm.player.durationMs.collectAsStateWithLifecycle()
            val playerQueue by vm.player.queue.collectAsStateWithLifecycle()
            val playerQueueIndex by vm.player.queueIndex.collectAsStateWithLifecycle()
            val favoriteIds by vm.favoriteTrackIds.collectAsStateWithLifecycle()
            val downloadedIds by vm.downloadedTrackIds.collectAsStateWithLifecycle()
            val downloadingIds by vm.downloadingTrackIds.collectAsStateWithLifecycle()
            val playlists by vm.playlists.collectAsStateWithLifecycle()
            val openPlaylistId by vm.openPlaylistId.collectAsStateWithLifecycle()
            val playlistTracks by vm.playlistTracks.collectAsStateWithLifecycle()
            val history by vm.history.collectAsStateWithLifecycle()
            val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
            var showPlaylistPicker by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            val castController = remember(context) { CastController(context) }
            val isCasting by castController.isCasting.collectAsStateWithLifecycle()
            val castIsPlaying by castController.isPlayingState.collectAsStateWithLifecycle()
            val castPositionMs by castController.positionMs.collectAsStateWithLifecycle()
            val castDurationMs by castController.durationMs.collectAsStateWithLifecycle()

            DisposableEffect(castController) {
                castController.initialize()
                onDispose { castController.release() }
            }

            // Le bouton système ouvre le sélecteur Cast. Une fois la session
            // réellement établie, le CastPlayer reçoit l'URL déjà résolue et
            // sa position courante ; le lecteur local est alors mis en pause.
            var wasCasting by remember { mutableStateOf(false) }
            LaunchedEffect(isCasting, currentTrack?.id) {
                if (isCasting) {
                    vm.player.currentMediaItem()?.let { item ->
                        castController.loadCurrent(item, positionMs, isPlaying)
                        if (isPlaying) vm.player.togglePlayPause()
                    }
                } else if (wasCasting) {
                    vm.player.seekTo(castController.currentPosition())
                    if (castController.wasPlayingBeforeCast()) vm.player.togglePlayPause()
                }
                wasCasting = isCasting
            }

            LaunchedEffect(isCasting) {
                while (isCasting) {
                    castController.refresh()
                    delay(500)
                }
            }

            val shownIsPlaying = if (isCasting) castIsPlaying else isPlaying
            val shownPositionMs = if (isCasting) castPositionMs else positionMs
            val shownDurationMs = if (isCasting) castDurationMs else durationMs

            LaunchedEffect(currentTrack) {
                while (currentTrack != null) {
                    vm.player.tickPosition()
                    delay(500)
                }
            }

            SharedTransitionLayout {
                Scaffold(
                    containerColor = MuzziQColors.Bg,
                    topBar = {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Réglages", tint = MuzziQColors.TextMuted)
                            }
                        }
                    },
                    bottomBar = {
                        Column {
                            currentTrack?.let { track ->
                                AnimatedVisibility(
                                    visible = !expanded,
                                    enter = slideInVertically { it },
                                    exit = slideOutVertically { it },
                                ) {
                                    MiniPlayer(
                                        track = track,
                                        isPlaying = shownIsPlaying,
                                        positionMs = shownPositionMs,
                                        durationMs = shownDurationMs,
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        onTogglePlayPause = {
                                            if (isCasting) {
                                                if (castController.isPlaying()) castController.pause() else castController.play()
                                            } else vm.player.togglePlayPause(vm.musicSource)
                                        },
                                        onExpand = { expanded = true },
                                    )
                                }
                            }
                            NavigationBar(containerColor = MuzziQColors.BgElevated) {
                                Tab.entries.forEachIndexed { index, t ->
                                    NavigationBarItem(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        icon = {
                                            Icon(
                                                when (t) {
                                                    Tab.HOME -> Icons.Rounded.Home
                                                    Tab.SEARCH -> Icons.Rounded.Search
                                                    Tab.LIBRARY -> Icons.Rounded.LibraryMusic
                                                    Tab.PLAYLISTS -> Icons.Rounded.QueueMusic
                                                    Tab.HISTORY -> Icons.Rounded.History
                                                },
                                                contentDescription = t.label,
                                            )
                                        },
                                        label = { Text(t.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    // Crossfade plutôt qu'un changement brutal de contenu au tap sur un
                    // onglet de la barre de navigation (§56.1, finition visuelle) — reste
                    // volontairement discret (pas de slide directionnel) car les onglets
                    // n'ont pas d'ordre hiérarchique entre eux.
                    AnimatedContent(
                        targetState = tab,
                        label = "tab-content",
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    ) { selectedTab ->
                    when (Tab.entries[selectedTab]) {
                        Tab.HOME -> HomeScreen(
                            mode = s.mode,
                            tracks = library,
                            homeRows = homeRows,
                            contentPadding = padding,
                            playlists = playlists,
                            onTrackClick = { vm.playFrom(library, it) },
                            onOpenPlaylist = { playlistId -> vm.openPlaylist(playlistId); tab = Tab.PLAYLISTS.ordinal },
                        )
                        Tab.SEARCH -> SearchScreen(s.mode, query, { query = it; vm.search(it) }, searchResults, padding) { vm.playFrom(searchResults, it) }
                        Tab.LIBRARY -> LibraryScreen(
                            mode = s.mode,
                            tracks = library,
                            artists = artists,
                            albums = albums,
                            browseTracks = browseTracks,
                            busy = busy,
                            contentPadding = padding,
                            onRescan = { vm.rescanStandaloneLibrary() },
                            onSelectArtist = { vm.openArtist(it) },
                            onSelectAlbum = { vm.openAlbum(it) },
                            onCloseBrowseDetail = { vm.closeBrowseDetail() },
                            onTrackClick = { vm.playFrom(if (browseTracks.isNotEmpty()) browseTracks else library, it) },
                        )
                        Tab.PLAYLISTS -> PlaylistsScreen(
                            playlists = playlists,
                            openPlaylistId = openPlaylistId,
                            playlistTracks = playlistTracks,
                            contentPadding = padding,
                            onCreatePlaylist = { vm.createPlaylist(it) },
                            onDeletePlaylist = { vm.deletePlaylist(it) },
                            onOpenPlaylist = { vm.openPlaylist(it) },
                            onClosePlaylist = { vm.closePlaylist() },
                            onRemoveTrack = { playlistId, trackId -> vm.removeFromPlaylist(playlistId, trackId) },
                            onTrackClick = { vm.playFrom(playlistTracks, it) },
                        )
                        Tab.HISTORY -> HistoryScreen(
                            mode = s.mode,
                            entries = history,
                            contentPadding = padding,
                            onTrackClick = { vm.playFrom(history.map { entry -> entry.track }, it) },
                        )
                    }
                    }
                }

                currentTrack?.let { track ->
                    AnimatedVisibility(
                        visible = expanded,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        PlayerScreen(
                            track = track,
                            isPlaying = shownIsPlaying,
                            positionMs = shownPositionMs,
                            durationMs = shownDurationMs,
                            animatedVisibilityScope = this@AnimatedVisibility,
                            onTogglePlayPause = {
                                if (isCasting) {
                                    if (castController.isPlaying()) castController.pause() else castController.play()
                                } else vm.player.togglePlayPause(vm.musicSource)
                            },
                            onSeek = { if (isCasting) castController.seekTo(it) else vm.player.seekTo(it) },
                            onCollapse = { expanded = false },
                            onSkipNext = { vm.player.skipNext() },
                            onSkipPrevious = { vm.player.skipPrevious() },
                            isFavorite = track.id in favoriteIds,
                            onToggleFavorite = { vm.toggleFavorite(track) },
                            isDownloaded = track.id in downloadedIds,
                            isDownloading = track.id in downloadingIds,
                            onToggleDownload = { vm.requestDownload(track) },
                            onAddToPlaylist = { showPlaylistPicker = true },
                            queue = playerQueue,
                            queueIndex = playerQueueIndex,
                            onJumpToQueueIndex = { vm.player.jumpToQueueIndex(it) },
                            lyricsProvider = vm.lyricsProvider,
                            isCasting = isCasting,
                        )
                    }
                }

                if (showPlaylistPicker) {
                    val track = currentTrack
                    // Spotify (lecture seule, §58) : jamais proposée ici, ajouter un
                    // morceau échouerait toujours explicitement côté SpotifyProvider —
                    // filtrée en amont plutôt que de laisser l'utilisateur taper dans le
                    // vide sur une option qui ne peut jamais réussir.
                    val addablePlaylists = playlists.filter { it.provider != MusicProviderId.SPOTIFY }
                    AlertDialog(
                        onDismissRequest = { showPlaylistPicker = false },
                        title = { Text("Ajouter à une playlist", color = MuzziQColors.TextPrimary) },
                        text = {
                            if (addablePlaylists.isEmpty()) {
                                Text("Aucune playlist — crée-en une depuis l'onglet Playlists.", color = MuzziQColors.TextMuted)
                            } else {
                                Column {
                                    addablePlaylists.forEach { playlist ->
                                        Text(
                                            playlist.name,
                                            color = MuzziQColors.TextPrimary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (track != null) vm.addToPlaylist(playlist.id, track)
                                                    showPlaylistPicker = false
                                                }
                                                .padding(vertical = 12.dp),
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            Text(
                                "Fermer",
                                color = MuzziQColors.TextMuted,
                                modifier = Modifier.clickable { showPlaylistPicker = false }.padding(8.dp),
                            )
                        },
                        containerColor = MuzziQColors.Surface,
                    )
                }

                // Glisse depuis le bas + fondu plutôt qu'une apparition brutale (§56.1) —
                // même famille de transition que le plein écran lecteur juste au-dessus.
                AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 8 },
                ) {
                    val spotifyAccount by vm.spotifyAccount.collectAsStateWithLifecycle()
                    val spotifyBusy by vm.spotifyBusy.collectAsStateWithLifecycle()
                    val spotifyError by vm.spotifyError.collectAsStateWithLifecycle()
                    SettingsScreen(
                        mode = s.mode,
                        serverUrl = serverUrl,
                        appVersion = BuildConfig.VERSION_NAME,
                        onClose = { showSettings = false },
                        onChangeMode = {
                            showSettings = false
                            vm.backToOnboarding()
                        },
                        spotifyAccount = spotifyAccount,
                        spotifyBusy = spotifyBusy,
                        spotifyError = spotifyError,
                        onConnectSpotify = {
                            // Onglet sécurisé (jamais une WebView maison) — l'utilisateur voit
                            // la vraie barre d'adresse accounts.spotify.com. Rien n'est ouvert
                            // si isConfigured() est faux côté vm (spotifyLoginUri() renvoie null).
                            val uri = vm.spotifyLoginUri()
                            if (uri != null) {
                                CustomTabsIntent.Builder().build().launchUrl(context, uri)
                            }
                        },
                        onDisconnectSpotify = { vm.disconnectSpotify() },
                    )
                }
            }
        }
    }
}
