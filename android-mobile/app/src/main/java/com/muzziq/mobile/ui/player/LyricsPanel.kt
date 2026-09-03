package com.muzziq.mobile.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.domain.LyricsProvider
import com.muzziq.mobile.ui.common.EmptyState
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Onglet Paroles du plein écran (chantier laissé de côté par la session précédente).
 * Consomme le contrat [LyricsProvider] (domain/Repositories.kt) déjà posé — aujourd'hui
 * uniquement [com.muzziq.mobile.domain.NullLyricsProvider] (aucune route serveur `/api/lyrics`,
 * aucun fournisseur tiers branché, plan §38 jamais commencé). Ce composant ne sait rien de
 * cette absence : il affiche ce que le provider renvoie, honnêtement, et un vrai fournisseur
 * branché plus tard n'a qu'à remplacer l'implémentation injectée, pas ce composant.
 */
@Composable
fun LyricsPanel(
    track: Track,
    lyricsProvider: LyricsProvider,
    modifier: Modifier = Modifier,
) {
    var loading by remember(track.id) { mutableStateOf(true) }
    var lyrics by remember(track.id) { mutableStateOf<String?>(null) }
    var failed by remember(track.id) { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        loading = true
        failed = false
        val result = lyricsProvider.lyricsFor(track)
        lyrics = result.getOrNull()
        failed = result.isFailure
        loading = false
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(color = MuzziQColors.Brand)
            failed -> EmptyState(
                message = "Impossible de récupérer les paroles pour l'instant.",
            )
            lyrics.isNullOrBlank() -> EmptyState(
                message = "Paroles non disponibles pour l'instant — aucun fournisseur de paroles n'est encore branché à MuzziQ.",
            )
            else -> Text(
                lyrics ?: "",
                color = MuzziQColors.TextPrimary,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            )
        }
    }
}
