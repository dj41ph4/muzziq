package com.muzziq.mobile.providers.youtube

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.InnerTube

/** Résolution locale des flux audio : le téléphone contacte directement YouTube. */
class YouTubeDirectExtractor {
    private val httpClient = HttpClient(OkHttp)
    private val innerTube = InnerTube(httpClient)
    private val configStore = RemotePlayerConfigStore(
        httpClient = httpClient,
        repository = PlayerConfigRepository.disabled(),
    )
    private val cipherService = YouTubeCipherService(httpClient, configStore)
    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(httpClient, innerTube, configStore),
        cipherService = cipherService,
        innerTube = innerTube,
    )

    suspend fun resolve(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = extractor.extract(
                videoId = videoId,
                hints = ContentHints(wantVideo = false),
                audioQuality = AudioQuality.HIGH,
            ) ?: error("Aucun flux audio disponible")
            stream.audioUrl
        }
    }

    fun close() {
        httpClient.close()
    }
}
