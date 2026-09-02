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
        _currentTrack.value = track
        val service = PlaybackServiceBridge.instanceOrNull() ?: return
        service.playTrack(track, source)
    }

    /** Lecture avec file d'attente réelle (§40) — [tracks] est le contexte visible dans
     * l'UI au moment du tap (résultats de recherche, bibliothèque…), [startIndex] le
     * morceau cliqué. Rend Suivant/Précédent fonctionnels. */
    fun playQueue(tracks: List<Track>, startIndex: Int, source: MusicSource) {
        if (tracks.isEmpty()) return
        _currentTrack.value = tracks[startIndex.coerceIn(0, tracks.lastIndex)]
        val service = PlaybackServiceBridge.instanceOrNull() ?: return
        service.playQueue(tracks, startIndex, source)
    }

    fun skipNext() {
        PlaybackServiceBridge.instanceOrNull()?.skipNext()
    }

    fun skipPrevious() {
        PlaybackServiceBridge.instanceOrNull()?.skipPrevious()
    }

    fun togglePlayPause() {
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
