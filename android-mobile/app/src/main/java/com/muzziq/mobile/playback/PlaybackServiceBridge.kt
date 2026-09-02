package com.muzziq.mobile.playback

/**
 * Référence process-wide vers l'instance PlaybackService en cours d'exécution.
 * MediaController expose l'API Player standard (play/pause/seek) mais pas la
 * résolution "Track applicatif + MusicSource → MediaItem jouable" (§12) —
 * spécifique à MuzziQ, pas une commande Media3 générique. Plutôt que
 * détourner SessionCommand pour un besoin purement intra-process (UI et
 * service tournent dans le même process ici, pas de vrai IPC cross-app), un
 * pont direct est plus simple et tout aussi correct.
 */
object PlaybackServiceBridge {
    @Volatile private var instance: PlaybackService? = null

    fun attach(service: PlaybackService) { instance = service }
    fun detach(service: PlaybackService) { if (instance === service) instance = null }
    fun instanceOrNull(): PlaybackService? = instance
}
