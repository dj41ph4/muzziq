package com.muzziq.mobile.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Premier lancement (§56.4) : les deux modes sont proposés À ÉGALITÉ — deux
 * cartes de même taille, aucune ordonnée comme "principale". Standalone est
 * une capacité complète de l'app, pas un repli en attendant un serveur.
 */
@Composable
fun OnboardingScreen(
    busy: Boolean,
    error: String?,
    onChooseStandalone: () -> Unit,
    onChooseLinked: (String) -> Unit,
) {
    var showServerForm by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "MuzziQ",
                color = MuzziQColors.Brand,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Bienvenue — choisis comment tu veux écouter. Les deux modes fonctionnent pleinement.",
                color = MuzziQColors.TextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            if (!showServerForm) {
                ModeCard(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "Local (standalone)",
                    description = "Ta bibliothèque sur cet appareil, aucun compte. Lecteur, moteur de goût et téléchargements tournent entièrement sur le téléphone.",
                    buttonLabel = "Commencer en local",
                    onClick = onChooseStandalone,
                    busy = busy,
                )
                ModeCard(
                    icon = Icons.Rounded.CloudQueue,
                    title = "Se connecter à un serveur",
                    description = "Rejoins un serveur MuzziQ existant pour son catalogue, ses recommandations et son acquisition automatisée.",
                    buttonLabel = "Configurer un serveur",
                    onClick = { showServerForm = true },
                    busy = false,
                )
            } else {
                Text("Adresse du serveur MuzziQ", color = MuzziQColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://muzziq.exemple.com", color = MuzziQColors.TextFaint) },
                )
                if (error != null) {
                    Text(error, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), fontSize = 13.sp)
                }
                Button(
                    onClick = { onChooseLinked(serverUrl) },
                    enabled = !busy && serverUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MuzziQColors.Brand, contentColor = androidx.compose.ui.graphics.Color(0xFF00210A)),
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.height(20.dp), color = androidx.compose.ui.graphics.Color(0xFF00210A), strokeWidth = 2.dp)
                    else Text("Tester la connexion et continuer", fontWeight = FontWeight.Bold)
                }
                Text(
                    "Retour",
                    color = MuzziQColors.TextMuted,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { showServerForm = false },
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MuzziQColors.Surface)
            .border(1.dp, MuzziQColors.Brand.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = MuzziQColors.Brand)
        Text(title, color = MuzziQColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(description, color = MuzziQColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
        Button(
            onClick = onClick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MuzziQColors.Brand, contentColor = androidx.compose.ui.graphics.Color(0xFF00210A)),
        ) {
            Text(buttonLabel, fontWeight = FontWeight.Bold)
        }
    }
}
