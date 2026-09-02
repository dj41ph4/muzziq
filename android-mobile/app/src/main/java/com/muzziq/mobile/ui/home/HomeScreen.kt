package com.muzziq.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Home/Discover (§46) — V1 volontairement sobre : "Bibliothèque" pour les deux
 * modes. Les rangées thématiques (Continuer l'écoute, Redécouvrir, Mix du
 * jour…) sont un chantier suivant, pas encore branchées sur de vraies données
 * ici — mieux vaut une liste honnête qu'un mockup de rangées vides.
 */
@Composable
fun HomeScreen(
    mode: AppMode,
    tracks: List<Track>,
    contentPadding: PaddingValues,
    onTrackClick: (Track) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        LazyColumn(contentPadding = contentPadding) {
            item {
                Column(androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Bonjour", color = MuzziQColors.TextMuted, fontSize = 14.sp)
                    Text(
                        if (mode == AppMode.STANDALONE) "Ta bibliothèque locale" else "Ta bibliothèque MuzziQ",
                        color = MuzziQColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (tracks.isEmpty()) {
                item {
                    Text(
                        if (mode == AppMode.STANDALONE) "Aucun morceau trouvé sur l'appareil — vérifie la permission musique dans Réglages."
                        else "Bibliothèque vide — ajoute des morceaux depuis la recherche.",
                        color = MuzziQColors.TextMuted,
                        modifier = androidx.compose.ui.Modifier.padding(16.dp),
                        fontSize = 13.sp,
                    )
                }
            }
            items(tracks, key = { it.id }) { track ->
                TrackRow(track) { onTrackClick(track) }
            }
        }
    }
}
