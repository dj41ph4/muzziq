package com.muzziq.mobile.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base Room locale — schéma défini et compilé (voir Entities.kt pour l'état
 * réel : pas encore instanciée ailleurs dans l'app). `exportSchema = false`
 * délibérément : pas de dossier de schémas JSON versionnés tant que cette
 * base n'est pas réellement utilisée en production sur un appareil (aucun
 * intérêt à figer des migrations avant la V1 réelle).
 */
@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        ProviderMappingEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        FavoriteEntity::class,
        PlaybackEventEntity::class,
        DownloadEntity::class,
        RecommendationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class MuzziQDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun providerMappingDao(): ProviderMappingDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackEventDao(): PlaybackEventDao
    abstract fun downloadDao(): DownloadDao
    abstract fun recommendationDao(): RecommendationDao

    companion object {
        @Volatile private var instance: MuzziQDatabase? = null

        fun get(context: Context): MuzziQDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuzziQDatabase::class.java,
                    "muzziq.db",
                ).build().also { instance = it }
            }
    }
}
