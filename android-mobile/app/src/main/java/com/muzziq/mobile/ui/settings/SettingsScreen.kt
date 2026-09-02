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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Réglages (§56.4) — jusqu'ici backToOnboarding() existait côté AppViewModel
 * sans aucun point d'entrée UI : impossible de changer de serveur ou de
 * repasser en standalone sans effacer les données de l'app. Corrigé ici.
 */
@Composable
fun SettingsScreen(
    mode: AppMode,
    serverUrl: String?,
    appVersion: String,
    onClose: () -> Unit,
    onChangeMode: () -> Unit,
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

            Text(
                "MuzziQ · v$appVersion",
                color = MuzziQColors.TextFaint,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
