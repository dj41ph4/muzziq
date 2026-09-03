package com.muzziq.mobile.providers.youtube

import android.content.Context
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

    suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        runCatching {
            val body = JSONObject()
                .put("context", context())
                .put("query", query)
                .toString()
                .toRequestBody(JSON)
            val request = Request.Builder()
                .url("$BASE_URL/search?key=$API_KEY")
                .post(body)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
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
        var lastError: Throwable? = null
        val rejected = rejectedProfiles[videoId].orEmpty()
        if ("extractor" !in rejected) {
            val extracted = extractor.resolve(videoId).mapCatching { stream ->
                check(probeAudioStream(stream.url, stream.headers)) {
                    "Le CDN a refusé le flux extrait avant lecture"
                }
                stream
            }
            extracted.getOrNull()?.let { stream ->
                selectedStreams[videoId] = stream
                streamHeadersByUrl[stream.url] = stream.headers
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
                check(probeAudioStream(url, profile.streamHeaders())) {
                    "Le CDN a refusé le flux ${profile.name} avant lecture"
                }
                ResolvedStream(url, profile)
            }
            val stream = result.getOrNull()
            if (stream != null) {
                selectedStreams[videoId] = stream.toOnlineStream()
                streamHeadersByUrl[stream.url] = stream.profile.streamHeaders()
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
        val key = if (stream.profileKey.startsWith("extractor:")) "extractor" else stream.profileKey
        rejectedProfiles.getOrPut(videoId) { ConcurrentHashMap.newKeySet() }.add(key)
    }

    fun selectedMimeType(videoId: String): String? = selectedStreams[videoId]?.mimeType

    /** En-têtes du profil ayant effectivement validé cette URL, consommés par Media3. */
    fun streamHeaders(url: String): Map<String, String> = streamHeadersByUrl[url].orEmpty()

    /** Une lecture de deux octets avec Range valide l'URL signée et le profil avant
     * de déléguer au player. Une 200 est aussi acceptable : certains CDN ignorent
     * volontairement la petite plage demandée. */
    private fun probeAudioStream(url: String, headers: Map<String, String>): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request).execute().use { response ->
            response.code == 200 || response.code == 206
        }
    }

    private fun context() = JSONObject()
        .put("client", JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20260901.01.00")
            .put("hl", "fr")
            .put("gl", "BE"))

    private data class PlaybackProfile(
        val name: String,
        val version: String,
        val clientId: String,
        val userAgent: String,
        val mimeType: String,
    ) {
        fun context() = JSONObject().put("client", JSONObject()
            .put("clientName", name)
            .put("clientVersion", version)
            .put("hl", "fr")
            .put("gl", "BE"))

        fun streamHeaders(): Map<String, String> = buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            put("Accept-Language", "fr-BE,fr;q=0.9")
            if (name == "WEB_REMIX") {
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

    private fun parseSearch(root: JSONObject): List<Track> {
        val results = mutableListOf<Track>()
        collectTracks(root, null, results)
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
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        /** Profil qui fournit actuellement un flux audio direct sans jeton. */
        const val PLAYBACK_USER_AGENT =
            "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)"
        private val playbackProfiles = listOf(
            PlaybackProfile("IOS", "21.03.1", "5", PLAYBACK_USER_AGENT, "audio/mp4"),
            PlaybackProfile(
                "ANDROID", "20.10.38", "3",
                "com.google.android.youtube/20.10.38 (Linux; U; Android 14)", "audio/webm"
            ),
        )
    }
}
