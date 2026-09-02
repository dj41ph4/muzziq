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
import com.muzziq.mobile.ui.HomeRowUi
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Home/Discover (§46). En mode Lié, [homeRows] vient de GET /api/home
 * (moteur de recommandation déterministe déjà réel côté serveur — Continuer
 * l'écoute, Récemment ajoutés, Parce que vous aimez X ; le serveur n'inclut
 * que les rangées non vides). En standalone, ou tant que le serveur n'a
 * encore aucune rangée à proposer, [homeRows] est vide et l'écran retombe
 * sur la liste "Bibliothèque" à plat — jamais un carrousel vide affiché
 * pour faire joli. Les carrousels horizontaux façon Spotify restent un
 * chantier design system suivant ; ici les rangées sont verticales,
 * honnêtes sur ce qui est réellement branché.
 */
@Composable
fun HomeScreen(
    mode: AppMode,
    tracks: List<Track>,
    homeRows: List<HomeRowUi>,
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
            if (homeRows.isNotEmpty()) {
                homeRows.forEach { row ->
                    item(key = "row-${row.id}") {
                        Text(
                            row.title,
                            color = MuzziQColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(row.tracks, key = { "row-${row.id}-${it.id}" }) { track ->
                        TrackRow(track) { onTrackClick(track) }
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    Text(
                        if (mode == AppMode.STANDALONE) "Aucun morceau trouvé sur l'appareil — vérifie la permission musique dans Réglages."
                        else "Bibliothèque vide — ajoute des morceaux depuis la recherche.",
                        color = MuzziQColors.TextMuted,
                        modifier = androidx.compose.ui.Modifier.padding(16.dp),
                        fontSize = 13.sp,
                    )
                }
            } else {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(track) { onTrackClick(track) }
                }
            }
        }
    }
}
