package com.muzziq.mobile.playback

import android.app.PendingIntent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.muzziq.mobile.data.MusicSource
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.standalone.StandaloneMusicSourceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Lecteur système MuzziQ : MediaSession + notification native + Android Auto
 * (§56, §56.2). Un seul pipeline de lecture pour les deux modes (§56.4) — le
 * schéma de l'URI (content:// en standalone, http(s):// en mode Lié) est géré
 * par un DataSource.Factory unique, jamais deux moteurs de lecture séparés.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTrack: Track? = null

    override fun onCreate() {
        super.onCreate()
        val httpDataSourceFactory = OkHttpDataSource.Factory(okhttp3.OkHttpClient())
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) recordAffinity(completed = true)
            }
        })

        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivityPendingIntent = activityIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .apply { sessionActivityPendingIntent?.let { setSessionActivity(it) } }
            .build()

        setMediaNotificationProvider(DefaultMediaNotificationProvider(this))
        PlaybackServiceBridge.attach(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    /** Résout et joue un [Track] via la source musicale active (serveur OU standalone),
     * appelé depuis l'UI (mini-player, écrans de liste) plutôt que de manipuler
     * directement le player — la résolution d'URL doit toujours passer par ici. */
    fun playTrack(track: Track, source: MusicSource) {
        scope.launch {
            recordAffinity(completed = false)
            val result = source.resolvePlayableUri(track)
            val url = result.getOrNull() ?: return@launch
            currentTrack = track
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.artworkUrl?.let { android.net.Uri.parse(it) })
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaId(track.id)
                .setMediaMetadata(metadata)
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun recordAffinity(completed: Boolean) {
        val track = currentTrack ?: return
        val standalone = StandaloneMusicSourceHolder.instance ?: return
        val position = if (completed) player.duration.coerceAtLeast(0) else player.currentPosition
        val duration = player.duration.coerceAtLeast(1)
        standalone.recordPlayback(track, position, duration)
    }

    override fun onDestroy() {
        PlaybackServiceBridge.detach(this)
        mediaSession?.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Arborescence Android Auto (§56.2) : "Bibliothèque" à plat en V1 — même source
     * de données (MusicSource actif) que le mobile, aucune logique de lecture
     * dupliquée. La recherche vocale route vers MusicSource.search(), résolue
     * ensuite par playTrack() exactement comme une recherche manuelle.
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("MuzziQ")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val source = MusicSourceLocator.source.value
            val tracks = source?.library()?.getOrNull().orEmpty()
            LibraryResult.ofItemList(ImmutableList.copyOf(tracks.map { it.toBrowsableMediaItem() }), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val source = MusicSourceLocator.source.value
            val count = source?.search(query)?.getOrNull()?.size ?: 0
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val source = MusicSourceLocator.source.value
            val tracks = source?.search(query)?.getOrNull().orEmpty()
            LibraryResult.ofItemList(ImmutableList.copyOf(tracks.map { it.toBrowsableMediaItem() }), params)
        }
    }

    private fun Track.toBrowsableMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

    companion object {
        const val ROOT_ID = "muzziq_root"
    }
}
