package com.muzziq.mobile.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.ui.SpotifyAccountUiState
import com.muzziq.mobile.ui.common.MuzziQBackdrop
import com.muzziq.mobile.ui.common.ModeBadge
import com.muzziq.mobile.ui.theme.MuzziQColors

@Composable
fun SettingsScreen(
    mode: AppMode,
    serverUrl: String?,
    savedServerUrls: List<String> = emptyList(),
    appVersion: String,
    onClose: () -> Unit,
    onChangeMode: () -> Unit,
    onSelectServer: (String) -> Unit = {},
    onAddServer: (String) -> Unit = {},
    onRemoveServer: (String) -> Unit = {},
    serverBusy: Boolean = false,
    serverError: String? = null,
    spotifyAccount: SpotifyAccountUiState,
    spotifyBusy: Boolean,
    spotifyError: String?,
    onConnectSpotify: () -> Unit,
    onDisconnectSpotify: () -> Unit,
    onSyncSpotifyFavorites: () -> Unit = {},
    onSyncSpotifyPlaylists: () -> Unit = {},
    showDeviceLocalTracks: Boolean,
    onShowDeviceLocalTracksChanged: (Boolean) -> Unit,
) {
    var showAddServer by remember { mutableStateOf(false) }
    var newServerUrl by remember { mutableStateOf("") }

    MuzziQBackdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour", tint = MuzziQColors.TextPrimary) }
                Text("Réglages", color = MuzziQColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f).padding(start = 4.dp))
                ModeBadge(if (mode == AppMode.STANDALONE) "LOCAL" else "SERVEUR")
            }

            SettingsSectionLabel("SOURCE ACTIVE")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MuzziQColors.Surface.copy(alpha = 0.94f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MuzziQColors.Brand.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(if (mode == AppMode.STANDALONE) Icons.Rounded.MusicNote else Icons.Rounded.CloudQueue, contentDescription = null, tint = MuzziQColors.Brand)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(if (mode == AppMode.STANDALONE) "Bibliothèque locale" else "Serveur MuzziQ", color = MuzziQColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (mode == AppMode.STANDALONE) "Lecture directe sur cet appareil" else serverUrl ?: "Serveur actif", color = MuzziQColors.TextMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Actif", tint = MuzziQColors.Brand)
                }
                Button(onClick = onChangeMode, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MuzziQColors.SurfaceRaised, contentColor = MuzziQColors.TextPrimary)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (mode == AppMode.STANDALONE) "Choisir un serveur" else "Revenir au choix des sources", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }

            SettingsSectionLabel("SERVEURS MuzziQ")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MuzziQColors.Surface.copy(alpha = 0.94f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (savedServerUrls.isEmpty()) {
                    Text("Aucun serveur enregistré. Ajoute ton instance MuzziQ pour la retrouver ici.", color = MuzziQColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(8.dp))
                } else {
                    savedServerUrls.forEach { url ->
                        ServerRow(url = url, active = url == serverUrl, onSelect = { onSelectServer(url) }, onRemove = { onRemoveServer(url) })
                    }
                }
                AnimatedVisibility(visible = showAddServer, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = newServerUrl, onValueChange = { newServerUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://muzziq.exemple.com", color = MuzziQColors.TextFaint) })
                        Button(onClick = { onAddServer(newServerUrl); newServerUrl = ""; showAddServer = false }, enabled = newServerUrl.isNotBlank() && !serverBusy, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MuzziQColors.Brand, contentColor = MuzziQColors.Bg)) {
                            if (serverBusy) CircularProgressIndicator(Modifier.size(18.dp), color = MuzziQColors.Bg, strokeWidth = 2.dp) else Text("Tester et enregistrer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (!showAddServer) {
                    Text("Ajouter un serveur", color = MuzziQColors.Brand, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showAddServer = true }.padding(10.dp))
                }
                if (serverError != null) Text(serverError, color = Color(0xFFFF7F88), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }

            SettingsSectionLabel("COMPTES")
            SpotifyAccountCard(spotifyAccount, spotifyBusy, spotifyError, onConnectSpotify, onDisconnectSpotify, onSyncSpotifyFavorites, onSyncSpotifyPlaylists)

            SettingsSectionLabel("BIBLIOTHÈQUE")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MuzziQColors.Surface.copy(alpha = 0.94f)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Afficher les fichiers du téléphone", color = MuzziQColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Masque uniquement les MP3/fichiers locaux détectés sur l’appareil. Les téléchargements Movviz restent visibles.", color = MuzziQColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Switch(checked = showDeviceLocalTracks, onCheckedChange = onShowDeviceLocalTracksChanged)
            }

            Text("MuzziQ · v$appVersion", color = MuzziQColors.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text, color = MuzziQColors.TextFaint, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun ServerRow(url: String, active: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (active) MuzziQColors.Brand.copy(alpha = 0.1f) else MuzziQColors.SurfaceRaised).clickable(enabled = !active, onClick = onSelect).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Link, contentDescription = null, tint = if (active) MuzziQColors.Brand else MuzziQColors.TextMuted, modifier = Modifier.size(19.dp))
        Text(url, color = MuzziQColors.TextPrimary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        if (active) Text("Actif", color = MuzziQColors.Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        else IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Supprimer", tint = MuzziQColors.TextFaint, modifier = Modifier.size(19.dp)) }
    }
}

@Composable
private fun SpotifyAccountCard(account: SpotifyAccountUiState, busy: Boolean, error: String?, onConnect: () -> Unit, onDisconnect: () -> Unit, onSyncFavorites: () -> Unit, onSyncPlaylists: () -> Unit) {
    val connected = account as? SpotifyAccountUiState.Connected
    val authorizedButUnavailable = account is SpotifyAccountUiState.AuthorizedButApiUnavailable
    val linked = connected != null || authorizedButUnavailable
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MuzziQColors.Surface.copy(alpha = 0.94f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (connected?.avatarUrl != null) AsyncImage(connected.avatarUrl, contentDescription = null, modifier = Modifier.size(42.dp).clip(CircleShape))
            else Box(Modifier.size(42.dp).clip(CircleShape).background(MuzziQColors.SurfaceRaised), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = MuzziQColors.TextMuted) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Spotify", color = MuzziQColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        connected != null -> connected.displayName ?: "Connecté"
                        authorizedButUnavailable -> "Compte lié — API Spotify indisponible"
                        account is SpotifyAccountUiState.NotConfigured -> "Configuration développeur requise"
                        else -> "Prêt à connecter ta bibliothèque"
                    },
                    color = MuzziQColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
            if (linked) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MuzziQColors.Brand)
        }
        if (account is SpotifyAccountUiState.NotConfigured) {
            Text("Le connecteur Spotify n'est pas inclus dans cette build. Une build officielle configurée permettra une connexion en un clic et la synchronisation des titres likés, sans télécharger les flux Spotify.", color = MuzziQColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        } else if (authorizedButUnavailable) {
            Text("La connexion OAuth a réussi et le jeton est conservé dans le coffre chiffré. Spotify bloque actuellement l'accès Web API à ce compte : les favoris et playlists ne peuvent pas encore être synchronisés.", color = MuzziQColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        } else if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MuzziQColors.Brand, strokeWidth = 2.dp)
        else Button(onClick = if (linked) onDisconnect else onConnect, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = MuzziQColors.SurfaceRaised, contentColor = MuzziQColors.TextPrimary)) { Text(if (linked) "Déconnecter" else "Lier mon compte Spotify", fontWeight = FontWeight.SemiBold) }
        if (account is SpotifyAccountUiState.Connected && !busy) {
            TextButton(onClick = onSyncFavorites, modifier = Modifier.fillMaxWidth()) {
                Text("Synchroniser les titres likés (dans les deux sens)", color = MuzziQColors.Brand, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSyncPlaylists, modifier = Modifier.fillMaxWidth()) {
                Text("Synchroniser les playlists (dans les deux sens)", color = MuzziQColors.Brand, fontWeight = FontWeight.Bold)
            }
        }
        if (error != null) Text(error, color = if (error.startsWith("Favoris synchronisés") || error.startsWith("Playlists synchronisées")) MuzziQColors.Brand else Color(0xFFFF7F88), fontSize = 12.sp)
    }
}
