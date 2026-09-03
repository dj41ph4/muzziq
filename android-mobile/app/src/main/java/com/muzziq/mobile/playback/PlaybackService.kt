package com.muzziq.mobile.playback

import android.app.PendingIntent
import android.util.Log
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
import com.muzziq.mobile.data.QueueStateStore
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.standalone.StandaloneMusicSourceHolder
import com.muzziq.mobile.standalone.StandaloneMusicSource
import com.muzziq.mobile.domain.PlaylistSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Queue logique (plan §40) : liste de Track + index courant. Volontairement PAS une
    // playlist Media3 multi-MediaItem — chaque morceau n'est résolu (resolvePlayableUri,
    // potentiellement un appel réseau côté serveur) qu'au moment où il devient courant,
    // jamais toute la file à l'avance. Limite assumée : pas de gapless entre morceaux
    // (un vrai preload du morceau suivant est un chantier séparé).
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    /** File d'attente observable (onglet Queue, plein écran) — miroir du champ [queue]
     * ci-dessous, mêmes écritures, jamais une deuxième source de vérité. */
    val queueFlow: StateFlow<List<Track>> = _queue.asStateFlow()
    private var queue: List<Track>
        get() = _queue.value
        set(value) { _queue.value = value }

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndexFlow: StateFlow<Int> = _queueIndex.asStateFlow()
    private var queueIndex: Int
        get() = _queueIndex.value
        set(value) { _queueIndex.value = value }

    private var queueSource: MusicSource? = null
    private var consecutiveOnlineStreamRetries = 0
    private val queueStateStore by lazy { QueueStateStore(this) }

    // Rempli à chaque construction d'item navigable (onGetChildren) — consulté par
    // onAddMediaItems() pour résoudre l'URI de lecture réelle au moment où l'utilisateur
    // tape un élément dans Android Auto (§56.2). Les items renvoyés par onGetChildren
    // n'ont jamais d'URI directement jouable, la résolution reste paresseuse (§12).
    private val browseTrackCache = mutableMapOf<String, Track>()

    override fun onCreate() {
        super.onCreate()
        // Chaque flux en ligne a son propre profil et ses propres en-têtes. Ils
        // sont injectés par URL lors de chaque ouverture/reprise du DataSpec.
        val httpDataSourceFactory = OkHttpDataSource.Factory(okhttp3.OkHttpClient())
        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            StreamHeaderDataSourceFactory(httpDataSourceFactory),
        )
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
                if (state == Player.STATE_READY) consecutiveOnlineStreamRetries = 0
                if (state == Player.STATE_ENDED) {
                    recordAffinity(completed = true)
                    if (hasNext()) skipNext()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val track = currentTrack
                val source = queueSource
                Log.e(TAG, "Lecture échouée: ${error.errorCodeName}", error)
                if (
                    track?.source is com.muzziq.mobile.data.model.TrackSource.YouTube &&
                    source is StandaloneMusicSource &&
                    consecutiveOnlineStreamRetries < MAX_ONLINE_STREAM_RETRIES
                ) {
                    consecutiveOnlineStreamRetries += 1
                    source.markOnlineStreamRejected(track)
                    scope.launch { resolveAndPlay(track, source) }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Persiste sur pause plutôt qu'à chaque frame (plan §57) — assez pour
                // reprendre "là où on s'était arrêté" entre deux lancements de l'app,
                // pas une précision à la seconde près pendant la lecture active.
                if (!isPlaying) persistQueueState()
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

    /** Résout et joue un [Track] isolé (pas de file autour) — utilisé quand l'appelant ne
     * fournit pas de contexte de liste. Préférer [playQueue] depuis l'UI dès qu'une liste
     * existe (Home/Recherche/Bibliothèque), pour que Suivant/Précédent fonctionnent. */
    fun playTrack(track: Track, source: MusicSource) {
        queue = listOf(track)
        queueIndex = 0
        queueSource = source
        scope.launch { resolveAndPlay(track, source) }
    }

    /** File d'attente réelle (§40) : joue [tracks]\[startIndex\] et retient le contexte pour
     * que [skipNext]/[skipPrevious] avancent dans la même liste — c'est ce qui rend les
     * boutons Suivant/Précédent du plein écran fonctionnels plutôt que décoratifs. */
    fun playQueue(tracks: List<Track>, startIndex: Int, source: MusicSource) {
        if (tracks.isEmpty()) return
        queue = tracks
        queueIndex = startIndex.coerceIn(0, tracks.lastIndex)
        queueSource = source
        scope.launch { resolveAndPlay(queue[queueIndex], source) }
    }

    fun skipNext() {
        val source = queueSource ?: return
        if (queue.isEmpty() || queueIndex >= queue.lastIndex) return
        queueIndex += 1
        scope.launch { resolveAndPlay(queue[queueIndex], source) }
    }

    fun skipPrevious() {
        val source = queueSource ?: return
        if (queue.isEmpty() || queueIndex <= 0) return
        queueIndex -= 1
        scope.launch { resolveAndPlay(queue[queueIndex], source) }
    }

    /** Saut direct à un morceau de la file (onglet Queue, plein écran) — même chemin de
     * résolution que [skipNext]/[skipPrevious] (resolveAndPlay), jamais une lecture ad hoc
     * indépendante du reste de la file. */
    fun jumpToQueueIndex(index: Int) {
        val source = queueSource ?: return
        if (index !in queue.indices || index == queueIndex) return
        queueIndex = index
        scope.launch { resolveAndPlay(queue[queueIndex], source) }
    }

    fun hasNext(): Boolean = queueIndex in 0 until queue.lastIndex
    fun hasPrevious(): Boolean = queueIndex > 0

    /** Lu par PlayerController après une transition (manuelle ou automatique en fin de
     * morceau) pour resynchroniser l'UI — le service reste la seule source de vérité sur
     * "que joue-t-on", jamais dupliquée côté UI (§56.4). */
    fun currentTrackOrNull(): Track? = currentTrack

    /** Résolution + lecture réelle — la seule voie qui touche [player.setMediaItem], appelée
     * depuis [playTrack]/[playQueue]/[skipNext]/[skipPrevious], jamais dupliquée ailleurs. */
    private suspend fun resolveAndPlay(track: Track, source: MusicSource) {
        recordAffinity(completed = false)
        val result = source.resolvePlayableUri(track)
        val url = result.getOrNull() ?: return
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
            .setMimeType((source as? StandaloneMusicSource)?.onlineMimeType(track))
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        persistQueueState()
    }

    private fun persistQueueState() {
        if (queue.isEmpty() || queueIndex !in queue.indices) return
        val position = player.currentPosition.coerceAtLeast(0)
        scope.launch { queueStateStore.save(queue, queueIndex, position) }
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
     * Arborescence Android Auto (§56.2) : Bibliothèque, Playlists, Historique — même
     * structure logique que les onglets mobiles, mêmes sources de données (MusicSource
     * actif, PlaylistRepositoryLocator, StandaloneMusicSourceHolder), aucune logique de
     * lecture dupliquée. La recherche vocale route vers MusicSource.search(), résolue
     * ensuite par onAddMediaItems() exactement comme un item de la bibliothèque tapé.
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
            val items = when {
                parentId == ROOT_ID -> listOf(
                    categoryItem(LIBRARY_ID, "Bibliothèque"),
                    categoryItem(PLAYLISTS_ID, "Playlists"),
                    categoryItem(HISTORY_ID, "Historique"),
                )
                parentId == LIBRARY_ID -> {
                    val source = MusicSourceLocator.source.value
                    source?.library()?.getOrNull().orEmpty().map { it.toBrowsableMediaItem() }
                }
                parentId == PLAYLISTS_ID -> {
                    val repo = PlaylistRepositoryLocator.repository.value
                    repo?.playlists()?.getOrNull().orEmpty().map { it.toBrowsableMediaItem() }
                }
                parentId == HISTORY_ID -> {
                    StandaloneMusicSourceHolder.instance?.recentHistory()?.map { it.track.toBrowsableMediaItem() }.orEmpty()
                }
                parentId.startsWith(PLAYLIST_PREFIX) -> {
                    val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                    val repo = PlaylistRepositoryLocator.repository.value
                    repo?.playlistTracks(playlistId)?.getOrNull().orEmpty().map { it.toBrowsableMediaItem() }
                }
                else -> emptyList()
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
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

        /** Résout l'URI de lecture réelle au moment où l'utilisateur tape un item navigable
         * (Android Auto, ou tout MediaController externe) — les items d'onGetChildren
         * n'ont jamais d'URI directement jouable (résolution paresseuse, §12). Item non
         * retrouvé dans le cache (session recréée entre le browse et le tap) : renvoyé
         * inchangé plutôt qu'une exception, ExoPlayer échouera proprement sur l'URI vide. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            val source = MusicSourceLocator.source.value
            mediaItems.map { item ->
                val track = browseTrackCache[item.mediaId]
                val url = if (track != null && source != null) source.resolvePlayableUri(track).getOrNull() else null
                if (track != null && url != null) {
                    // Même ordre que resolveAndPlay() : enregistre l'écoute partielle du
                    // morceau sortant AVANT de basculer currentTrack — sans ça, une lecture
                    // démarrée depuis Android Auto ne laissait aucune trace de "début
                    // d'écoute" côté historique/affinité (écart réel, corrigé ici).
                    recordAffinity(completed = false)
                    queue = listOf(track)
                    queueIndex = 0
                    queueSource = source
                    currentTrack = track
                    item.buildUpon().setUri(url).setMediaId(track.id).build()
                } else {
                    item
                }
            }.toMutableList()
        }
    }

    private fun categoryItem(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

    private fun PlaylistSummary.toBrowsableMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId("$PLAYLIST_PREFIX$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setSubtitle("$itemCount morceau(x)")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

    private fun Track.toBrowsableMediaItem(): MediaItem {
        browseTrackCache[id] = this
        return MediaItem.Builder()
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
    }

    companion object {
        private const val TAG = "MuzziQPlayback"
        private const val MAX_ONLINE_STREAM_RETRIES = 2
        const val ROOT_ID = "muzziq_root"
        const val LIBRARY_ID = "muzziq_library"
        const val PLAYLISTS_ID = "muzziq_playlists"
        const val HISTORY_ID = "muzziq_history"
        const val PLAYLIST_PREFIX = "muzziq_playlist:"
    }
}
