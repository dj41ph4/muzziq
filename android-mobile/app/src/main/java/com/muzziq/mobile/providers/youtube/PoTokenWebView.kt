package com.muzziq.mobile.providers.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ## AVERTISSEMENT — jamais vérifié sur appareil réel
 *
 * Ce fichier n'a **jamais pu être exécuté** dans cet environnement : aucun
 * émulateur ni appareil Android n'y est disponible (voir CLAUDE.md, "Ce qui
 * n'est PAS encore fait"). La seule vérification possible ici est la
 * compilation (`gradle testDebugUnitTest` + `assembleRelease` en CI GitHub
 * Actions). Conformément à la règle #4/#5 du projet ("le typecheck ne prouve
 * rien" / "ne jamais faire confiance à une capacité déclarée plutôt que
 * testée"), ce module n'est **pas** branché dans `ServerMusicSource` ni
 * `StandaloneMusicSource` — il est livré comme brique isolée. Avant toute
 * activation en production : capturer un flux réel sur un vrai appareil
 * (Android Studio + émulateur, ou device physique), vérifier que le
 * `globalName`/la convention d'appel du VM BotGuard supposée dans
 * `assets/po_token.html` correspond bien à ce que Google sert réellement à
 * cet instant (elle change sans préavis), et confirmer qu'un jeton
 * d'intégrité obtenu ici est effectivement accepté par le pipeline de
 * lecture serveur (`streamingData`/`serviceIntegrityDimensions.poToken`).
 *
 * ## Pourquoi une WebView et pas un solveur BotGuard maison
 *
 * Même conclusion que côté serveur (`src/providers/youtube-music/
 * poTokenBrowser.ts`, Playwright/Chromium) : le challenge BotGuard actuel
 * détecte l'absence d'un vrai moteur de rendu (jsdom testé et cassé en
 * pratique côté serveur le 2026-09-02, voir `docs/reverse-engineering/
 * youtube-music/README.md`). Sur Android, l'équivalent standard d'un "vrai
 * moteur de rendu" embarquable sans dépendance lourde est `android.webkit.
 * WebView` — composant système, pas un binaire à embarquer (contrairement à
 * Chromium côté serveur, ~300 Mo). C'est le mécanisme documenté par de
 * nombreux lecteurs YouTube Music tiers open source pour cette tâche précise
 * (voir `docs/reverse-engineering/youtube-music/metrolist-analysis.md`,
 * section PoToken — étude de comportement uniquement, aucun code copié,
 * Metrolist/innertubex sont GPL-3.0).
 *
 * ## Flux implémenté
 *
 * 1. Charge `assets/po_token.html` dans une `WebView` invisible, JS activé,
 *    `blockNetworkLoads = true` (la page elle-même ne peut faire aucun appel
 *    réseau — seuls les appels HTTP explicites ci-dessous, faits en Kotlin
 *    via OkHttp, sortent réellement).
 * 2. La page demande le challenge via le pont [Bridge.fetchCreateChallenge],
 *    qui fait l'appel réel `POST https://www.youtube.com/api/jnn/v1/Create`
 *    (endpoint documenté publiquement — voir `metrolist-analysis.md` — et
 *    corroboré indépendamment par d'autres projets tiers sous licence
 *    permissive qui exposent le même flux Google ; jamais capturé en direct
 *    dans cette session faute d'appareil).
 * 3. La page évalue le script interpréteur renvoyé (récupéré par Kotlin via
 *    [Bridge.fetchInterpreterScript], jamais par la page elle-même — réseau
 *    bloqué), obtient un VM BotGuard, et le fait résoudre le challenge.
 * 4. La page relaie la réponse via [Bridge.submitBotguardResponse], qui fait
 *    l'appel réel `POST https://www.youtube.com/api/jnn/v1/GenerateIT` et
 *    renvoie l'`integrityToken` + sa durée de vie.
 * 5. Le jeton est mis en cache ([cachedToken]) avec une marge de sécurité
 *    ([SAFETY_MARGIN_MS]) avant expiration — jamais servi après expiration
 *    réelle moins la marge, jamais optimiste (règle #7 du projet, même
 *    principe appliqué ici à un jeton plutôt qu'une fusion d'identité).
 *
 * Si la `WebView` meurt (processus de rendu tué par le système — courant sur
 * un appareil sous pression mémoire), [onRenderProcessGone] la marque morte
 * et la fait recréer proprement au prochain appel plutôt que de laisser
 * planter l'app ou de renvoyer indéfiniment une erreur.
 *
 * `x-goog-api-key` : clé publique documentée par plusieurs implémentations
 * tierces indépendantes et open source de ce même flux Google (pas un secret
 * serveur MuzziQ, comparable à la clé InnerTube déjà utilisée dans
 * `docs/reverse-engineering/youtube-music/README.md` pour SEARCH) — reportée
 * ici telle que documentée publiquement, non interceptée directement par ce
 * dépôt (aucun appareil pour le faire). À reconfirmer par capture réelle
 * avant activation en production, comme le reste de ce fichier.
 */
class PoTokenWebView private constructor(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val stateLock = Mutex()
    private var webView: WebView? = null
    private var initDeferred: CompletableDeferred<Unit>? = null
    private var integrityDeferred: CompletableDeferred<IntegrityToken>? = null
    private var cachedToken: IntegrityToken? = null

    /** Jeton d'intégrité valide, obtenu depuis le cache si assez frais, sinon
     * en relançant le flux complet (recrée la WebView si nécessaire). Ne
     * renvoie jamais un jeton dont il ne reste pas au moins [SAFETY_MARGIN_MS]
     * avant expiration réelle — un appelant qui reçoit un `Result.success`
     * ici peut l'utiliser immédiatement sans revérifier l'expiration. */
    suspend fun obtainIntegrityToken(): Result<IntegrityToken> = stateLock.withLock {
        val cached = cachedToken
        if (cached != null && System.currentTimeMillis() < cached.expiresAtEpochMs - SAFETY_MARGIN_MS) {
            return@withLock Result.success(cached)
        }
        runCatching {
            withTimeout(TOTAL_FLOW_TIMEOUT_MS) {
                ensureWebViewReady()
                val token = awaitIntegrityToken()
                cachedToken = token
                token
            }
        }
    }

    /** Détruit la WebView (à appeler depuis un cycle de vie propre —
     * `onCleared`/`onDestroy` de l'appelant — jamais indispensable pour la
     * correction du flux, seulement pour libérer les ressources WebView tôt). */
    fun destroy() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }

    private suspend fun ensureWebViewReady() {
        if (webView != null) return
        val deferred = CompletableDeferred<Unit>()
        initDeferred = deferred
        mainHandler.post { createWebView() }
        deferred.await()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        val wv = WebView(appContext)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = false
        // Coeur de la contrainte de sécurité : la page ne doit jamais pouvoir
        // faire un appel réseau elle-même — tout ce qui sort passe par le
        // pont [Bridge], contrôlé et journalisable côté Kotlin.
        wv.settings.blockNetworkLoads = true
        wv.settings.blockNetworkImage = true
        wv.addJavascriptInterface(Bridge(), BRIDGE_NAME)
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                // Filet de sécurité en plus de blockNetworkLoads : même si la
                // page tentait de charger une ressource externe, elle est
                // interceptée et vidée ici plutôt que servie.
                return WebResourceResponse("text/plain", "utf-8", null)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Processus de rendu tué par le système (pression mémoire,
                // crash interne) — ne jamais laisser planter l'app hôte.
                // Marque l'instance morte ; le prochain appel à
                // obtainIntegrityToken() en recrée une neuve.
                view.destroy()
                if (webView === view) {
                    webView = null
                }
                initDeferred?.takeIf { !it.isCompleted }
                    ?.completeExceptionally(IllegalStateException("Processus de rendu WebView terminé (onRenderProcessGone)"))
                integrityDeferred?.takeIf { !it.isCompleted }
                    ?.completeExceptionally(IllegalStateException("Processus de rendu WebView terminé pendant la résolution (onRenderProcessGone)"))
                return true // ne pas laisser le système gérer (crash) — géré ici.
            }
        }
        webView = wv
        wv.loadUrl(LOCAL_PAGE_URL)
    }

    private suspend fun awaitIntegrityToken(): IntegrityToken {
        val deferred = CompletableDeferred<IntegrityToken>()
        integrityDeferred = deferred
        return deferred.await()
    }

    /** Pont JS ↔ Kotlin exposé à `assets/po_token.html` sous le nom
     * [BRIDGE_NAME]. Chaque méthode `@JavascriptInterface` s'exécute sur un
     * thread binder dédié (pas le thread UI) — les appels HTTP synchrones
     * ci-dessous sont donc sans risque de bloquer le rendu, contrairement à
     * un appel équivalent fait depuis le thread principal. */
    private inner class Bridge {

        @JavascriptInterface
        fun fetchCreateChallenge(): String = runCatching {
            val body = JSONArray().apply {
                put(REQUEST_KEY)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(CREATE_URL)
                .headers(botguardHeaders())
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Create a répondu ${response.code} : $raw")
                }
                parseCreateResponse(raw)
            }
        }.getOrElse { err -> JSONObject().put("error", err.message ?: err.toString()).toString() }

        @JavascriptInterface
        fun fetchInterpreterScript(url: String): String = runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Téléchargement du script interpréteur refusé (${response.code})")
                response.body?.string().orEmpty()
            }
        }.getOrElse { "" }

        @JavascriptInterface
        fun submitBotguardResponse(botguardResponse: String): String = runCatching {
            val body = JSONArray().apply {
                put(REQUEST_KEY)
                put(botguardResponse)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(GENERATE_IT_URL)
                .headers(botguardHeaders())
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("GenerateIT a répondu ${response.code} : $raw")
                raw
            }
        }.getOrElse { err -> JSONObject().put("error", err.message ?: err.toString()).toString() }

        @JavascriptInterface
        fun onReady() {
            initDeferred?.takeIf { !it.isCompleted }?.complete(Unit)
        }

        @JavascriptInterface
        fun onIntegrityToken(generateItResponseRaw: String) {
            val deferred = integrityDeferred?.takeIf { !it.isCompleted } ?: return
            runCatching { parseIntegrityToken(generateItResponseRaw) }
                .onSuccess { deferred.complete(it) }
                .onFailure { deferred.completeExceptionally(it) }
        }

        @JavascriptInterface
        fun onError(stage: String, message: String) {
            val error = IllegalStateException("PoToken WebView — échec à l'étape '$stage' : $message")
            initDeferred?.takeIf { !it.isCompleted }?.completeExceptionally(error)
            integrityDeferred?.takeIf { !it.isCompleted }?.completeExceptionally(error)
        }
    }

    private fun botguardHeaders() = okhttp3.Headers.Builder()
        .add("Content-Type", "application/json+protobuf")
        .add("x-goog-api-key", GOOG_API_KEY)
        .add("x-user-agent", "grpc-web-javascript/0.1")
        .build()

    /**
     * Réponse `jnn/v1/Create`, normalisée en un objet JSON à clés nommées
     * (`interpreterJavascript`/`interpreterUrl`/`program`/`globalName`) pour
     * que `po_token.html` n'ait pas à connaître les positions numériques.
     *
     * Forme réelle documentée par plusieurs implémentations tierces
     * indépendantes et publiques de ce même flux Google (licences
     * permissives ; recoupement structurel, pas une copie de code) :
     * - la réponse HTTP est `[a, b]` où `b`, si présent et de type chaîne,
     *   encode le tableau de challenge réel sous une forme "brouillée" —
     *   base64 puis chaque octet décalé de +97 avant décodage UTF-8 (une
     *   simple obfuscation de transport, pas une protection cryptographique
     *   — comparable en esprit à un ROT-N appliqué à des octets plutôt qu'à
     *   des lettres) ; sinon `a` est déjà le tableau de challenge réel.
     * - dans ce tableau de challenge : index 0 = messageId (ignoré ici),
     *   index 1 = script interpréteur enveloppé (tableau dont le premier
     *   élément est la source JS), index 2 = URL de l'interpréteur enveloppée
     *   (même forme), index 3 = hash de l'interpréteur (ignoré ici),
     *   index 4 = `program` (le challenge BotGuard proprement dit, à
     *   transmettre tel quel au VM), index 5 = `globalName` (nom sous lequel
     *   le script interpréteur expose son VM sur `window`).
     *
     * Jamais interceptée en direct dans cette session (aucun appareil
     * disponible) — voir l'avertissement en tête de fichier.
     */
    private fun parseCreateResponse(raw: String): String {
        val topLevel = runCatching { JSONArray(raw) }.getOrNull()
            ?: return raw // Repli : peut-être déjà un objet à clés nommées (variante d'API) — transmis tel quel.

        val challengeArray = run {
            val maybeScrambled = topLevel.optString(1, null)
            if (!maybeScrambled.isNullOrEmpty()) {
                runCatching { JSONArray(descramble(maybeScrambled)) }.getOrNull()
            } else {
                null
            } ?: topLevel.optJSONArray(0) ?: topLevel
        }

        val result = JSONObject()
        firstStringOfWrapped(challengeArray, 1)?.let { result.put("interpreterJavascript", it) }
        firstStringOfWrapped(challengeArray, 2)?.let { result.put("interpreterUrl", it) }
        result.put("program", challengeArray.opt(4))
        challengeArray.optString(5, null)?.takeIf { it.isNotBlank() }?.let { result.put("globalName", it) }
        return result.toString()
    }

    /** `descramble` : base64 → chaque octet +97 (modulo 256) → UTF-8. Simple
     * transformation d'obfuscation de transport documentée publiquement pour
     * ce flux (voir commentaire de [parseCreateResponse]) — pas un secret,
     * réimplémentée ici indépendamment à partir de la description du
     * comportement, jamais depuis du code copié. */
    private fun descramble(base64: String): String {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        val shifted = ByteArray(bytes.size) { i -> ((bytes[i].toInt() and 0xFF) + 97).toByte() }
        return String(shifted, Charsets.UTF_8)
    }

    /** Certains champs de la réponse Create sont "enveloppés" dans un
     * tableau à un seul élément (protection `TrustedTypes` côté navigateur
     * réel — sans objet pour cette page qui n'exécute jamais dans un vrai
     * DOM d'application web, mais la forme du champ reste la même). */
    private fun firstStringOfWrapped(array: JSONArray, index: Int): String? {
        val wrapped = array.opt(index)
        val candidate = when (wrapped) {
            is JSONArray -> wrapped.optString(0, null)
            is String -> wrapped
            else -> null
        }
        return candidate?.takeIf { it.isNotBlank() }
    }

    private fun parseIntegrityToken(generateItResponseRaw: String): IntegrityToken {
        val obj = runCatching { JSONObject(generateItResponseRaw) }.getOrNull()
        if (obj != null && obj.has("error")) {
            error("GenerateIT : ${obj.optString("error")}")
        }
        val array = runCatching { JSONArray(generateItResponseRaw) }.getOrElse {
            error("Réponse GenerateIT illisible (ni tableau JSON attendu) : $generateItResponseRaw")
        }
        // Forme documentée publiquement (recoupée sur plusieurs
        // implémentations tierces indépendantes de ce même flux Google) :
        // [0]=integrityToken, [1]=estimatedTtlSecs, [2]=mintRefreshThreshold,
        // [3]=websafeFallbackToken (ce dernier ignoré ici). Jamais confirmée
        // par une capture réelle dans cet environnement.
        val token = array.optString(0, null) ?: error("GenerateIT sans jeton d'intégrité en position 0")
        // 3600s (1h) par défaut si absent/illisible — volontairement
        // conservateur (mieux vaut redemander un jeton trop tôt que trop tard).
        val ttlSeconds = array.optLong(1, DEFAULT_TTL_SECONDS)
        val expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds)
        return IntegrityToken(value = token, expiresAtEpochMs = expiresAt)
    }

    data class IntegrityToken(
        val value: String,
        val expiresAtEpochMs: Long,
    )

    companion object {
        private const val BRIDGE_NAME = "PoTokenHost"
        private const val LOCAL_PAGE_URL = "file:///android_asset/po_token.html"

        // Endpoints documentés publiquement (voir avertissement en tête de
        // fichier) — pas un secret serveur MuzziQ.
        private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        private const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
        private const val GOOG_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"

        // Clé de requête InnerTube générique publiquement documentée pour ce
        // flux (pas propre à un compte/appareil) — à reconfirmer sur appareil.
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"

        private const val DEFAULT_TTL_SECONDS = 3600L

        /** Marge de sécurité avant expiration réelle — même principe que
         * `SpotifyAuthManager.validAccessToken()` (marge 60s) mais plus
         * généreuse ici (10 min), cohérente avec ce que documente
         * `metrolist-analysis.md` pour ce flux précis. */
        private val SAFETY_MARGIN_MS = TimeUnit.MINUTES.toMillis(10)

        private val TOTAL_FLOW_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45)

        private val JSON_MEDIA_TYPE = "application/json+protobuf".toMediaType()

        @Volatile
        private var instance: PoTokenWebView? = null

        /** Instance unique réutilisée (processus Chromium/WebView déjà chaud,
         * même principe que `poTokenBrowser.ts` côté serveur — ce qui est mis
         * en cache est l'instance, jamais une valeur de jeton figée au-delà
         * de sa vraie durée de vie). */
        fun getOrCreate(context: Context): PoTokenWebView {
            return instance ?: synchronized(this) {
                instance ?: PoTokenWebView(context.applicationContext).also { instance = it }
            }
        }
    }
}
