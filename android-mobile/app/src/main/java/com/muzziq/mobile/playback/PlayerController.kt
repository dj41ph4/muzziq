package com.muzziq.mobile.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.muzziq.mobile.data.MusicSource
import com.muzziq.mobile.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pont Compose ↔ MediaController (§39 : le player est une fonction de premier
 * niveau, pas une réflexion d'état ad hoc). Un seul contrôleur pour toute
 * l'app — mini-player et plein écran lisent le même state.
 */
class PlayerController(context: Context) {
    private val appContext = context.applicationContext
    private var controller: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    /** Non nul entre [restoreDisplay] et la reprise effective — le morceau affiché
     * (mini-player) n'a pas encore été rechargé dans le vrai player média (plan §57 :
     * restaurer l'affichage au lancement de l'app n'implique pas de relancer un flux
     * réseau tant que l'utilisateur n'a rien demandé). */
    private var pendingRestore: Pair<List<Track>, Int>? = null

    fun connect(onReady: () -> Unit = {}) {
        if (controller != null) { onReady(); return }
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    _durationMs.value = controller?.duration?.coerceAtLeast(0) ?: 0L
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Se resynchronise sur le service — couvre à la fois un skip manuel
                    // (skipNext/skipPrevious) et l'avance automatique en fin de morceau
                    // (STATE_ENDED côté PlaybackService), les deux chemins passant par
                    // resolveAndPlay() côté service.
                    _currentTrack.value = PlaybackServiceBridge.instanceOrNull()?.currentTrackOrNull()
                }
            })
            onReady()
        }, MoreExecutors.directExecutor())
    }

    /** Lecture d'un morceau isolé, sans file autour (pas de Suivant/Précédent utile). */
    fun play(track: Track, source: MusicSource) {
        pendingRestore = null
        _currentTrack.value = track
        val service = PlaybackServiceBridge.instanceOrNull() ?: return
        service.playTrack(track, source)
    }

    /** Lecture avec file d'attente réelle (§40) — [tracks] est le contexte visible dans
     * l'UI au moment du tap (résultats de recherche, bibliothèque…), [startIndex] le
     * morceau cliqué. Rend Suivant/Précédent fonctionnels. */
    fun playQueue(tracks: List<Track>, startIndex: Int, source: MusicSource) {
        if (tracks.isEmpty()) return
        pendingRestore = null
        _currentTrack.value = tracks[startIndex.coerceIn(0, tracks.lastIndex)]
        val service = PlaybackServiceBridge.instanceOrNull() ?: return
        service.playQueue(tracks, startIndex, source)
    }

    /** Affiche une file persistée (plan §57) sans démarrer de lecture réseau/disque —
     * seul un vrai geste utilisateur (togglePlayPause) déclenche [playQueue]. Sans ça,
     * relancer l'app relancerait silencieusement un flux, contraire à la règle "jamais
     * de mise à jour/action silencieuse sans confirmation" appliquée ici à la lecture. */
    fun restoreDisplay(tracks: List<Track>, currentIndex: Int, positionMs: Long) {
        if (tracks.isEmpty()) return
        val index = currentIndex.coerceIn(0, tracks.lastIndex)
        val track = tracks[index]
        _currentTrack.value = track
        _positionMs.value = positionMs
        _durationMs.value = ((track.durationSeconds ?: 0.0) * 1000).toLong()
        pendingRestore = tracks to index
    }

    fun skipNext() {
        PlaybackServiceBridge.instanceOrNull()?.skipNext()
    }

    fun skipPrevious() {
        PlaybackServiceBridge.instanceOrNull()?.skipPrevious()
    }

    /** [source] n'est nécessaire que pour reprendre une file restaurée ([restoreDisplay])
     * jamais encore chargée dans le vrai player — null quand une lecture est déjà active,
     * auquel cas c'est un simple play/pause MediaController. */
    fun togglePlayPause(source: MusicSource? = null) {
        val restore = pendingRestore
        if (restore != null && source != null) {
            pendingRestore = null
            playQueue(restore.first, restore.second, source)
            return
        }
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun tickPosition() {
        _positionMs.value = controller?.currentPosition?.coerceAtLeast(0) ?: 0L
        _durationMs.value = controller?.duration?.coerceAtLeast(0) ?: 0L
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
