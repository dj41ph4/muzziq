package com.muzziq.mobile.playback

import com.muzziq.mobile.data.MusicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Point d'accès partagé à la source musicale active (serveur ou standalone),
 * ancré en singleton process-wide plutôt qu'injecté par constructeur : le
 * MediaLibraryService (PlaybackService) est instancié par le système Android
 * indépendamment de l'Activity, il n'existe pas de graphe de DI ici (pas de
 * Hilt dans cette V1 — cohérent avec la stack minimale du plan §4). Même
 * raison structurelle que le pattern `globalThis.__muzziqXxx` côté serveur
 * (règle absolue du dépôt serveur, §2) : état partagé qui doit survivre à des composants
 * créés indépendamment.
 */
object MusicSourceLocator {
    private val _source = MutableStateFlow<MusicSource?>(null)
    val source: StateFlow<MusicSource?> = _source

    fun set(source: MusicSource) {
        _source.value = source
    }

    fun clear() {
        _source.value = null
    }
}
