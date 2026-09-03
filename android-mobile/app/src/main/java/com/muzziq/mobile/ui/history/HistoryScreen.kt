package com.muzziq.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.standalone.HistoryEntry
import com.muzziq.mobile.ui.common.EmptyState
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors
import java.util.concurrent.TimeUnit

/**
 * Historique d'écoute (plan §41) — standalone uniquement à ce jour (voir
 * AppViewModel.history) : PlaybackService n'enregistre un événement que pour
 * un morceau local, le mode Lié n'a encore aucune route consommée pour ça.
 * Affiché honnêtement plutôt qu'un écran vide silencieux.
 */
@Composable
fun HistoryScreen(
    mode: AppMode,
    entries: List<HistoryEntry>,
    contentPadding: PaddingValues,
    onTrackClick: (Track) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        Text(
            "Historique",
            color = MuzziQColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )
        if (mode == AppMode.LINKED) {
            Text(
                "Historique disponible uniquement en local pour l'instant — le mode Lié " +
                    "n'envoie pas encore les événements de lecture au serveur.",
                color = MuzziQColors.TextFaint,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (entries.isEmpty()) {
            EmptyState("Rien d'écouté récemment.")
        }
        LazyColumn(contentPadding = contentPadding) {
            items(entries, key = { it.track.id + it.playedAt }) { entry ->
                TrackRow(
                    track = entry.track,
                    onClick = { onTrackClick(entry.track) },
                    trailingContent = {
                        Text(relativeTime(entry.playedAt), color = MuzziQColors.TextFaint, fontSize = 11.sp)
                    },
                )
            }
        }
    }
}

private fun relativeTime(playedAtMs: Long): String {
    val diffMs = (System.currentTimeMillis() - playedAtMs).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a ${minutes}min"
        hours < 24 -> "il y a ${hours}h"
        else -> "il y a ${days}j"
    }
}
