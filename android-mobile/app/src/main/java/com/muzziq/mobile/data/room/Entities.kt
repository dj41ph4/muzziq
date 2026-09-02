package com.muzziq.mobile.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schéma Room — base de données locale structurée (plan Android, chantier
 * "autonomous-first"). État réel au moment de l'écriture : ce fichier définit
 * le schéma et compile (vérifié seulement par le prochain run CI, aucun SDK
 * Android ici), mais RIEN dans l'app ne l'instancie ni ne le lit encore —
 * `standalone/LocalTasteDatabase.kt` (SQLiteOpenHelper) reste la seule
 * source réellement active pour la bibliothèque locale et le moteur de goût.
 * La migration (remplacer LocalTasteDatabase par ce schéma, porter les DAOs
 * dans StandaloneMusicSource) est un chantier séparé, pas fait ici — ajout
 * pur, délibérément non branché pour ne rien casser de ce qui fonctionne.
 */

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String,
    val thumbnailUrl: String? = null,
    val year: Int? = null,
)

@Entity(
    tableName = "tracks",
    indices = [Index("albumId"), Index("artistId")],
)
data class TrackEntity(
    /** recordingId côté serveur, ou content:// URI côté standalone — jamais un ID provider brut. */
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val durationSeconds: Double? = null,
    val artworkUrl: String? = null,
    /** "SERVER" | "LOCAL" — reflet de TrackSource (data/model/Models.kt), jamais une 3e valeur inventée. */
    val sourceType: String,
    val trackNumber: Int? = null,
)

/** Table cruciale (plan §67) — permet de changer de provider sans changer les IDs MuzziQ,
 * portée telle quelle depuis le modèle serveur (src/lib/library/providerMappingsStore.ts). */
@Entity(
    tableName = "provider_mappings",
    indices = [Index("trackId"), Index(value = ["provider", "externalId"], unique = true)],
)
data class ProviderMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val provider: String,
    val externalId: String,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

/**
 * Morceau dénormalisé directement dans l'item (titre/artiste/pochette/source) plutôt
 * que référencé via TrackDao/TrackEntity : ces deux dernières tables ne sont alimentées
 * par personne aujourd'hui (StandaloneMusicSource garde sa propre table SQLite,
 * data/room/ n'est pas encore la source de vérité de la bibliothèque, voir le
 * commentaire en tête de ce fichier). Dénormaliser évite de dépendre d'un système de
 * synchronisation Track↔Room qui n'existe pas encore réellement.
 */
@Entity(
    tableName = "playlist_items",
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Double? = null,
    val artworkUrl: String? = null,
    /** "SERVER" | "LOCAL" — reflet de TrackSource, jamais une 3e valeur inventée. */
    val sourceKind: String,
    val sourceRef: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long,
)

/** Règles statistiques du plan (§42) — mêmes seuils que LocalTasteDatabase (SQLiteOpenHelper),
 * schéma équivalent pour une migration ultérieure sans perte sémantique. */
@Entity(
    tableName = "playback_events",
    indices = [Index("trackId")],
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val artist: String,
    val playedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val downloadedAt: Long,
    val localPath: String,
    val sizeBytes: Long,
    val quality: String? = null,
)

@Entity(
    tableName = "recommendations",
    indices = [Index("trackId")],
)
data class RecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val reason: String,
    val score: Double,
    val generatedAt: Long,
)
