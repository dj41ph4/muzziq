package com.muzziq.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.domain.MusicProviderId
import com.muzziq.mobile.domain.PlaylistSummary
import com.muzziq.mobile.ui.HomeRowUi
import com.muzziq.mobile.ui.common.EmptyState
import com.muzziq.mobile.ui.common.HorizontalShelf
import com.muzziq.mobile.ui.common.MuzziQTopBar
import com.muzziq.mobile.ui.common.QuickTile
import com.muzziq.mobile.ui.common.SectionTitle
import com.muzziq.mobile.ui.common.SquareMediaCard
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors
import java.time.LocalTime

/**
 * Home/Discover (§46, §56-58) — salutation dépendant de l'heure locale, grille de raccourcis
 * compacts (playlists existantes, réutilise QuickTile plutôt qu'un nouveau composant §56),
 * puis carrousels horizontaux titrés pour chaque rangée serveur (GET /api/home, moteur de
 * recommandation déterministe déjà réel — voir AppViewModel.refreshHomeRows). Une rangée
 * vide n'est jamais envoyée par le serveur (voir doc en tête de HomeRowUi côté ViewModel),
 * donc chaque carrousel affiché ici a toujours du contenu — jamais de section vide "pour
 * faire joli". En mode Standalone ou tant que le serveur n'a aucune rangée, on retombe sur
 * la bibliothèque à plat (comportement fonctionnel inchangé par rapport à avant cette passe
 * visuelle, uniquement la présentation change).
 */
@Composable
fun HomeScreen(
    mode: AppMode,
    tracks: List<Track>,
    homeRows: List<HomeRowUi>,
    contentPadding: PaddingValues,
    playlists: List<PlaylistSummary> = emptyList(),
    onTrackClick: (Track) -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
) {
    val greeting = remember(mode) { greetingForNow() }
    // Spotify (lecture seule, §58) exclu des raccourcis : ouvrir une playlist Spotify passe
    // par l'onglet Playlists dédié, pas par un raccourci Home qui suggérerait une action
    // identique (ajout/suppression) à ce que permettent les playlists MuzziQ.
    val quickPlaylists = playlists.filter { it.provider != MusicProviderId.SPOTIFY }.take(6)

    Box(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        LazyColumn(contentPadding = contentPadding) {
            item {
                MuzziQTopBar(
                    title = greeting,
                    subtitle = if (mode == AppMode.STANDALONE) "Ta bibliothèque locale" else "Ta bibliothèque MuzziQ",
                )
            }

            if (quickPlaylists.isNotEmpty()) {
                // Grille 2 colonnes non-lazy (au plus 6 raccourcis, jamais de contenu non
                // borné) plutôt qu'un LazyVerticalGrid imbriqué dans ce LazyColumn — évite le
                // double scrollable et le calcul de hauteur fragile qu'imposerait une grille
                // paresseuse ici (règle §"pas de liste non bornée dans une colonne scrollable").
                lazyColumnItems(quickPlaylists.chunked(2)) { rowPair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPair.forEach { playlist ->
                            QuickTile(
                                title = playlist.name,
                                artworkUrl = null,
                                modifier = Modifier.weight(1f).height(52.dp),
                                onClick = { onOpenPlaylist(playlist.id) },
                            )
                        }
                        if (rowPair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (homeRows.isNotEmpty()) {
                homeRows.forEach { row ->
                    item(key = "row-title-${row.id}") { SectionTitle(row.title) }
                    item(key = "row-shelf-${row.id}") {
                        HorizontalShelf(items = row.tracks, key = { "row-${row.id}-${it.id}" }) { track ->
                            SquareMediaCard(
                                title = track.title,
                                subtitle = track.artist,
                                artworkUrl = track.artworkUrl,
                                onClick = { onTrackClick(track) },
                            )
                        }
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    EmptyState(
                        if (mode == AppMode.STANDALONE) "Aucun morceau trouvé sur l'appareil — vérifie la permission musique dans Réglages."
                        else "Bibliothèque vide — ajoute des morceaux depuis la recherche.",
                    )
                }
            } else {
                item { SectionTitle("Ta bibliothèque") }
                lazyColumnItems(tracks, key = { it.id }) { track ->
                    TrackRow(track) { onTrackClick(track) }
                }
            }
        }
    }
}

private fun greetingForNow(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 5 -> "Bonne nuit"
        hour < 12 -> "Bonjour"
        hour < 18 -> "Bon après-midi"
        else -> "Bonsoir"
    }
}
