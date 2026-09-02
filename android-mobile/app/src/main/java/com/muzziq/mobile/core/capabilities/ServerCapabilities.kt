package com.muzziq.mobile.core.capabilities

/** État de la connexion optionnelle au serveur : aucune capacité locale ne dépend de lui. */
enum class ServerConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DEGRADED,
    ERROR,
}

/** Capacités exposées à l'UI et au domaine ; elles évitent les tests implicites sur AppMode. */
data class MuzziQCapabilities(
    val localPlayback: Boolean = true,
    val onlineCatalogue: Boolean = true,
    val onlineStreaming: Boolean = true,
    val localLibrary: Boolean = true,
    val localDownloads: Boolean = true,
    val playlists: Boolean = true,
    val recommendations: Boolean = true,
    val lyrics: Boolean = true,
    val androidAuto: Boolean = true,
    val flacAcquisition: Boolean = false,
    val torrentAcquisition: Boolean = false,
    val nasLibrary: Boolean = false,
    val monitoring: Boolean = false,
    val automaticUpgrade: Boolean = false,
    val centralSync: Boolean = false,
    val remoteJam: Boolean = false,
)

/** Source unique de la disponibilité : serveur absent signifie enrichissements absents, jamais app absente. */
class CapabilityManager {
    fun forConnection(state: ServerConnectionState): MuzziQCapabilities = when (state) {
        ServerConnectionState.CONNECTED, ServerConnectionState.DEGRADED -> MuzziQCapabilities(
            flacAcquisition = true,
            torrentAcquisition = true,
            nasLibrary = true,
            monitoring = true,
            automaticUpgrade = true,
            centralSync = true,
            remoteJam = true,
        )
        ServerConnectionState.DISCONNECTED,
        ServerConnectionState.CONNECTING,
        ServerConnectionState.ERROR -> MuzziQCapabilities()
    }
}
