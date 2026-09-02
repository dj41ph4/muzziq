package com.muzziq.mobile.standalone

import com.muzziq.mobile.data.ApiClientFactory
import com.muzziq.mobile.data.model.AddLibraryItemRequest

/**
 * Migration Standalone → Lié (§56.4) : un événement PONCTUEL déclenché quand
 * l'utilisateur connecte, après une période standalone, un serveur MuzziQ.
 * Ne supprime jamais les fichiers locaux, ne fusionne jamais silencieusement
 * une affinité (INTERDIT 7 — même logique de confiance que l'IdentityResolver
 * serveur). Portée V1 honnête : upload des LibraryItems locaux sans équivalent
 * connu côté serveur (provider="local"), rapport visible du résultat — la
 * réconciliation fine par IdentityResolver (doublon exact vs variante) reste
 * côté serveur, ce client ne fait qu'exposer les items bruts.
 */
class MigrationManager(private val standalone: StandaloneMusicSource) {

    data class SyncReport(val reconciled: Int, val added: Int, val failed: Int)

    suspend fun migrateTo(baseUrl: String, cookie: String?): SyncReport {
        val api = ApiClientFactory.create(baseUrl)
        val local = standalone.rawTracks()
        var added = 0
        var failed = 0
        for (track in local) {
            val res = runCatching {
                api.addLibraryItem(
                    AddLibraryItemRequest(
                        provider = "local",
                        providerTrackId = track.contentUri,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationSeconds = track.durationSeconds,
                        addPolicy = "STREAM_ONLY",
                    ),
                    cookie,
                )
            }
            if (res.isSuccess && res.getOrNull()?.isSuccessful == true) added++ else failed++
        }
        // "reconciled" au sens strict (dédupliqué côté serveur via IdentityResolver)
        // n'est pas observable depuis ce client — on rapporte honnêtement ajouts/échecs,
        // jamais un nombre de réconciliations qu'on ne peut pas vérifier ici.
        return SyncReport(reconciled = 0, added = added, failed = failed)
    }
}
