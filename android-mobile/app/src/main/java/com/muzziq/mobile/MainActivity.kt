package com.muzziq.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.ui.AppViewModel
import com.muzziq.mobile.ui.RootUiState
import com.muzziq.mobile.ui.home.HomeScreen
import com.muzziq.mobile.ui.library.LibraryScreen
import com.muzziq.mobile.ui.onboarding.OnboardingScreen
import com.muzziq.mobile.ui.player.MiniPlayer
import com.muzziq.mobile.ui.player.PlayerScreen
import com.muzziq.mobile.ui.search.SearchScreen
import com.muzziq.mobile.ui.theme.MuzziQColors
import com.muzziq.mobile.ui.theme.MuzziQTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.rescanStandaloneLibrary() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            MuzziQTheme {
                MuzziQApp(
                    vm = vm,
                    onRequestAudioPermission = { requestAudioPermission.launch(audioPermission()) },
                )
            }
        }
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
}

private enum class Tab(val label: String) { HOME("Accueil"), SEARCH("Recherche"), LIBRARY("Bibliothèque") }

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
            val searchResults by vm.searchResults.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val currentTrack by vm.player.currentTrack.collectAsStateWithLifecycle()
            val isPlaying by vm.player.isPlaying.collectAsStateWithLifecycle()
            val positionMs by vm.player.positionMs.collectAsStateWithLifecycle()
            val durationMs by vm.player.durationMs.collectAsStateWithLifecycle()

            LaunchedEffect(currentTrack) {
                while (currentTrack != null) {
                    vm.player.tickPosition()
                    delay(500)
                }
            }

            SharedTransitionLayout {
                Scaffold(
                    containerColor = MuzziQColors.Bg,
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
                                        isPlaying = isPlaying,
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        onTogglePlayPause = { vm.player.togglePlayPause() },
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
                    when (Tab.entries[tab]) {
                        Tab.HOME -> HomeScreen(s.mode, library, padding) { vm.play(it) }
                        Tab.SEARCH -> SearchScreen(s.mode, query, { query = it; vm.search(it) }, searchResults, padding) { vm.play(it) }
                        Tab.LIBRARY -> LibraryScreen(s.mode, library, busy, padding, onRescan = { vm.rescanStandaloneLibrary() }) { vm.play(it) }
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
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            animatedVisibilityScope = this@AnimatedVisibility,
                            onTogglePlayPause = { vm.player.togglePlayPause() },
                            onSeek = { vm.player.seekTo(it) },
                            onCollapse = { expanded = false },
                        )
                    }
                }
            }
        }
    }
}
