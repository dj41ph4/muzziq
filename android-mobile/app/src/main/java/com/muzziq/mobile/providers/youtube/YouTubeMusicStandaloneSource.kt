package com.muzziq.mobile.providers.youtube

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

/** Client minimal YouTube Music côté Android. Aucune requête ne passe par MuzziQ. */
class YouTubeMusicStandaloneSource {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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
        runCatching {
            val body = JSONObject()
                .put("context", playbackContext())
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
            bestAudio.getString("url")
        }
    }

    private fun context() = JSONObject()
        .put("client", JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20260901.01.00")
            .put("hl", "fr")
            .put("gl", "BE"))

    private fun playbackContext() = JSONObject()
        .put("client", JSONObject()
            .put("clientName", "ANDROID")
            .put("clientVersion", "20.10.38")
            .put("hl", "fr")
            .put("gl", "BE"))

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
        return Track(id, title, artist, source = TrackSource.YouTube(id))
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
    }
}
