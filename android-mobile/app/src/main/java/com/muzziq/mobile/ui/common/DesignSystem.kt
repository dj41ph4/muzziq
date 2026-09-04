package com.muzziq.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.muzziq.mobile.ui.theme.MuzziQColors

/**
 * Design system réutilisable (§56 du plan) — sorti de HomeScreen/SearchScreen/LibraryScreen
 * pour éviter de dupliquer les mêmes patterns de carte visuellement à chaque écran (titre de
 * section, carrousel horizontal, card carrée, chip de filtre...). Purement présentationnel :
 * aucun composant ici n'appelle de repository/API, tout reste passé en paramètre par l'écran
 * appelant — mêmes branchements de données qu'avant cette passe, juste la couche visuelle.
 */

/** Fond vivant partagé par les écrans principaux : halos lents et profondeur légère,
 * sans détourner l'attention de la musique. */
@Composable
fun MuzziQBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "muzziq-backdrop")
    val drift by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "backdrop-glow",
    )
    Box(modifier.fillMaxWidth().background(MuzziQColors.Bg)) {
        Box(
            Modifier
                .size(280.dp)
                .offset(x = (-110).dp, y = (-110).dp)
                .alpha(drift)
                .background(Brush.radialGradient(listOf(MuzziQColors.Brand, Color.Transparent)), CircleShape),
        )
        Box(
            Modifier
                .size(230.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = 50.dp)
                .alpha(drift * 0.7f)
                .background(Brush.radialGradient(listOf(MuzziQColors.AccentViolet, Color.Transparent)), CircleShape),
        )
        content()
    }
}

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
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(MuzziQColors.Brand))
            Text(title, color = MuzziQColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
        }
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

/**
 * Cover carrée/ronde avec état de chargement réel — utilisée par [SquareMediaCard]/
 * [AlbumCard]/[ArtistCircleCard]/[QuickTile]/[HeroCard]. `null` d'entrée (pas encore de
 * cover connue, ex. browse artiste/album côté serveur — voir AppViewModel.openArtist/
 * openAlbum) retombe directement sur l'icône, jamais de requête réseau lancée pour rien.
 * Une URL non nulle passe par [Skeleton] (§56) pendant le chargement Coil, puis l'icône de
 * secours en cas d'échec réseau — remplace le AsyncImage muet d'avant qui laissait un
 * espace vide sans retour visuel pendant le chargement.
 */
@Composable
private fun MediaCover(
    artworkUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
) {
    if (artworkUrl == null) {
        Box(
            Modifier.size(size).clip(shape).background(MuzziQColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MuzziQColors.TextFaint)
        }
        return
    }
    // Pas de lambda `content` trailing ici : le slot `loading`/`error` de cette surcharge
    // Coil retombe déjà sur SubcomposeAsyncImageContent() par défaut pour l'état succès —
    // en ajouter un explicitement fait échouer la résolution de surcharge (erreur réelle
    // trouvée par le CI : "Argument type mismatch... EqualityDelegate").
    SubcomposeAsyncImage(
        model = artworkUrl,
        contentDescription = null,
        modifier = Modifier.size(size).clip(shape),
        loading = { Skeleton(Modifier.size(size), shape) },
        error = {
            Box(
                Modifier.size(size).background(MuzziQColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MuzziQColors.TextFaint)
            }
        },
    )
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
            .clip(RoundedCornerShape(12.dp))
            .background(MuzziQColors.SurfaceRaised)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaCover(artworkUrl, 58.dp, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
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
            .padding(horizontal = 15.dp, vertical = 8.dp),
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

/** Placeholder pendant un chargement (§56 Skeleton) — utilisé par [MediaCover] pendant le
 * chargement réel d'une cover via Coil (SubcomposeAsyncImage.loading), donc par toute
 * card du design system qui affiche une image (SquareMediaCard/AlbumCard/PlaylistCard/
 * ArtistCircleCard/QuickTile/HeroCard). Pas d'animation shimmer en V1 (juste la couleur
 * Surface plate) — suffisant vu la vitesse habituelle du cache Coil, une vraie animation
 * resterait à ajouter si des chargements visiblement lents sont un jour observés. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmer by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "skeleton-shimmer",
    )
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(
                colors = listOf(MuzziQColors.Surface, MuzziQColors.SurfaceRaised, MuzziQColors.Surface),
                start = Offset(shimmer * 420f, 0f),
                end = Offset(shimmer * 420f + 260f, 0f),
            ),
        ),
    )
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
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            if (subtitle != null) {
                Text(subtitle, color = MuzziQColors.TextMuted, fontSize = 14.sp)
            }
            Text(title, color = MuzziQColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        if (trailingContent != null) trailingContent()
    }
}

@Composable
fun ModeBadge(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MuzziQColors.Brand.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MuzziQColors.Brand))
        Text(label, color = MuzziQColors.Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun AnimatedEqualizer(modifier: Modifier = Modifier, color: Color = MuzziQColors.Brand) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val first by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(620), RepeatMode.Reverse), label = "bar-1")
    val second by transition.animateFloat(1f, 0.3f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "bar-2")
    val third by transition.animateFloat(0.5f, 0.95f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bar-3")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(first, second, third).forEach { height ->
            Box(Modifier.width(3.dp).height((14 * height).dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}
