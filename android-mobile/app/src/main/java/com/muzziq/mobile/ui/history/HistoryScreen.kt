package com.muzziq.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muzziq.mobile.data.AppMode
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.standalone.HistoryEntry
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
            Text(
                "Rien d'écouté récemment.",
                color = MuzziQColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(contentPadding = contentPadding) {
            items(entries, key = { it.track.id + it.playedAt }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClick(entry.track) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = entry.track.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(entry.track.title, color = MuzziQColors.TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.track.artist, color = MuzziQColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(relativeTime(entry.playedAt), color = MuzziQColors.TextFaint, fontSize = 11.sp)
                }
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
