package com.muzziq.mobile.providers.spotify

import com.muzziq.mobile.security.CredentialVault

/** Jetons Spotify réels — jamais persistés ailleurs qu'via [CredentialVault]
 * (jamais dans Room/LinkedMusicAccountEntity, jamais dans AppPrefs/DataStore en
 * clair, jamais loggés). [expiresAtEpochMs] permet de rafraîchir un peu avant
 * expiration plutôt que d'attendre un 401 (voir SpotifyAuthManager.validAccessToken). */
data class SpotifyTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val scope: String?,
)

/**
 * Façade CredentialVault → jetons Spotify typés. Ne fait aucun appel réseau —
 * uniquement lecture/écriture du coffre chiffré (voir security/CredentialVault.kt).
 */
class SpotifyCredentialStore(private val vault: CredentialVault) {
    suspend fun save(tokens: SpotifyTokens) {
        vault.store(KEY_ACCESS_TOKEN, tokens.accessToken)
        vault.store(KEY_REFRESH_TOKEN, tokens.refreshToken)
        vault.store(KEY_EXPIRES_AT, tokens.expiresAtEpochMs.toString())
        vault.store(KEY_SCOPE, tokens.scope.orEmpty())
    }

    suspend fun load(): SpotifyTokens? {
        val accessToken = vault.retrieve(KEY_ACCESS_TOKEN) ?: return null
        val refreshToken = vault.retrieve(KEY_REFRESH_TOKEN) ?: return null
        val expiresAt = vault.retrieve(KEY_EXPIRES_AT)?.toLongOrNull() ?: return null
        val scope = vault.retrieve(KEY_SCOPE)?.takeIf { it.isNotBlank() }
        return SpotifyTokens(accessToken, refreshToken, expiresAt, scope)
    }

    /** Déconnexion (règle absolue du plan) : efface uniquement les jetons, jamais les
     * données MuzziQ (favoris/playlists/historique/mappings/downloads restent intacts,
     * aucune de ces tables ne référence ce coffre). */
    suspend fun clear() {
        vault.remove(KEY_ACCESS_TOKEN)
        vault.remove(KEY_REFRESH_TOKEN)
        vault.remove(KEY_EXPIRES_AT)
        vault.remove(KEY_SCOPE)
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "spotify_access_token"
        const val KEY_REFRESH_TOKEN = "spotify_refresh_token"
        const val KEY_EXPIRES_AT = "spotify_expires_at"
        const val KEY_SCOPE = "spotify_scope"
    }
}
