package com.muzziq.mobile.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.ui.SpotifyAccountUiState
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Réglages (§56.4) — jusqu'ici backToOnboarding() existait côté AppViewModel
 * sans aucun point d'entrée UI : impossible de changer de serveur ou de
 * repasser en standalone sans effacer les données de l'app. Corrigé ici.
 * Étendu (§67, priorité 5) avec une section Comptes → Spotify plutôt qu'un
 * sous-écran séparé : un seul provider externe câblé à ce jour, un sous-écran
 * dédié serait de la sur-ingénierie tant qu'un deuxième (YouTube Music) n'existe
 * pas réellement.
 */
@Composable
fun SettingsScreen(
    mode: AppMode,
    serverUrl: String?,
    appVersion: String,
    onClose: () -> Unit,
    onChangeMode: () -> Unit,
    spotifyAccount: SpotifyAccountUiState,
    spotifyBusy: Boolean,
    spotifyError: String?,
    onConnectSpotify: () -> Unit,
    onDisconnectSpotify: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Retour", tint = MuzziQColors.TextPrimary)
                }
                Text("Réglages", color = MuzziQColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .background(MuzziQColors.Surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Mode actuel", color = MuzziQColors.TextMuted, fontSize = 12.sp)
                Text(
                    if (mode == AppMode.STANDALONE) "Local (standalone)" else "Connecté à un serveur",
                    color = MuzziQColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (mode == AppMode.LINKED && serverUrl != null) {
                    Text(serverUrl, color = MuzziQColors.TextFaint, fontSize = 12.sp)
                }
            }

            Button(
                onClick = onChangeMode,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 20.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MuzziQColors.Surface, contentColor = MuzziQColors.TextPrimary),
            ) {
                Text(
                    if (mode == AppMode.STANDALONE) "Se connecter à un serveur" else "Changer de serveur / passer en local",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Ta bibliothèque locale et tes réglages sur cet appareil ne sont jamais supprimés en changeant de mode.",
                color = MuzziQColors.TextFaint,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (spotifyAccount != SpotifyAccountUiState.NotConfigured) {
                Text(
                    "Comptes",
                    color = MuzziQColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MuzziQColors.Surface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val connected = spotifyAccount as? SpotifyAccountUiState.Connected
                        if (connected?.avatarUrl != null) {
                            AsyncImage(
                                model = connected.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MuzziQColors.BgElevated),
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MuzziQColors.TextMuted,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Spotify", color = MuzziQColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (spotifyAccount) {
                                    is SpotifyAccountUiState.Connected -> spotifyAccount.displayName ?: "Connecté"
                                    else -> "Bibliothèque et playlists en lecture seule"
                                },
                                color = MuzziQColors.TextFaint,
                                fontSize = 12.sp,
                            )
                        }
                        if (spotifyAccount is SpotifyAccountUiState.Connected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Connecté", tint = MuzziQColors.TextMuted)
                        }
                    }

                    if (spotifyBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MuzziQColors.TextPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Button(
                            onClick = if (spotifyAccount is SpotifyAccountUiState.Connected) onDisconnectSpotify else onConnectSpotify,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MuzziQColors.BgElevated, contentColor = MuzziQColors.TextPrimary),
                        ) {
                            Text(
                                if (spotifyAccount is SpotifyAccountUiState.Connected) "Déconnecter" else "Connecter",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (spotifyError != null) {
                        Text(spotifyError, color = MuzziQColors.TextFaint, fontSize = 12.sp)
                    }
                }
                Text(
                    "Déconnecter Spotify ne supprime jamais tes favoris/playlists/historique — seuls les identifiants du compte sont effacés.",
                    color = MuzziQColors.TextFaint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Text(
                "MuzziQ · v$appVersion",
                color = MuzziQColors.TextFaint,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
