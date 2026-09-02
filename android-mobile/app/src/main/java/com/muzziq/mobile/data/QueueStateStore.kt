package com.muzziq.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.muzziq.mobile.data.model.PersistedQueueState
import com.muzziq.mobile.data.model.PersistedTrackDto
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first

private val Context.queueDataStore by preferencesDataStore(name = "muzziq_queue_state")

/**
 * Persistance de la file d'attente courante (plan §40) — survit à la mort du
 * process (service arrêté par le système, app relancée), pas seulement à une
 * rotation d'écran. Séparé d'AppPrefs délibérément : cycle de vie et
 * fréquence d'écriture différents (une écriture par changement de morceau ou
 * pause, pas à chaque frame). DataStore + Moshi plutôt que Room ici : un seul
 * blob JSON, aucune requête relationnelle nécessaire pour ce besoin précis.
 *
 * Ne dépend d'aucune capacité serveur/YouTube — fonctionne identiquement en
 * standalone et en mode Lié, cohérent avec le principe d'autonomie complète
 * du plan (§56.4).
 */
class QueueStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(PersistedQueueState::class.java)
    private val key = stringPreferencesKey("queue_state_json")

    suspend fun save(tracks: List<Track>, currentIndex: Int, positionMs: Long) {
        if (tracks.isEmpty() || currentIndex !in tracks.indices) return
        val state = PersistedQueueState(
            tracks = tracks.map { it.toDto() },
            currentIndex = currentIndex,
            positionMs = positionMs,
        )
        val json = adapter.toJson(state)
        appContext.queueDataStore.edit { it[key] = json }
    }

    suspend fun load(): PersistedQueueState? {
        val json = appContext.queueDataStore.data.first()[key] ?: return null
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }

    suspend fun clear() {
        appContext.queueDataStore.edit { it.remove(key) }
    }

    companion object {
        fun Track.toDto(): PersistedTrackDto = PersistedTrackDto(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            artworkUrl = artworkUrl,
            sourceKind = when (source) {
                is TrackSource.Server -> "SERVER"
                is TrackSource.Local -> "LOCAL"
            },
            sourceRef = when (val s = source) {
                is TrackSource.Server -> s.recordingId
                is TrackSource.Local -> s.contentUri
            },
        )

        fun PersistedTrackDto.toTrack(): Track = Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            artworkUrl = artworkUrl,
            source = if (sourceKind == "LOCAL") TrackSource.Local(sourceRef) else TrackSource.Server(sourceRef),
        )
    }
}
