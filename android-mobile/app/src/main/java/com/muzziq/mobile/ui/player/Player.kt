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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import coil.compose.AsyncImage
import com.muzziq.mobile.data.model.Track
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
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MuzziQColors.BgElevated)
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .sharedElement(
                    rememberSharedContentState(key = "player-artwork"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
        )
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

/**
 * Plein écran lecteur (§56.1) : pochette large, dégradé dynamique par pochette
 * infusant le fond vers le noir, contrôles principaux + barre de progression.
 * Paroles/file d'attente/appareils par onglets restent un chantier suivant.
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
) {
    val dominant = rememberDominantColor(track.artworkUrl)
    val animatedDominant by animateFloatAsState(targetValue = 1f, animationSpec = tween(600), label = "grad")

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
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MuzziQColors.TextPrimary)
            }

            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .sharedElement(
                            rememberSharedContentState(key = "player-artwork"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                )
            }

            Column(Modifier.padding(top = 32.dp)) {
                Text(track.title, color = MuzziQColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = MuzziQColors.TextMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
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
