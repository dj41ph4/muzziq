package com.muzziq.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun LibraryScreen(
    mode: AppMode,
    tracks: List<Track>,
    busy: Boolean,
    contentPadding: PaddingValues,
    onRescan: () -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MuzziQColors.Bg)) {
        LazyColumn(contentPadding = contentPadding) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Bibliothèque", color = MuzziQColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (mode == AppMode.STANDALONE) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.padding(4.dp), color = MuzziQColors.Brand, strokeWidth = 2.dp)
                        else TextButton(onClick = onRescan) { Text("Rescanner", color = MuzziQColors.Brand) }
                    }
                }
            }
            items(tracks, key = { it.id }) { track ->
                TrackRow(track) { onTrackClick(track) }
            }
        }
    }
}
