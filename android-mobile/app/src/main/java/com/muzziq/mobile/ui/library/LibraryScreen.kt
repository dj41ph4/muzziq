package com.muzziq.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.ui.AlbumUi
import com.muzziq.mobile.ui.ArtistUi
import com.muzziq.mobile.ui.common.AlbumCard
import com.muzziq.mobile.ui.common.ArtistCircleCard
import com.muzziq.mobile.ui.common.EmptyState
import com.muzziq.mobile.ui.common.HeroCard
import com.muzziq.mobile.ui.common.MuzziQFilterChip
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.palette.rememberDominantColor
import com.muzziq.mobile.ui.theme.MuzziQColors

private enum class LibraryView { FAVORITES, TRACKS, ARTISTS, ALBUMS }

/**
 * Bibliothèque à plat + browse artiste/album (§17) — ce dernier ne couvre que
 * la bibliothèque locale déjà scannée (serveur ou standalone), aucun browse du
 * catalogue YouTube Music (recherche uniquement, voir
 * android-mobile/docs/online-streaming-status.md).
 */
@Composable
fun LibraryScreen(
    mode: AppMode,
    tracks: List<Track>,
    favoriteTracks: List<Track> = emptyList(),
    artists: List<ArtistUi>,
    albums: List<AlbumUi>,
    browseTracks: List<Track>,
    busy: Boolean,
    contentPadding: PaddingValues,
    onRescan: () -> Unit,
    onSelectArtist: (String) -> Unit,
    onSelectAlbum: (String) -> Unit,
    onCloseBrowseDetail: () -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    var view by remember { mutableStateOf(LibraryView.TRACKS) }
    var detailTitle by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        if (detailTitle != null) {
            // Cover réelle du premier morceau si connue (standalone : vient du scan local ;
            // mode Lié : toujours null aujourd'hui, voir AppViewModel.openArtist/openAlbum —
            // limite déjà documentée, jamais une cover inventée). Le dégradé HeroCard retombe
            // simplement sur MuzziQColors.Surface quand aucune couleur dominante n'est extraite.
            val heroArtwork = browseTracks.firstOrNull()?.artworkUrl
            val dominant = rememberDominantColor(heroArtwork)
            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                LazyColumn {
                    item {
                        HeroCard(
                            title = detailTitle.orEmpty(),
                            subtitle = if (browseTracks.isNotEmpty()) "${browseTracks.size} morceau(x)" else null,
                            artworkUrl = heroArtwork,
                            dominant = dominant,
                        )
                    }
                    items(browseTracks, key = { it.id }) { track -> TrackRow(track, onClick = { onTrackClick(track) }) }
                }
                IconButton(
                    onClick = { detailTitle = null; onCloseBrowseDetail() },
                    modifier = Modifier
                        .padding(8.dp)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape),
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour", tint = MuzziQColors.TextPrimary)
                }
            }
            return@Box
        }

        LazyColumn(contentPadding = contentPadding) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Bibliothèque", color = MuzziQColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (mode == AppMode.STANDALONE) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.padding(4.dp), color = MuzziQColors.Brand, strokeWidth = 2.dp)
                        else TextButton(onClick = onRescan) { Text("Rescanner", color = MuzziQColors.Brand) }
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LibraryView.entries.toList()) { v ->
                        MuzziQFilterChip(
                            label = when (v) {
                                LibraryView.FAVORITES -> "Titres likés"
                                LibraryView.TRACKS -> "Titres"
                                LibraryView.ARTISTS -> "Artistes"
                                LibraryView.ALBUMS -> "Albums"
                            },
                            selected = v == view,
                            onClick = { view = v },
                        )
                    }
                }
            }
            when (view) {
                LibraryView.FAVORITES -> {
                    if (favoriteTracks.isEmpty()) item { EmptyState("Aucun titre liké. Utilise le cœur depuis la lecture ou la recherche.") }
                    items(favoriteTracks, key = { it.id }) { track -> TrackRow(track, onClick = { onTrackClick(track) }) }
                }
                LibraryView.TRACKS -> items(tracks, key = { it.id }) { track -> TrackRow(track, onClick = { onTrackClick(track) }) }
                LibraryView.ARTISTS -> {
                    if (artists.isEmpty()) item { EmptyBrowseNotice(mode) }
                    // Grille 2 colonnes non-lazy (même pattern que les raccourcis Home,
                    // voir HomeScreen) plutôt qu'un LazyVerticalGrid imbriqué dans ce
                    // LazyColumn — évite le double scrollable.
                    items(artists.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            row.forEach { artist ->
                                Column {
                                    ArtistCircleCard(
                                        name = artist.name,
                                        artworkUrl = null,
                                        onClick = { detailTitle = artist.name; onSelectArtist(artist.id) },
                                    )
                                    Text(
                                        "${artist.trackCount} morceau(x) · ${artist.albumCount} album(s)",
                                        color = MuzziQColors.TextMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                LibraryView.ALBUMS -> {
                    if (albums.isEmpty()) item { EmptyBrowseNotice(mode) }
                    items(albums.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            row.forEach { album ->
                                AlbumCard(
                                    title = album.title,
                                    artist = album.artist,
                                    artworkUrl = null,
                                    onClick = { detailTitle = album.title; onSelectAlbum(album.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBrowseNotice(mode: AppMode) {
    EmptyState(
        if (mode == AppMode.STANDALONE) "Aucun morceau scanné pour l'instant."
        else "Bibliothèque serveur vide, ou pas encore chargée.",
    )
}
