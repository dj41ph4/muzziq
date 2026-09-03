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
import com.muzziq.mobile.domain.MusicProviderId
import com.muzziq.mobile.domain.PlaylistSummary
import com.muzziq.mobile.ui.common.EmptyState
import com.muzziq.mobile.ui.common.HeroCard
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.palette.rememberDominantColor
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Playlists (plan §6/§66/§67/§58) — liste + détail, fonctionnent identiquement en
 * standalone (RoomPlaylistRepository) et en mode Lié (ServerPlaylistRepository), avec
 * les playlists Spotify agrégées par-dessus quand un compte est connecté
 * (AppViewModel.refreshPlaylists()) — jamais fusionnées avec une playlist Room/serveur
 * de même nom (§58), toujours distinguées par un badge "Spotify" et jamais
 * modifiables depuis cet écran (lecture seule, boutons Supprimer/Retirer masqués).
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
            // PlaylistSummary ne porte pas de cover propre (§66/§67, pas encore de champ
            // artwork côté modèle) — on retombe honnêtement sur la cover du premier morceau
            // plutôt que d'inventer une pochette de playlist qui n'existe pas.
            val heroArtwork = playlistTracks.firstOrNull()?.artworkUrl
            val dominant = rememberDominantColor(heroArtwork)
            // Spotify (lecture seule) : jamais de bouton "Retirer" — aucune route
            // d'écriture n'existe côté SpotifyProvider pour cette playlist (§58).
            val removable = playlist?.provider != MusicProviderId.SPOTIFY
            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                LazyColumn {
                    item {
                        HeroCard(
                            title = playlist?.name ?: "Playlist",
                            subtitle = if (playlistTracks.isNotEmpty()) "${playlistTracks.size} morceau(x)" else null,
                            artworkUrl = heroArtwork,
                            dominant = dominant,
                        ) {
                            if (playlist?.provider == MusicProviderId.SPOTIFY) SourceBadge(text = "Spotify")
                        }
                    }
                    if (playlistTracks.isEmpty()) {
                        item { EmptyState("Playlist vide — ajoute des morceaux depuis la recherche ou la bibliothèque.") }
                    }
                    items(playlistTracks, key = { it.id }) { track ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { TrackRow(track, onClick = { onTrackClick(track) }) }
                            if (removable) {
                                IconButton(onClick = { onRemoveTrack(openPlaylistId, track.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Retirer", tint = MuzziQColors.TextFaint)
                                }
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onClosePlaylist,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f), CircleShape),
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour", tint = MuzziQColors.TextPrimary)
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
                    item { EmptyState("Aucune playlist — crée-en une avec le bouton +.") }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(playlist.name, color = MuzziQColors.TextPrimary, fontSize = 15.sp)
                                if (playlist.provider == MusicProviderId.SPOTIFY) {
                                    SourceBadge(text = "Spotify", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            Text("${playlist.itemCount} morceau(x)", color = MuzziQColors.TextMuted, fontSize = 12.sp)
                        }
                        // Spotify (lecture seule) : jamais de bouton "Supprimer" — la
                        // playlist n'existe pas dans Room/le serveur (§58).
                        if (playlist.provider != MusicProviderId.SPOTIFY) {
                            IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer", tint = MuzziQColors.TextFaint)
                            }
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

/** Badge de provenance (§58) — jamais un simple renommage "(Spotify)" dans le titre,
 * un élément visuel séparé pour que la distinction reste évidente même si deux
 * playlists (une locale/serveur, une Spotify) partagent le même nom. */
@Composable
private fun SourceBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(MuzziQColors.Surface, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = MuzziQColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
