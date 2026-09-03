package com.muzziq.mobile.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.standalone.StandaloneMusicSource
import com.muzziq.mobile.ui.common.EmptyState
import com.muzziq.mobile.ui.common.MuzziQFilterChip
import com.muzziq.mobile.ui.common.SectionTitle
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors

/** Suggestions de découverte affichées avant toute saisie (§56-58) — de simples requêtes
 * texte pré-remplies déclenchant la vraie recherche déjà branchée (search() côté ViewModel,
 * providers actifs via ProviderRegistry), jamais un filtre par genre/mood inventé : aucune
 * route/metadata de genre n'existe côté serveur ou standalone aujourd'hui, donc ces chips
 * ne prétendent pas plus que ce qu'elles font réellement — un raccourci de saisie. */
private val discoveryShortcuts = listOf("Rock", "Pop", "Jazz", "Électro", "Rap", "Classique", "Metal", "Chill")

@Composable
fun SearchScreen(
    mode: AppMode,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Track>,
    contentPadding: PaddingValues,
    onTrackClick: (Track) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Text(
                "Recherche",
                color = MuzziQColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
                placeholder = { Text(if (mode == AppMode.STANDALONE) "Rechercher dans ta musique locale" else "Rechercher un titre, artiste, album", color = MuzziQColors.TextFaint) },
            )
            if (mode == AppMode.STANDALONE) {
                Text(
                    StandaloneMusicSource.STREAMING_UNAVAILABLE_NOTICE,
                    color = MuzziQColors.TextFaint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (query.isBlank()) {
                SectionTitle("Découvrir")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(discoveryShortcuts) { shortcut ->
                        MuzziQFilterChip(label = shortcut, selected = false) { onQueryChange(shortcut) }
                    }
                }
            } else if (results.isEmpty()) {
                EmptyState("Aucun résultat pour « $query ».")
            } else {
                SectionTitle("Résultats")
                LazyColumn {
                    items(results, key = { it.id }) { track ->
                        TrackRow(track) { onTrackClick(track) }
                    }
                }
            }
        }
    }
}
