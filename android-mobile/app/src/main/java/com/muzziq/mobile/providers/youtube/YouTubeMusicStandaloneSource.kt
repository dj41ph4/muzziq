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
import java.util.concurrent.TimeUnit

/** Client minimal YouTube Music côté Android. Aucune requête ne passe par MuzziQ. */
class YouTubeMusicStandaloneSource {
    private val extractor = YouTubeDirectExtractor()
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
        val extracted = extractor.resolve(videoId)
        if (extracted.isSuccess) return@withContext extracted
        runCatching {
            val body = JSONObject()
                .put("context", context())
                .put("videoId", videoId)
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
            for (index in 0 until formats.length()) {
                val format = formats.optJSONObject(index) ?: continue
                val mime = format.optString("mimeType")
                val url = format.optString("url")
                if (mime.startsWith("audio/") && url.isNotBlank()) return@runCatching url
            }
            error("Flux audio chiffré ou indisponible; le solveur de signature doit encore être activé")
        }
    }

    private fun context() = JSONObject()
        .put("client", JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20260901.01.00")
            .put("hl", "fr")
            .put("gl", "BE"))

    private fun parseSearch(root: JSONObject): List<Track> {
        val results = mutableListOf<Track>()
        val contents = root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents") ?: return results
        for (i in 0 until contents.length()) {
            val shelf = contents.optJSONObject(i)?.optJSONObject("musicShelfRenderer") ?: continue
            val items = shelf.optJSONArray("contents") ?: continue
            for (j in 0 until items.length()) {
                val renderer = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val playlistData = renderer.optJSONObject("playlistItemData")
                val id = renderer.optString("videoId").takeIf { it.isNotBlank() }
                    ?: playlistData?.optString("videoId")?.takeIf { it.isNotBlank() }
                    ?: playlistData?.optString("playlistSetVideoId")?.takeIf { it.isNotBlank() }
                    ?: continue
                val flex = renderer.optJSONArray("flexColumns")
                val title = runsText(flex?.optJSONObject(0)).ifBlank { "Titre inconnu" }
                val artist = runsText(flex?.optJSONObject(1)).ifBlank { "Artiste inconnu" }
                results += Track(id, title, artist, source = TrackSource.YouTube(id))
            }
        }
        return results.distinctBy { it.id }.take(30)
    }

    private fun runsText(column: JSONObject?): String =
        column?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")?.let { runs ->
                (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            }.orEmpty()

    companion object {
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
