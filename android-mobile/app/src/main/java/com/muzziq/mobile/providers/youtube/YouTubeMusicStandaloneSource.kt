package com.muzziq.mobile.providers.youtube

import android.content.Context
import android.net.Uri
import com.muzziq.mobile.data.model.Track
import com.muzziq.mobile.data.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/** Client minimal YouTube Music côté Android. Aucune requête ne passe par MuzziQ. */
class YouTubeMusicStandaloneSource(context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val extractor = StandaloneStreamExtractor(context)
    private val selectedStreams = ConcurrentHashMap<String, ResolvedOnlineStream>()
    private val rejectedProfiles = ConcurrentHashMap<String, MutableSet<String>>()
    private val streamHeadersByUrl = ConcurrentHashMap<String, Map<String, String>>()
    @Volatile private var activeStreamHeaders: Map<String, String> = emptyMap()
    @Volatile private var activeOnlineStream: ResolvedOnlineStream? = null

    suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        runCatching {
            val body = JSONObject()
                .put("context", context())
                .put("query", query)
                .put("params", SONG_SEARCH_PARAMS)
                .toString()
                .toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/search?key=$API_KEY")
                .post(body)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("User-Agent", WEB_USER_AGENT)
                .header("X-YouTube-Client-Name", WEB_CLIENT_ID)
                .header("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
                .build()
            val json = client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Recherche YouTube Music: HTTP ${response.code}" }
                JSONObject(response.body?.string().orEmpty())
            }
            parseSearch(json)
        }
    }

    suspend fun resolvePlayableUri(track: Track): Result<String> = withContext(Dispatchers.IO) {
        val videoId = (track.source as? TrackSource.YouTube)?.videoId
            ?: return@withContext Result.failure(IllegalArgumentException("Source YouTube invalide"))
        // Une URL InnerTube est signée mais reste normalement valable plusieurs heures.
        // Rejouer le même titre ou passer au suivant pré-résolu ne doit donc pas refaire
        // une requête player à chaque fois.
        selectedStreams[videoId]?.takeIf(::isStillValid)?.let { stream ->
            activate(stream)
            return@withContext Result.success(stream.url)
        }
        selectedStreams.remove(videoId)
        var lastError: Throwable? = null
        val rejected = rejectedProfiles[videoId].orEmpty()
        if ("extractor" !in rejected) {
            // Ne jamais sonder le CDN avant de démarrer : ce Range bloquait la
            // lecture alors que Media3 sait ouvrir le flux directement.
            val extracted = extractor.resolve(videoId)
            extracted.getOrNull()?.let { stream ->
                remember(videoId, stream)
                return@withContext Result.success(stream.url)
            }
            lastError = extracted.exceptionOrNull()
        }
        for (profile in playbackProfiles.filterNot { it.name in rejected }) {
            val result = runCatching {
                val body = JSONObject()
                    .put("context", profile.context())
                    .put("videoId", videoId)
                    .put("contentCheckOk", true)
                    .put("racyCheckOk", true)
                    .toString()
                    .toRequestBody(JSON)
                val request = Request.Builder()
                    .url("$BASE_URL/player?key=$API_KEY")
                    .post(body)
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .header("User-Agent", profile.userAgent)
                    .header("X-YouTube-Client-Name", profile.clientId)
                    .header("X-YouTube-Client-Version", profile.version)
                    .build()
                val json = client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Lecture YouTube Music: HTTP ${response.code}" }
                    JSONObject(response.body?.string().orEmpty())
                }
                val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                    ?: error("Aucun flux audio retourné")
                val bestAudio = (0 until formats.length())
                    .mapNotNull { formats.optJSONObject(it) }
                    .filter { it.optString("mimeType").startsWith("audio/") && it.optString("url").isNotBlank() }
                    .maxByOrNull { it.optLong("bitrate", 0L) }
                    ?: error("InnerTube n'a retourné aucun flux audio direct")
                val url = bestAudio.getString("url")
                ResolvedStream(url, profile)
            }
            val stream = result.getOrNull()
            if (stream != null) {
                val onlineStream = stream.toOnlineStream()
                remember(videoId, onlineStream)
                return@withContext Result.success(stream.url)
            }
            lastError = result.exceptionOrNull()
        }
        Result.failure(lastError ?: IllegalStateException("Aucun profil InnerTube n'a fourni de flux audio"))
    }

    /** Le CDN a refusé le flux après résolution : ne réessaie jamais le même
     * profil pour cette vidéo pendant la session et force le profil suivant. */
    fun markCurrentProfileRejected(videoId: String) {
        val stream = selectedStreams.remove(videoId) ?: return
        streamHeadersByUrl.remove(stream.url)
        if (activeStreamHeaders == stream.headers) activeStreamHeaders = emptyMap()
        if (activeOnlineStream?.url == stream.url) activeOnlineStream = null
        val key = if (stream.profileKey.startsWith("extractor:")) "extractor" else stream.profileKey
        rejectedProfiles.getOrPut(videoId) { ConcurrentHashMap.newKeySet() }.add(key)
    }

    fun selectedMimeType(videoId: String): String? = selectedStreams[videoId]?.mimeType

    /** Résout le titre suivant à l'avance sans commencer sa lecture. */
    suspend fun preload(track: Track) {
        resolvePlayableUri(track)
    }

    /** En-têtes du profil ayant effectivement validé cette URL, consommés par Media3. */
    /** Media3 peut normaliser une URL signée lors de sa conversion en Uri. La
     * correspondance exacte reste préférable, mais le lecteur n'a qu'un flux
     * en ligne actif : les en-têtes actifs sont le repli sûr dans ce cas. */
    fun streamHeaders(url: String): Map<String, String> =
        streamHeadersByUrl[url] ?: activeStreamHeaders

    fun streamRequest(url: String): StreamRequest {
        // Media3 peut normaliser le texte d'une URL signée lors de sa
        // conversion en Uri. Le flux actif reste donc le repli fiable pour
        // conserver les mêmes headers et contraintes de lecture.
        val stream = selectedStreams.values.firstOrNull { it.url == url } ?: activeOnlineStream
        return StreamRequest(
            headers = stream?.headers ?: activeStreamHeaders,
            contentLengthBytes = stream?.contentLengthBytes,
            requireBoundedRange = stream?.requireBoundedRange == true,
            rangeChunkSizeBytes = stream?.rangeChunkSizeBytes
                ?.takeIf { it > 0 }
                ?: ResolvedOnlineStream.DEFAULT_RANGE_CHUNK_BYTES,
            useRangeChunks = stream?.useRangeChunks == true,
        )
    }

    private fun remember(videoId: String, stream: ResolvedOnlineStream) {
        selectedStreams[videoId] = stream
        activate(stream)
    }

    private fun activate(stream: ResolvedOnlineStream) {
        streamHeadersByUrl[stream.url] = stream.headers
        activeStreamHeaders = stream.headers
        activeOnlineStream = stream
    }

    private fun isStillValid(stream: ResolvedOnlineStream): Boolean {
        val expiryMs = Uri.parse(stream.url).getQueryParameter("expire")
            ?.toLongOrNull()?.times(1_000L)
        return expiryMs?.let { System.currentTimeMillis() < it - URL_EXPIRY_SAFETY_MS }
            ?: (System.currentTimeMillis() - stream.resolvedAtEpochMs < SESSION_URL_TTL_MS)
    }

    private fun context() = JSONObject()
        .put("client", JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", WEB_CLIENT_VERSION)
            .put("hl", "fr")
            .put("gl", "BE"))

    private data class PlaybackProfile(
        val name: String,
        val version: String,
        val clientId: String,
        val userAgent: String,
        val mimeType: String,
    ) {
        fun context(): JSONObject {
            val value = JSONObject().put("client", JSONObject()
                .put("clientName", name)
                .put("clientVersion", version)
                .put("hl", "fr")
                .put("gl", "BE"))
            if (name == "WEB_EMBEDDED") {
                value.put("thirdParty", JSONObject().put("embedUrl", "https://www.reddit.com/"))
            }
            return value
        }

        fun streamHeaders(): Map<String, String> = buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            put("Accept-Language", "fr-BE,fr;q=0.9")
            if (name == "WEB_REMIX" || name == "WEB_EMBEDDED") {
                put("Origin", "https://music.youtube.com")
                put("Referer", "https://music.youtube.com/")
            }
        }
    }

    private data class ResolvedStream(val url: String, val profile: PlaybackProfile) {
        fun toOnlineStream() = ResolvedOnlineStream(
            url = url,
            mimeType = profile.mimeType,
            headers = profile.streamHeaders(),
            profileKey = profile.name,
        )
    }

    data class StreamRequest(
        val headers: Map<String, String>,
        val contentLengthBytes: Long?,
        val requireBoundedRange: Boolean,
        val rangeChunkSizeBytes: Long,
        val useRangeChunks: Boolean,
    )

    private fun parseSearch(root: JSONObject): List<Track> {
        val results = mutableListOf<Track>()
        // Ne parcourt jamais tout le JSON : les réponses contiennent aussi des
        // recommandations, des cartes d'actualité et des sections de navigation.
        // Seules les sections de résultats de l'onglet Songs sont des pistes.
        val sections = root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: return emptyList()
        for (index in 0 until sections.length()) {
            val section = sections.optJSONObject(index) ?: continue
            val items = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                ?: section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                ?: continue
            for (itemIndex in 0 until items.length()) {
                val renderer = items.optJSONObject(itemIndex)
                    ?.optJSONObject("musicResponsiveListItemRenderer")
                    ?: continue
                trackFromRenderer(renderer, null)?.let(results::add)
            }
        }
        return results.distinctBy { it.id }.take(30)
    }

    /** Les recherches Music alternent `musicShelfRenderer` et `musicCardShelfRenderer`.
     * On parcourt les deux formes, y compris celles imbriquées dans les cartes artiste. */
    private fun collectTracks(node: Any?, inheritedArtist: String?, results: MutableList<Track>) {
        when (node) {
            is JSONArray -> for (index in 0 until node.length()) collectTracks(node.opt(index), inheritedArtist, results)
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { renderer ->
                    trackFromRenderer(renderer, inheritedArtist)?.let(results::add)
                    return
                }
                node.optJSONObject("musicCardShelfRenderer")?.let { card ->
                    val artist = runsText(card.optJSONObject("title")).ifBlank { inheritedArtist.orEmpty() }
                    collectTracks(card.optJSONArray("contents"), artist.takeIf { it.isNotBlank() }, results)
                    return
                }
                node.optJSONObject("musicShelfRenderer")?.let { shelf ->
                    collectTracks(shelf.optJSONArray("contents"), inheritedArtist, results)
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) collectTracks(node.opt(keys.next()), inheritedArtist, results)
            }
        }
    }

    private fun trackFromRenderer(renderer: JSONObject, inheritedArtist: String?): Track? {
        val flex = renderer.optJSONArray("flexColumns")
        val titleColumn = flex?.optJSONObject(0)
        val title = runsText(titleColumn).ifBlank { return null }
        val playlistData = renderer.optJSONObject("playlistItemData")
        val id = renderer.optString("videoId").takeIf { it.isNotBlank() }
            ?: playlistData?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: watchVideoId(titleColumn)
            ?: watchVideoId(renderer.optJSONObject("overlay"))
            ?: return null
        val artist = artistFromColumns(flex).ifBlank { inheritedArtist.orEmpty() }.ifBlank { "Artiste inconnu" }
        return Track(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl(renderer, id),
            source = TrackSource.YouTube(id),
        )
    }

    private fun artistFromColumns(columns: JSONArray?): String {
        if (columns == null) return ""
        for (index in 1 until columns.length()) {
            val column = columns.optJSONObject(index) ?: continue
            val runs = column.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
            for (runIndex in 0 until runs.length()) {
                val run = runs.optJSONObject(runIndex) ?: continue
                val pageType = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    ?.optJSONObject("browseEndpointContextSupportedConfigs")?.optJSONObject("browseEndpointContextMusicConfig")
                    ?.optString("pageType")
                if (pageType == "MUSIC_PAGE_TYPE_ARTIST") return run.optString("text")
            }
        }
        return ""
    }

    private fun watchVideoId(node: Any?): String? {
        when (node) {
            is JSONArray -> for (index in 0 until node.length()) watchVideoId(node.opt(index))?.let { return it }
            is JSONObject -> {
                node.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) watchVideoId(node.opt(keys.next()))?.let { return it }
            }
        }
        return null
    }

    private fun artworkUrl(renderer: JSONObject, videoId: String): String {
        val thumbnails = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val fromResponse = thumbnails?.let { items ->
            (0 until items.length())
                .mapNotNull { items.optJSONObject(it) }
                .maxByOrNull { it.optInt("width", 0) }
                ?.optString("url")
                ?.takeIf { it.isNotBlank() }
        }
        return fromResponse ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    private fun runsText(node: JSONObject?): String {
        val text = node?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text") ?: node
        return text?.optJSONArray("runs")?.let { runs ->
                (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            }.orEmpty()
    }

    companion object {
        private const val URL_EXPIRY_SAFETY_MS = 30_000L
        private const val SESSION_URL_TTL_MS = 20 * 60 * 1_000L
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val WEB_CLIENT_ID = "67"
        private const val WEB_CLIENT_VERSION = "1.20260114.03.00"
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val SONG_SEARCH_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        /** Secours brut uniquement : le chemin normal passe par l'extracteur.
         * Les flux iOS imposent des plages bornées et ne sont donc pas remis à
         * un DataSource progressif standard. */
        private val playbackProfiles = listOf(
            PlaybackProfile(
                "TVHTML5", "7.20260707.07.00", "7",
                "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold " +
                    "(unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
                "audio/webm"
            ),
            PlaybackProfile(
                "WEB_EMBEDDED", "2.20260708.00.00", "56",
                WEB_USER_AGENT, "audio/webm"
            ),
            PlaybackProfile(
                "ANDROID", "20.10.38", "3",
                "com.google.android.youtube/20.10.38 (Linux; U; Android 14)", "audio/webm"
            ),
        )
    }
}
