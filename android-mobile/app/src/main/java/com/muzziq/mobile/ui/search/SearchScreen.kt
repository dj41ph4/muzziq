package com.muzziq.mobile.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.muzziq.mobile.ui.common.TrackRow
import com.muzziq.mobile.ui.theme.MuzziQColors

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
            LazyColumn {
                items(results, key = { it.id }) { track ->
                    TrackRow(track) { onTrackClick(track) }
                }
            }
        }
    }
}
