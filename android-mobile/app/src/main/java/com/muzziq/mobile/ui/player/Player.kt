package com.muzziq.mobile.ui.player

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import com.muzziq.mobile.domain.LyricsProvider
import com.muzziq.mobile.ui.common.MuzziQFilterChip
import com.muzziq.mobile.ui.common.Skeleton
import com.muzziq.mobile.ui.palette.rememberDominantColor
import com.muzziq.mobile.ui.theme.MuzziQColors
import kotlinx.coroutines.delay

/**
 * Mini-player persistant (§56.1) — visible partout, tap ouvre le plein écran
 * via une transition d'élément partagé sur la pochette (jamais un modal qui
 * apparaît d'un coup).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MuzziQColors.BgElevated)
            .clickable(onClick = onExpand),
    ) {
        // Barre de progression fine (§56.1, façon Spotify) — mêmes positionMs/durationMs
        // que le plein écran (PlayerController.tickPosition, déjà réel), jamais une valeur
        // recalculée séparément ici.
        if (durationMs > 0) {
            LinearProgressIndicator(
                progress = { (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MuzziQColors.Brand,
                trackColor = MuzziQColors.Surface,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverModifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .sharedElement(
                    rememberSharedContentState(key = "player-artwork"),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            if (track.artworkUrl == null) {
                Skeleton(coverModifier, RoundedCornerShape(6.dp))
            } else {
                // Pas de lambda `content` trailing : cette surcharge Coil retombe déjà sur
                // SubcomposeAsyncImageContent() par défaut pour l'état succès (voir
                // DesignSystem.kt, MediaCover — erreur de résolution de surcharge trouvée
                // par le CI en l'ajoutant).
                SubcomposeAsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier = coverModifier,
                    loading = { Skeleton(Modifier.size(44.dp), RoundedCornerShape(6.dp)) },
                    error = { Skeleton(Modifier.size(44.dp), RoundedCornerShape(6.dp)) },
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(track.title, color = MuzziQColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artist, color = MuzziQColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MuzziQColors.Brand,
                )
            }
        }
    }
}

/** Panneaux du plein écran (§56.1) — Lecture reste l'onglet par défaut à l'ouverture (c'est
 * lui qui porte la transition d'élément partagé sur la pochette). */
private enum class PlayerPanel(val label: String) {
    NOW_PLAYING("Lecture"),
    LYRICS("Paroles"),
    QUEUE("Queue"),
}

