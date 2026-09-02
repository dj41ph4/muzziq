package com.muzziq.mobile.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists ORDER BY name")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: String): ArtistEntity?
}

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(albums: List<AlbumEntity>)

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC")
    fun observeByArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: String): AlbumEntity?
}

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(track: TrackEntity)

    @Query("SELECT * FROM tracks ORDER BY artist, album, title")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' LIMIT 200")
    suspend fun search(query: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE sourceType = :sourceType ORDER BY artist, title")
    fun observeBySource(sourceType: String): Flow<List<TrackEntity>>

    @Query("DELETE FROM tracks WHERE sourceType = :sourceType")
    suspend fun deleteBySource(sourceType: String)
}

@Dao
interface ProviderMappingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mapping: ProviderMappingEntity)

    @Query("SELECT * FROM provider_mappings WHERE trackId = :trackId")
    suspend fun forTrack(trackId: String): List<ProviderMappingEntity>

    @Query("SELECT * FROM provider_mappings WHERE provider = :provider AND externalId = :externalId LIMIT 1")
    suspend fun byExternalId(provider: String, externalId: String): ProviderMappingEntity?
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    /** Instantané suspend — cf. DownloadDao.getAllOnce(), même besoin (rafraîchir un
     * StateFlow après une action plutôt qu'observer en continu). */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<PlaylistEntity>

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deleteById(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeItem(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearItems(playlistId: String)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    fun observeItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getItemsOnce(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun itemCount(playlistId: String): Int
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun remove(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>
}

@Dao
interface PlaybackEventDao {
    @Insert
    suspend fun insert(event: PlaybackEventEntity)

    @Query("SELECT * FROM playback_events WHERE trackId = :trackId ORDER BY playedAt DESC")
    suspend fun forTrack(trackId: String): List<PlaybackEventEntity>

    /** Même logique d'affinité qu'en SQLite brut (LocalTasteDatabase) : compte les écoutes
     * significatives (>50% — la pondération réelle reste calculée en Kotlin, pas ici). */
    @Query("SELECT artist, COUNT(*) as playCount FROM playback_events WHERE completed = 1 GROUP BY artist ORDER BY playCount DESC LIMIT :limit")
    suspend fun topCompletedArtists(limit: Int): List<ArtistPlayCount>
}

data class ArtistPlayCount(val artist: String, val playCount: Int)

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    /** Instantané suspend — pour un appelant qui a juste besoin de la liste une fois
     * (ex. rafraîchir un StateFlow après une action), pas d'observer en continu. */
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    suspend fun getAllOnce(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun getForTrack(trackId: String): DownloadEntity?

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}

@Dao
interface RecommendationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recommendations: List<RecommendationEntity>)

    @Query("SELECT * FROM recommendations ORDER BY score DESC LIMIT :limit")
    suspend fun top(limit: Int): List<RecommendationEntity>

    @Query("DELETE FROM recommendations")
    suspend fun clear()
}

@Dao
interface LinkedMusicAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: LinkedMusicAccountEntity)

    @Query("SELECT * FROM linked_music_accounts ORDER BY connectedAt")
    fun observeAll(): Flow<List<LinkedMusicAccountEntity>>

    @Query("SELECT * FROM linked_music_accounts ORDER BY connectedAt")
    suspend fun getAllOnce(): List<LinkedMusicAccountEntity>

    @Query("SELECT * FROM linked_music_accounts WHERE provider = :provider LIMIT 1")
    suspend fun byProvider(provider: String): LinkedMusicAccountEntity?

    /** Déconnexion (règle absolue du plan : ne supprime que le compte, jamais les données
     * MuzziQ associées — favoris/playlists/historique/mappings/downloads restent intacts). */
    @Query("DELETE FROM linked_music_accounts WHERE id = :accountId")
    suspend fun delete(accountId: String)

    @Query("UPDATE linked_music_accounts SET status = :status, lastSyncAt = :lastSyncAt WHERE id = :accountId")
    suspend fun updateStatus(accountId: String, status: String, lastSyncAt: Long?)
}
