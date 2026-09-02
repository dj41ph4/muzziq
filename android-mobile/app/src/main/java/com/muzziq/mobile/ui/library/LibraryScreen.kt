package com.muzziq.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.ui.AlbumUi
import com.muzziq.mobile.ui.ArtistUi
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors

private enum class LibraryView { TRACKS, ARTISTS, ALBUMS }

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
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { detailTitle = null; onCloseBrowseDetail() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour", tint = MuzziQColors.TextPrimary)
                    }
                    Text(detailTitle.orEmpty(), color = MuzziQColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                LazyColumn {
                    items(browseTracks, key = { it.id }) { track -> TrackRow(track) { onTrackClick(track) } }
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryView.entries.forEach { v ->
                        val selected = v == view
                        Text(
                            when (v) {
                                LibraryView.TRACKS -> "Titres"
                                LibraryView.ARTISTS -> "Artistes"
                                LibraryView.ALBUMS -> "Albums"
                            },
                            color = if (selected) MuzziQColors.Brand else MuzziQColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) MuzziQColors.Surface else MuzziQColors.Bg)
                                .clickable { view = v }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            when (view) {
                LibraryView.TRACKS -> items(tracks, key = { it.id }) { track -> TrackRow(track) { onTrackClick(track) } }
                LibraryView.ARTISTS -> {
                    if (artists.isEmpty()) item { EmptyBrowseNotice(mode) }
                    items(artists, key = { it.id }) { artist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { detailTitle = artist.name; onSelectArtist(artist.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Column {
                                Text(artist.name, color = MuzziQColors.TextPrimary, fontSize = 15.sp)
                                Text("${artist.trackCount} morceau(x) · ${artist.albumCount} album(s)", color = MuzziQColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                LibraryView.ALBUMS -> {
                    if (albums.isEmpty()) item { EmptyBrowseNotice(mode) }
                    items(albums, key = { it.id }) { album ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { detailTitle = album.title; onSelectAlbum(album.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Column {
                                Text(album.title, color = MuzziQColors.TextPrimary, fontSize = 15.sp)
                                Text("${album.artist} · ${album.trackCount} morceau(x)", color = MuzziQColors.TextMuted, fontSize = 12.sp)
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
    Text(
        if (mode == AppMode.STANDALONE) "Aucun morceau scanné pour l'instant."
        else "Bibliothèque serveur vide, ou pas encore chargée.",
        color = MuzziQColors.TextMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(16.dp),
    )
}
