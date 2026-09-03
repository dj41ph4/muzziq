package com.muzziq.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Design system réutilisable (§56 du plan) — sorti de HomeScreen/SearchScreen/LibraryScreen
 * pour éviter de dupliquer les mêmes patterns de carte visuellement à chaque écran (titre de
 * section, carrousel horizontal, card carrée, chip de filtre...). Purement présentationnel :
 * aucun composant ici n'appelle de repository/API, tout reste passé en paramètre par l'écran
 * appelant — mêmes branchements de données qu'avant cette passe, juste la couche visuelle.
 */

/** Titre de section au-dessus d'un carrousel ou d'une liste (§56 SectionTitle). */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = MuzziQColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (actionLabel != null && onActionClick != null) {
            Text(
                actionLabel,
                color = MuzziQColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onActionClick),
            )
        }
    }
}

/**
 * Carrousel horizontal générique (§56 HorizontalShelf) — jamais rendu si [items] est vide,
 * la décision de masquer une section entière reste à l'appelant (HomeScreen ne doit jamais
 * afficher un carrousel vide, voir consigne §46 déjà respectée côté ViewModel).
 */
@Composable
fun <T> HorizontalShelf(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = key) { item -> itemContent(item) }
    }
}

/** Cover carrée avec fallback icône note — utilisée par [SquareMediaCard]/[AlbumCard]. */
@Composable
private fun MediaCover(
    artworkUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
) {
    if (artworkUrl != null) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MuzziQColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MuzziQColors.TextFaint)
        }
    }
}

/** Card carrée générique pour un carrousel (titre + morceau/album/playlist) — §56 SquareMediaCard. */
@Composable
fun SquareMediaCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        MediaCover(artworkUrl, 140.dp, RoundedCornerShape(10.dp))
        Text(
            title,
            color = MuzziQColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = MuzziQColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Alias sémantique de [SquareMediaCard] pour un album — même rendu, nom explicite côté appelant. */
@Composable
fun AlbumCard(title: String, artist: String, artworkUrl: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SquareMediaCard(title = title, subtitle = artist, artworkUrl = artworkUrl, modifier = modifier, onClick = onClick)
}

/** Alias sémantique de [SquareMediaCard] pour une playlist. */
@Composable
fun PlaylistCard(title: String, trackCount: Int, artworkUrl: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SquareMediaCard(
        title = title,
        subtitle = if (trackCount > 0) "$trackCount morceau(x)" else null,
        artworkUrl = artworkUrl,
        modifier = modifier,
        onClick = onClick,
    )
}

/** Cover ronde pour un artiste dans un carrousel (§56 ArtistCircleCard) — jamais carrée : un
 * artiste se distingue visuellement d'un album/playlist rien qu'à la forme de sa cover. */
@Composable
fun ArtistCircleCard(name: String, artworkUrl: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .width(110.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaCover(artworkUrl, 110.dp, CircleShape)
        Text(
            name,
            color = MuzziQColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Raccourci compact façon Spotify (grille "Favoris"/"Mix"/"Playlist récente") — §56 QuickTile.
 * Rectangulaire, cover + libellé sur une ligne, pensé pour une grille 2 colonnes en Home. */
@Composable
fun QuickTile(
    title: String,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MuzziQColors.Surface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaCover(artworkUrl, 52.dp, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
        Text(
            title,
            color = MuzziQColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
    }
}

/** Chip de filtre/genre/mood (§56 FilterChip) — un seul composant pour la sélection de vue
 * Bibliothèque (Titres/Artistes/Albums) et les chips de découverte en Recherche. */
@Composable
fun MuzziQFilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) MuzziQColors.Bg else MuzziQColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MuzziQColors.Brand else MuzziQColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * Grande card d'en-tête pour Artiste/Album/Playlist (§56 HeroCard) — cover large + dégradé
 * dérivé de la couleur dominante (voir ui/palette/DynamicPalette.kt, déjà utilisé par le
 * player plein écran) infusant vers le fond, pas de couleur fixe arbitraire par écran.
 */
@Composable
fun HeroCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    dominant: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to dominant.copy(alpha = 0.55f),
                    1f to MuzziQColors.Bg,
                ),
            )
            .padding(20.dp),
    ) {
        MediaCover(artworkUrl, 160.dp, RoundedCornerShape(12.dp))
        Text(
            title,
            color = MuzziQColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (subtitle != null) {
            Text(subtitle, color = MuzziQColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (trailingContent != null) {
            Box(Modifier.padding(top = 12.dp)) { trailingContent() }
        }
    }
}

/** Placeholder animé pendant un chargement (§56 Skeleton) — pas encore branché à un vrai
 * état "loading" par écran (la plupart des données locales/Room arrivent quasi instantanément),
 * posé ici pour que les écrans suivants puissent l'utiliser sans redéfinir le shimmer. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).background(MuzziQColors.Surface))
}

/** État vide générique (§56 EmptyState) — icône + message centré, utilisé par les écrans
 * qui affichaient jusqu'ici un simple Text() perdu dans la liste. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.MusicNote,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MuzziQColors.TextFaint, modifier = Modifier.size(40.dp))
        Text(
            message,
            color = MuzziQColors.TextMuted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Barre supérieure commune (§56 MuzziQTopBar) — titre + slot d'actions optionnel, pour que
 * Home/Recherche/Bibliothèque partagent la même typographie de titre d'écran. */
@Composable
fun MuzziQTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            if (subtitle != null) {
                Text(subtitle, color = MuzziQColors.TextMuted, fontSize = 14.sp)
            }
            Text(title, color = MuzziQColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (trailingContent != null) trailingContent()
    }
}
