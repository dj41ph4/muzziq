package com.muzziq.mobile.data

import com.muzziq.mobile.data.model.Track

/**
 * Abstraction centrale (§56.4) : les écrans (Home, Recherche, Bibliothèque, Player)
 * ne connaissent que ce contrat, jamais s'ils parlent au serveur MuzziQ ou à
 * MediaStore. Standalone et Lié sont deux implémentations à parts égales — ni
 * l'une ni l'autre n'est un "mode de repli". Voir ServerMusicSource /
 * StandaloneMusicSource.
 */
interface MusicSource {
    val label: String

    suspend fun health(): Boolean

    /** Bibliothèque complète (locale en standalone, LibraryItems côté serveur en lié). */
    suspend fun library(): Result<List<Track>>

    /** Recherche unifiée (§47) — locale (nom/artiste) en standalone, catalogue
     * YouTube Music + local en lié. */
    suspend fun search(query: String): Result<List<Track>>

    /** Résout une source jouable réelle pour ExoPlayer — jamais un flux fabriqué. */
    suspend fun resolvePlayableUri(track: Track): Result<String>

    /** Playlists — non supportées en standalone V1 (bibliothèque locale simple, §56.4). */
    suspend fun playlists(): Result<List<String>> = Result.success(emptyList())
}
