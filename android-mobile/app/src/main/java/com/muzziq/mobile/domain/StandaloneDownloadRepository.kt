package com.muzziq.mobile.domain

import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.standalone.StandaloneMusicSource

/**
 * Implémentation standalone de DownloadRepository — pas un mock : en
 * standalone, chaque morceau de la bibliothèque locale EST déjà un fichier
 * sur l'appareil (§56.4, `StandaloneMusicSource` scanne `MediaStore`, ne
 * connaît que des fichiers réels). "Téléchargé" et "dans la bibliothèque
 * locale" sont donc littéralement le même ensemble ici — `requestDownload`
 * est un no-op délibéré et honnête, pas une fonctionnalité manquante
 * déguisée : il n'y a rien à télécharger pour un morceau qui est déjà local.
 *
 * Pas encore d'équivalent pour le mode Lié (télécharger un morceau serveur
 * pour lecture hors-ligne, plan §57 DeviceOfflineItem) — ce cas-là reste
 * vraiment non implémenté, contrairement à celui-ci.
 */
class StandaloneDownloadRepository(private val standalone: StandaloneMusicSource) : DownloadRepository {
    override suspend fun downloadedTrackIds(): Result<List<String>> =
        standalone.library().map { tracks -> tracks.map { it.id } }

    override suspend fun requestDownload(track: Track): Result<Unit> = Result.success(Unit)
}
