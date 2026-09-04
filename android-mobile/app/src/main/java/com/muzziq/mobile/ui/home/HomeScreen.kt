package com.muzziq.mobile.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.domain.MusicProviderId
import com.muzziq.mobile.domain.PlaylistSummary
import com.muzziq.mobile.ui.HomeRowUi
import com.muzziq.mobile.ui.common.EmptyState
import com.muzziq.mobile.ui.common.HorizontalShelf
import com.muzziq.mobile.ui.common.ModeBadge
import com.muzziq.mobile.ui.common.MuzziQBackdrop
import com.muzziq.mobile.ui.common.MuzziQTopBar
import com.muzziq.mobile.ui.common.QuickTile
import com.muzziq.mobile.ui.common.SectionTitle
import com.muzziq.mobile.ui.common.SquareMediaCard
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors
import java.time.LocalTime

@Composable
fun HomeScreen(
    mode: AppMode,
    tracks: List<Track>,
    homeRows: List<HomeRowUi>,
    contentPadding: PaddingValues,
    playlists: List<PlaylistSummary> = emptyList(),
    onTrackClick: (Track) -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
) {
    val greeting = remember(mode) { greetingForNow() }
    val quickPlaylists = playlists.filter { it.provider != MusicProviderId.SPOTIFY }.take(6)
    val featuredTrack = tracks.firstOrNull()

    MuzziQBackdrop(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = contentPadding) {
            item {
                MuzziQTopBar(
                    title = greeting,
                    subtitle = if (mode == AppMode.STANDALONE) "Ton espace musical" else "Ton espace MuzziQ",
                    trailingContent = { ModeBadge(if (mode == AppMode.STANDALONE) "LOCAL" else "SERVEUR") },
                )
            }

            if (featuredTrack != null) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 },
                    ) {
                        FeaturedListeningCard(track = featuredTrack, onClick = { onTrackClick(featuredTrack) })
                    }
                }
            }

            if (quickPlaylists.isNotEmpty()) {
                item { SectionTitle("Reprendre l'écoute") }
                lazyColumnItems(quickPlaylists.chunked(2)) { rowPair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowPair.forEach { playlist ->
                            QuickTile(
                                title = playlist.name,
                                artworkUrl = null,
                                modifier = Modifier.weight(1f).height(64.dp),
                                onClick = { onOpenPlaylist(playlist.id) },
                            )
                        }
                        if (rowPair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (homeRows.isNotEmpty()) {
                homeRows.forEach { row ->
                    item(key = "row-title-${row.id}") { SectionTitle(row.title, actionLabel = "Tout voir") }
                    item(key = "row-shelf-${row.id}") {
                        HorizontalShelf(items = row.tracks, key = { "row-${row.id}-${it.id}" }) { track ->
                            SquareMediaCard(title = track.title, subtitle = track.artist, artworkUrl = track.artworkUrl, onClick = { onTrackClick(track) })
                        }
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    EmptyState(
                        if (mode == AppMode.STANDALONE) "Aucun morceau trouvé sur l'appareil — vérifie la permission musique dans Réglages."
                        else "Bibliothèque vide — ajoute des morceaux depuis la recherche.",
                    )
                }
            } else {
                item { SectionTitle("Ta bibliothèque", actionLabel = "Explorer") }
                lazyColumnItems(tracks.take(12), key = { it.id }) { track -> TrackRow(track, onClick = { onTrackClick(track) }) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FeaturedListeningCard(track: Track, onClick: () -> Unit) {
    val scale by animateFloatAsState(1f, tween(700), label = "featured-scale")
    Row(
        Modifier
            .padding(horizontal = 18.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(MuzziQColors.AccentViolet.copy(alpha = 0.9f), MuzziQColors.BrandDark, MuzziQColors.SurfaceRaised)))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size((68 * scale).dp).clip(RoundedCornerShape(16.dp)).background(MuzziQColors.Bg.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MuzziQColors.TextPrimary, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text("Pour toi aujourd'hui", color = MuzziQColors.TextPrimary.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(track.title, color = MuzziQColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            Text(track.artist, color = MuzziQColors.TextPrimary.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(MuzziQColors.TextPrimary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Lire", tint = MuzziQColors.Bg, modifier = Modifier.size(26.dp))
        }
    }
}

private fun greetingForNow(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 5 -> "Bonne nuit"
        hour < 12 -> "Bonjour"
        hour < 18 -> "Bon après-midi"
        else -> "Bonsoir"
    }
}
