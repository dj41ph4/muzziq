package com.muzziq.mobile.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.ui.common.MuzziQBackdrop
import com.muzziq.mobile.ui.theme.MuzziQColors

@Composable
fun OnboardingScreen(
    busy: Boolean,
    error: String?,
    savedServers: List<String> = emptyList(),
    onChooseStandalone: () -> Unit,
    onChooseLinked: (String) -> Unit,
) {
    var showServerForm by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }

    MuzziQBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MuzziQ", color = MuzziQColors.Brand, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    Text("Ta musique. Ton espace.", color = MuzziQColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MuzziQColors.Brand.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Language, contentDescription = null, tint = MuzziQColors.Brand, modifier = Modifier.size(25.dp))
                }
            }
            Text("Choisis où vit ta musique. Tu pourras changer de source à tout moment depuis les réglages.", color = MuzziQColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)

            AnimatedContent(
                targetState = showServerForm,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "source-choice",
            ) { serverForm ->
                if (!serverForm) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SourceCard(
                            icon = Icons.Rounded.PhoneAndroid,
                            eyebrow = "PRIVÉ · HORS-LIGNE",
                            title = "Local / Standalone",
                            description = "Lis tes fichiers directement sur l'appareil, retrouve ta bibliothèque et lance YouTube Music sans serveur.",
                            accent = MuzziQColors.Brand,
                            action = "Continuer en local",
                            busy = busy,
                            onClick = onChooseStandalone,
                        )
                        SourceCard(
                            icon = Icons.Rounded.CloudQueue,
                            eyebrow = "PUISSANT · SYNCHRONISÉ",
                            title = "Serveur MuzziQ",
                            description = "Connecte un serveur pour profiter de sa bibliothèque, de ses playlists, de ses recommandations et de son acquisition.",
                            accent = MuzziQColors.AccentViolet,
                            action = "Ajouter un serveur",
                            busy = false,
                            onClick = { showServerForm = true },
                        )
                    }
                } else {
                    ServerForm(
                        url = serverUrl,
                        onUrlChange = { serverUrl = it },
                        busy = busy,
                        error = error,
                        onSubmit = { onChooseLinked(serverUrl) },
                        onBack = { showServerForm = false },
                    )
                }
            }

            if (!showServerForm && savedServers.isNotEmpty()) {
                Text("SERVEURS CONNUS", color = MuzziQColors.TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 8.dp))
                savedServers.forEach { url ->
                    SavedServerRow(url = url, enabled = !busy, onClick = { onChooseLinked(url) })
                }
            }
            if (!showServerForm && error != null) Text(error, color = Color(0xFFFF7F88), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SourceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, eyebrow: String, title: String, description: String, accent: Color, action: String, busy: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MuzziQColors.Surface.copy(alpha = 0.92f)).border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(24.dp)).padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Text(eyebrow, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        Text(title, color = MuzziQColors.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Text(description, color = MuzziQColors.TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MuzziQColors.Bg)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MuzziQColors.Bg, strokeWidth = 2.dp) else Text(action, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ServerForm(url: String, onUrlChange: (String) -> Unit, busy: Boolean, error: String?, onSubmit: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MuzziQColors.Surface.copy(alpha = 0.94f)).padding(19.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MuzziQColors.AccentViolet)
            Text("Ajouter un serveur", color = MuzziQColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 10.dp))
        }
        Text("MuzziQ vérifie l'adresse avant de l'enregistrer sur cet appareil.", color = MuzziQColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
        OutlinedTextField(value = url, onValueChange = onUrlChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://muzziq.exemple.com", color = MuzziQColors.TextFaint) })
        if (error != null) Text(error, color = Color(0xFFFF7F88), fontSize = 13.sp)
        Button(onClick = onSubmit, enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = MuzziQColors.AccentViolet, contentColor = MuzziQColors.Bg)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MuzziQColors.Bg, strokeWidth = 2.dp) else Text("Tester et continuer", fontWeight = FontWeight.Bold)
        }
        Text("Retour au choix", color = MuzziQColors.TextMuted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp))
    }
}

@Composable
private fun SavedServerRow(url: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MuzziQColors.SurfaceRaised).clickable(enabled = enabled, onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MuzziQColors.AccentViolet.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = MuzziQColors.AccentViolet, modifier = Modifier.size(18.dp))
        }
        Text(url, color = MuzziQColors.TextPrimary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
    }
}
