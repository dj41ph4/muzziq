package com.muzziq.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.ui.theme.MuzziQColors

@Composable
fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fallback icône directement si aucune URL (jamais de requête réseau pour rien) ;
        // sinon Skeleton (§56) pendant le chargement Coil plutôt qu'un espace vide muet.
        if (track.artworkUrl == null) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MuzziQColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MuzziQColors.TextFaint, modifier = Modifier.size(20.dp))
            }
        } else {
            SubcomposeAsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                loading = { Skeleton(Modifier.size(48.dp), RoundedCornerShape(8.dp)) },
                error = {
                    Box(
                        Modifier.size(48.dp).background(MuzziQColors.Surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MuzziQColors.TextFaint, modifier = Modifier.size(20.dp))
                    }
                },
            ) {
                SubcomposeAsyncImageContent()
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(track.title, color = MuzziQColors.TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = MuzziQColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
