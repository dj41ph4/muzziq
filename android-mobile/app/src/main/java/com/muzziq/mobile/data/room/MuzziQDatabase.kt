package com.muzziq.mobile.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base Room locale — schéma défini et compilé, et réellement en production
 * (consommée par RoomFavoriteRepository, RoomPlaylistRepository, les
 * téléchargements). `exportSchema = true` : le schéma JSON versionné vit
 * dans `android-mobile/app/schemas/` (généré par KSP, voir
 * `room.schemaLocation` dans app/build.gradle.kts) — nécessaire pour que
 * Room valide chaque migration à la compilation plutôt que de découvrir un
 * schéma cassé au premier redémarrage d'un vrai appareil.
 *
 * INTERDICTION ABSOLUE de `fallbackToDestructiveMigration()` : ça effacerait
 * silencieusement favoris/playlists/downloads existants au moindre bump de
 * version. Toute évolution de schéma passe par une vraie `Migration`.
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
        LinkedMusicAccountEntity::class,
    ],
    version = 2,
    exportSchema = true,
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
    abstract fun linkedMusicAccountDao(): LinkedMusicAccountDao

    companion object {
        @Volatile private var instance: MuzziQDatabase? = null

        /**
         * v1 -> v2 : ajoute `linked_music_accounts` (pivot multi-provider cumulatif,
         * voir Entities.kt). Aucune colonne existante touchée — les 10 tables v1
         * traversent intactes, aucune perte possible pour un utilisateur déjà en
         * production (favoris/playlists/downloads).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `linked_music_accounts` (
                        `id` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `externalUserId` TEXT NOT NULL,
                        `displayName` TEXT,
                        `avatarUrl` TEXT,
                        `isPrimary` INTEGER NOT NULL,
                        `syncEnabled` INTEGER NOT NULL,
                        `connectedAt` INTEGER NOT NULL,
                        `lastSyncAt` INTEGER,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): MuzziQDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuzziQDatabase::class.java,
                    "muzziq.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