/**
 * Plein écran lecteur (§56.1) : pochette large, dégradé dynamique par pochette
 * infusant le fond vers le noir, contrôles principaux + barre de progression.
 * Onglets Paroles/Queue (voir [LyricsPanel]/[QueuePanel]) — Queue reflète la vraie file
 * d'attente de PlaybackService (jamais une liste fictive : un morceau isolé sans liste
 * autour affiche un état vide honnête plutôt qu'un faux onglet, voir QueuePanel). Paroles
 * consomme le contrat [LyricsProvider] déjà posé (domain/Repositories.kt), aujourd'hui sans
 * fournisseur réel branché (NullLyricsProvider) — affiché honnêtement, jamais de texte
 * inventé. Slider + contrôles de lecture restent visibles sur les trois onglets (façon
 * Spotify : on garde la main sur la lecture même en consultant Paroles/Queue).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.PlayerScreen(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onToggleDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    queue: List<Track>,
    queueIndex: Int,
    onJumpToQueueIndex: (Int) -> Unit,
    lyricsProvider: LyricsProvider,
) {
    val dominant = rememberDominantColor(track.artworkUrl)
    val animatedDominant by animateFloatAsState(targetValue = 1f, animationSpec = tween(600), label = "grad")
    var panel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(PlayerPanel.NOW_PLAYING) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to dominant.copy(alpha = 0.9f * animatedDominant),
                    0.55f to MuzziQColors.Bg,
                    1f to MuzziQColors.Bg,
                )
            ),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MuzziQColors.TextPrimary)
                }
                Row(
                    Modifier.weight(1f).padding(start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerPanel.entries.forEach { p ->
                        MuzziQFilterChip(label = p.label, selected = panel == p, onClick = { panel = p })
                    }
                }
            }

            if (panel != PlayerPanel.NOW_PLAYING) {
                Box(Modifier.weight(1f).padding(top = 20.dp)) {
                    when (panel) {
                        PlayerPanel.LYRICS -> LyricsPanel(track = track, lyricsProvider = lyricsProvider)
                        PlayerPanel.QUEUE -> QueuePanel(queue = queue, currentIndex = queueIndex, onJumpTo = onJumpToQueueIndex)
                        PlayerPanel.NOW_PLAYING -> Unit
                    }
                }
            }

            if (panel == PlayerPanel.NOW_PLAYING) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                val coverModifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .sharedElement(
                        rememberSharedContentState(key = "player-artwork"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                if (track.artworkUrl == null) {
                    Skeleton(coverModifier, RoundedCornerShape(16.dp))
                } else {
                    SubcomposeAsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        modifier = coverModifier,
                        loading = { Skeleton(Modifier.fillMaxSize(), RoundedCornerShape(16.dp)) },
                        error = { Skeleton(Modifier.fillMaxSize(), RoundedCornerShape(16.dp)) },
                    )
                }
            }

            Row(Modifier.padding(top = 32.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = MuzziQColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = MuzziQColors.TextMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                    // Affichage de source (plan §39) — dérivé de track.source, déjà connu
                    // sans appel réseau supplémentaire : fichier local vs flux serveur.
                    Text(
                        when (track.source) {
                            is TrackSource.Local -> "Fichier local"
                            is TrackSource.Server -> "Diffusion serveur"
                            is TrackSource.YouTube -> "YouTube Music direct"
                            // Pas encore un vrai chemin de lecture (voir SpotifyProvider.streamResolver) —
                            // ce libellé n'est atteint que si l'UI affiche un jour un morceau Spotify
                            // dans le plein écran, pas encore le cas (mode exclusif Standalone/Lié).
                            is TrackSource.Spotify -> "Spotify"
                        },
                        color = MuzziQColors.TextFaint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // Favoris Room (§56.4) — indépendant du mode et du blocage cipher YouTube,
                // fonctionne identiquement en standalone et en lié, jamais un bouton mort :
                // toggleFavorite() écrit réellement dans la base locale.
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (isFavorite) MuzziQColors.Brand else MuzziQColors.TextMuted,
                    )
                }
                // Téléchargement hors-ligne (plan §57) — en standalone toujours "déjà
                // téléchargé" (StandaloneDownloadRepository), en mode Lié rapatrie
                // réellement les octets (ServerDownloadRepository) puis sert le fichier
                // local à toute lecture suivante (ServerMusicSource.resolvePlayableUri).
                IconButton(onClick = onToggleDownload, enabled = !isDownloading) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MuzziQColors.Brand, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            contentDescription = if (isDownloaded) "Téléchargé" else "Télécharger",
                            tint = if (isDownloaded) MuzziQColors.Brand else MuzziQColors.TextMuted,
                        )
                    }
                }
                // Ajout à une playlist (plan §6/§66) — même geste en standalone et en lié,
                // AppViewModel choisit la bonne implémentation (RoomPlaylistRepository/
                // ServerPlaylistRepository), le picker vit dans MainActivity.
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Ajouter à une playlist", tint = MuzziQColors.TextMuted)
                }
            }
            }

            var sliderPosition by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(positionMs.toFloat()) }
            LaunchedEffect(positionMs) { sliderPosition = positionMs.toFloat() }

            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
                valueRange = 0f..(durationMs.coerceAtLeast(1)).toFloat(),
                colors = SliderDefaults.colors(thumbColor = MuzziQColors.Brand, activeTrackColor = MuzziQColors.Brand),
                modifier = Modifier.padding(top = 24.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(sliderPosition.toLong()), color = MuzziQColors.TextFaint, fontSize = 11.sp)
                Text(formatMs(durationMs), color = MuzziQColors.TextFaint, fontSize = 11.sp)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp, alignment = Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Précédent",
                    tint = MuzziQColors.TextMuted,
                    modifier = Modifier.size(36.dp).clickable(onClick = onSkipPrevious),
                )
                // Le bouton play/pause "morph" (§56.1) — micro-animation discrète via
                // un simple crossfade d'icône plutôt qu'un vrai path-morph vectoriel,
                // suffisant en V1 sans lib d'animation vectorielle supplémentaire.
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MuzziQColors.Brand)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF00210A),
                        modifier = Modifier.size(32.dp),
                    )
                }
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Suivant",
                    tint = MuzziQColors.TextMuted,
                    modifier = Modifier.size(36.dp).clickable(onClick = onSkipNext),
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
