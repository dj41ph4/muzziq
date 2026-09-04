package com.muzziq.mobile.playback

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Petite frontière Cast indépendante du lecteur local : le service reste le
 * lecteur principal, tandis que CastPlayer prend temporairement le relais dès
 * qu'une session Google Cast est réellement ouverte.
 */
class CastController(context: Context) : SessionAvailabilityListener, CastStateListener {
    private val appContext = context.applicationContext
    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null
    private var wasPlayingBeforeCast = false
    private var lastRemotePosition = 0L

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castState = MutableStateFlow(CastState.NO_DEVICES_AVAILABLE)
    val castState: StateFlow<Int> = _castState.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlayingState: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** L'initialisation est tolérante : un appareil sans Google Play Services
     * conserve l'expérience locale complète. */
    fun initialize() {
        if (castPlayer != null) return
        runCatching {
            val context = CastContext.getSharedInstance(appContext)
            castContext = context
            context.addCastStateListener(this)
            @Suppress("DEPRECATION")
            castPlayer = CastPlayer(context).also {
                it.setSessionAvailabilityListener(this)
                it.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
                    override fun onEvents(player: Player, events: Player.Events) { refresh() }
                })
            }
            _castState.value = context.castState
        }.onFailure {
            castContext = null
            castPlayer = null
            _castState.value = CastState.NO_DEVICES_AVAILABLE
        }
    }

    fun loadCurrent(item: MediaItem, positionMs: Long, playWhenReady: Boolean) {
        val player = castPlayer ?: return
        wasPlayingBeforeCast = playWhenReady
        player.setMediaItem(item, positionMs)
        player.prepare()
        if (playWhenReady) player.play()
    }

    fun play() { castPlayer?.play() }
    fun isPlaying(): Boolean = castPlayer?.isPlaying == true
    fun pause() {
        lastRemotePosition = castPlayer?.currentPosition ?: lastRemotePosition
        castPlayer?.pause()
    }
    fun seekTo(positionMs: Long) {
        lastRemotePosition = positionMs
        castPlayer?.seekTo(positionMs)
    }
    fun currentPosition(): Long = castPlayer?.currentPosition ?: lastRemotePosition
    fun wasPlayingBeforeCast(): Boolean = wasPlayingBeforeCast
    fun refresh() {
        val player = castPlayer ?: return
        _isPlaying.value = player.isPlaying
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = player.duration.coerceAtLeast(0L)
    }

    override fun onCastStateChanged(state: Int) { _castState.value = state }

    override fun onCastSessionAvailable() { _isCasting.value = true }

    override fun onCastSessionUnavailable() {
        lastRemotePosition = castPlayer?.currentPosition ?: lastRemotePosition
        _isPlaying.value = false
        _isCasting.value = false
    }

    fun release() {
        castContext?.removeCastStateListener(this)
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        castContext = null
    }
}
