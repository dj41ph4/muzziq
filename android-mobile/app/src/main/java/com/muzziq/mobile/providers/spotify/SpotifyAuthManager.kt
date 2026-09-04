package com.muzziq.mobile.providers.spotify

import android.net.Uri
import android.util.Base64
import com.muzziq.mobile.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Authorization Code + PKCE (RFC 7636) vers Spotify — flux "public client",
 * conçu pour un client qui ne peut pas garder de secret (un APK peut toujours
 * être décompilé). Jamais de `client_secret` embarqué ici, ni nulle part dans
 * ce module : c'est structurellement inutile avec PKCE, pas juste "pas encore
 * ajouté".
 *
 * Client ID : public et injecté à la construction de l'APK par la CI dans
 * `BuildConfig.SPOTIFY_CLIENT_ID`. L'utilisateur final n'a aucune propriété à
 * renseigner : le bouton Réglages lance directement Spotify.
 *
 * Statut réel Spotify "Development Mode" (vérifié sur developer.spotify.com,
 * 2026-09) : une app non étendue au mode production reste limitée à 25
 * utilisateurs autorisés explicitement (allowlist dans le dashboard
 * développeur) et nécessite un compte Premium pour le propriétaire de l'app —
 * aucun blocage pour un usage personnel/test (un seul compte, celui du
 * développeur, largement sous la limite), mais ça exclut de fait un partage
 * public de MuzziQ sans demande d'extension "Quota Extension" à Spotify.
 * Documenté ici plutôt que découvert en silence à l'usage.
 *
 * Le retour utilise un port loopback éphémère (127.0.0.1) écouté par MuzziQ
 * pendant la connexion. Chrome peut donc afficher la vraie page Spotify puis
 * revenir automatiquement dans l'application sans serveur MuzziQ, sans WebView
 * et sans intent-filter/deep link propriétaire.
 */
class SpotifyAuthManager(
    private val credentialStore: SpotifyCredentialStore,
) {
    /** Fausse si l'APK n'a pas été construit avec le connecteur Spotify. */
    fun isConfigured(): Boolean = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()

    /** Génère un `code_verifier` PKCE (43-128 caractères, RFC 7636 §4.1) — aléatoire
     * cryptographique, jamais réutilisé entre deux tentatives de connexion. */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    /** `code_challenge` = BASE64URL(SHA256(code_verifier)), méthode S256 (RFC 7636 §4.2) —
     * jamais la méthode "plain" (dégraderait PKCE à une vérification cosmétique). */
    fun codeChallengeFor(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return base64UrlNoPadding(digest)
    }

    /** URL à ouvrir dans un onglet Custom Tabs/navigateur (jamais une WebView pour un
     * login OAuth tiers — règle générale de sécurité, l'utilisateur doit voir la vraie
     * barre d'adresse accounts.spotify.com, pas une WebView que l'app contrôle). */
    fun buildAuthorizationUri(codeChallenge: String, state: String, redirectUri: String): Uri =
        Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .build()

    /** Échange le `code` reçu sur le redirect URI loopback contre les jetons réels, à partir du
     * même `codeVerifier` que celui utilisé pour construire l'URL d'autorisation. */
    suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): Result<SpotifyTokens> = runCatching {
        val res = SpotifyApiClientFactory.accounts.exchangeCode(
            grantType = "authorization_code",
            code = code,
            redirectUri = redirectUri,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            codeVerifier = codeVerifier,
        )
        if (!res.isSuccessful) error("Échange du code Spotify refusé (${res.code()})")
        val body = res.body() ?: error("Réponse Spotify vide")
        val tokens = SpotifyTokens(
            accessToken = body.accessToken,
            refreshToken = body.refreshToken ?: error("Spotify n'a renvoyé aucun refresh_token"),
            expiresAtEpochMs = System.currentTimeMillis() + body.expiresInSeconds * 1000L,
            scope = body.scope,
        )
        credentialStore.save(tokens)
        tokens
    }

    /** Access token valide, rafraîchi automatiquement si expiré/proche de l'expiration
     * (marge de 60s) — c'est la fonction que SpotifyProvider appelle avant chaque appel
     * à la Web API, jamais un accès direct au coffre pour lire l'access token seul. */
    suspend fun validAccessToken(): Result<String> {
        val stored = credentialStore.load() ?: return Result.failure(IllegalStateException("Aucun compte Spotify lié"))
        if (System.currentTimeMillis() < stored.expiresAtEpochMs - 60_000L) {
            return Result.success(stored.accessToken)
        }
        return refresh(stored.refreshToken).map { it.accessToken }
    }

    private suspend fun refresh(refreshToken: String): Result<SpotifyTokens> = runCatching {
        val res = SpotifyApiClientFactory.accounts.refreshToken(
            grantType = "refresh_token",
            refreshToken = refreshToken,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
        )
        if (!res.isSuccessful) error("Rafraîchissement du jeton Spotify refusé (${res.code()})")
        val body = res.body() ?: error("Réponse Spotify vide")
        val tokens = SpotifyTokens(
            accessToken = body.accessToken,
            // Spotify ne renvoie pas toujours un nouveau refresh_token sur ce grant —
            // garder l'ancien tant qu'un nouveau n'est pas fourni (sinon la prochaine
            // tentative de refresh échouerait avec un refresh_token vide).
            refreshToken = body.refreshToken ?: refreshToken,
            expiresAtEpochMs = System.currentTimeMillis() + body.expiresInSeconds * 1000L,
            scope = body.scope,
        )
        credentialStore.save(tokens)
        tokens
    }

    suspend fun disconnect() {
        credentialStore.clear()
    }

    suspend fun isConnected(): Boolean = credentialStore.load() != null

    private fun base64UrlNoPadding(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    companion object {
        /** Lecture + écritures déclenchées explicitement depuis les réglages ou les
         * playlists. Spotify ne donne toujours pas de droit de télécharger ses flux. */
        const val SCOPES = "user-read-private user-library-read user-library-modify playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private"
    }
}
