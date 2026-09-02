package com.muzziq.mobile.playback

import com.muzziq.mobile.domain.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Même pattern que MusicSourceLocator — point d'accès process-wide au
 * PlaylistRepository actif (Room en standalone, serveur en mode Lié),
 * nécessaire pour que PlaybackService (créé indépendamment de l'Activity)
 * puisse exposer les playlists dans l'arborescence Android Auto (§56.2).
 */
object PlaylistRepositoryLocator {
    private val _repository = MutableStateFlow<PlaylistRepository?>(null)
    val repository: StateFlow<PlaylistRepository?> = _repository

    fun set(repository: PlaylistRepository) {
        _repository.value = repository
    }

    fun clear() {
        _repository.value = null
    }
}
