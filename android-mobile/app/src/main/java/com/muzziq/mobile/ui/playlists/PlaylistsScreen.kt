package com.muzziq.mobile.ui.playlists

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.domain.PlaylistSummary
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Playlists (plan §6/§66) — liste + détail, fonctionnent identiquement en
 * standalone (RoomPlaylistRepository) et en mode Lié (ServerPlaylistRepository) :
 * même écran, même comportement, seule l'implémentation change côté AppViewModel.
 */
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSummary>,
    openPlaylistId: String?,
    playlistTracks: List<Track>,
    contentPadding: PaddingValues,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onClosePlaylist: () -> Unit,
    onRemoveTrack: (String, String) -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        if (openPlaylistId != null) {
            val playlist = playlists.firstOrNull { it.id == openPlaylistId }
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClosePlaylist) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour", tint = MuzziQColors.TextPrimary)
                    }
                    Text(
                        playlist?.name ?: "Playlist",
                        color = MuzziQColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                }
                if (playlistTracks.isEmpty()) {
                    Text(
                        "Playlist vide — ajoute des morceaux depuis la recherche ou la bibliothèque.",
                        color = MuzziQColors.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn {
                    items(playlistTracks, key = { it.id }) { track ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { TrackRow(track) { onTrackClick(track) } }
                            IconButton(onClick = { onRemoveTrack(openPlaylistId, track.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Retirer", tint = MuzziQColors.TextFaint)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = contentPadding) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Playlists", color = MuzziQColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Nouvelle playlist", tint = MuzziQColors.Brand)
                        }
                    }
                }
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            "Aucune playlist — crée-en une avec le bouton +.",
                            color = MuzziQColors.TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(playlist.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MuzziQColors.Surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = MuzziQColors.TextMuted)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(playlist.name, color = MuzziQColors.TextPrimary, fontSize = 15.sp)
                            Text("${playlist.itemCount} morceau(x)", color = MuzziQColors.TextMuted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Supprimer", tint = MuzziQColors.TextFaint)
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Nouvelle playlist", color = MuzziQColors.TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Nom de la playlist") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onCreatePlaylist(name); showCreateDialog = false }) {
                        Text("Créer", color = MuzziQColors.Brand)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Annuler", color = MuzziQColors.TextMuted) }
                },
                containerColor = MuzziQColors.Surface,
            )
        }
    }
}
