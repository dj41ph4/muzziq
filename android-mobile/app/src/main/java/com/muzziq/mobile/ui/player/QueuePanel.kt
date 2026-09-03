package com.muzziq.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.ui.common.EmptyState
import com.muzziq.mobile.ui.common.Skeleton
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Onglet Queue du plein écran (chantier laissé de côté par la session précédente — voir
 * l'en-tête de Player.kt). Consomme la vraie file d'attente de `PlaybackService`
 * (`queue`/`queueIndex`, exposée via `PlayerController.queue`/`queueIndex`) — mêmes données
 * que Suivant/Précédent, jamais une liste séparée. Design inspiré de
 * `src/components/ContextPanel.tsx` côté web (morceau courant surligné, tap pour sauter),
 * réimplémenté en Compose plutôt que traduit ligne à ligne.
 */
@Composable
fun QueuePanel(
    queue: List<Track>,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.size <= 1) {
        // Une seule lecture isolée (PlayerController.play, sans liste autour) n'est pas une
        // vraie file d'attente consultable — dire cet état honnêtement plutôt qu'afficher un
        // onglet qui ne contiendrait que le morceau déjà visible dans Now Playing.
        EmptyState(
            message = "Pas de file d'attente pour ce morceau — lance-le depuis une liste (bibliothèque, playlist, recherche) pour voir les morceaux suivants ici.",
            icon = Icons.Rounded.QueueMusic,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Text(
                "File d'attente (${queue.size})",
                color = MuzziQColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(queue.size) { index ->
            val t = queue[index]
            val isCurrent = index == currentIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isCurrent) MuzziQColors.Surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(enabled = !isCurrent) { onJumpTo(index) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val coverModifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                if (t.artworkUrl == null) {
                    Skeleton(coverModifier, RoundedCornerShape(6.dp))
                } else {
                    SubcomposeAsyncImage(
                        model = t.artworkUrl,
                        contentDescription = null,
                        modifier = coverModifier,
                        loading = { Skeleton(Modifier.size(40.dp), RoundedCornerShape(6.dp)) },
                        error = { Skeleton(Modifier.size(40.dp), RoundedCornerShape(6.dp)) },
                    )
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        t.title,
                        color = if (isCurrent) MuzziQColors.Brand else MuzziQColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        t.artist,
                        color = MuzziQColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isCurrent) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "En cours de lecture",
                        tint = MuzziQColors.Brand,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
