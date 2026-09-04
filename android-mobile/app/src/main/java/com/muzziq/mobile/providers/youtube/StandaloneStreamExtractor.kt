package com.muzziq.mobile.providers.youtube

import android.content.Context
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Extraction complète d'un flux audio standalone. La configuration de signature
 * est persistée et rechargée par ETag afin de suivre les rotations du player.
 */
class StandaloneStreamExtractor(context: Context) {
    private val appContext = context.applicationContext
    private val httpClient = HttpClient(OkHttp)
    private val innerTube = InnerTube(httpClient)
    private val configRepository = AndroidPlayerConfigRepository(appContext)
    private val configStore = RemotePlayerConfigStore(httpClient, configRepository)
    private val cipherService = YouTubeCipherService(httpClient, configStore)
    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(httpClient, innerTube, configStore),
        cipherService = cipherService,
        innerTube = innerTube,
    )

    suspend fun resolve(videoId: String): Result<ResolvedOnlineStream> = runCatching {
        val stream = requireNotNull(
            extractor.extract(
                videoId = videoId,
                hints = ContentHints(wantVideo = false).withStreamCapabilities(
                    allowHls = false,
                    allowSabr = false,
                    // Les flux YouTube récents peuvent exiger des plages bornées.
                    // MuzziQ conserve la taille exacte fournie par l'extracteur.
                    allowBoundedRange = true,
                ),
                excludedClients = emptySet(),
                audioQuality = AudioQuality.HIGH,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            ),
        ) { "Aucun flux audio exploitable" }
        ResolvedOnlineStream(
            url = stream.audioUrl,
            mimeType = stream.mimeType,
            headers = stream.headers,
            profileKey = "extractor:${stream.profileId}",
            contentLengthBytes = stream.contentLengthBytes,
            requireBoundedRange = stream.requireBoundedRange,
            rangeChunkSizeBytes = stream.rangeChunkSizeBytes,
            useRangeChunks = stream.useRangeChunks,
            clientName = stream.clientName,
        )
    }

    private class AndroidPlayerConfigRepository(context: Context) : PlayerConfigRepository {
        private val preferences = context.getSharedPreferences("stream_player_config", Context.MODE_PRIVATE)

        override val enabled = true
        override val sourceUrl = CONFIG_URL
        override val defaultSourceUrl = CONFIG_URL
        override var cachedJson: String
            get() = preferences.getString("json", "").orEmpty()
            set(value) = preferences.edit().putString("json", value).apply()
        override var cachedAtMs: Long
            get() = preferences.getLong("cached_at_ms", 0L)
            set(value) = preferences.edit().putLong("cached_at_ms", value).apply()
        override var cachedSourceUrl: String
            get() = preferences.getString("source_url", "").orEmpty()
            set(value) = preferences.edit().putString("source_url", value).apply()
        override var cachedEtag: String
            get() = preferences.getString("etag", "").orEmpty()
            set(value) = preferences.edit().putString("etag", value).apply()

        private companion object {
            const val CONFIG_URL =
                "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        }
    }
}

data class ResolvedOnlineStream(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val profileKey: String,
    val contentLengthBytes: Long? = null,
    val requireBoundedRange: Boolean = false,
    val rangeChunkSizeBytes: Long = DEFAULT_RANGE_CHUNK_BYTES,
    val useRangeChunks: Boolean = false,
    val clientName: String? = null,
) {
    companion object {
        const val DEFAULT_RANGE_CHUNK_BYTES = 512L * 1024L
    }
}
