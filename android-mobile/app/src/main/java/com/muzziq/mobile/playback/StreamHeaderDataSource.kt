package com.muzziq.mobile.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import android.net.Uri
import com.muzziq.mobile.standalone.StandaloneMusicSourceHolder

/**
 * Les URLs média en ligne sont signées et liées au profil qui les a demandées.
 * Media3 crée un DataSource neuf à chaque ouverture ou reprise : les en-têtes
 * doivent donc être retrouvés à partir de l'URL à cet endroit, plutôt que
 * définis une fois globalement lors de la création du lecteur.
 */
class StreamHeaderDataSourceFactory(
    private val upstream: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = StreamHeaderDataSource(upstream.createDataSource())
}

private class StreamHeaderDataSource(
    private val upstream: DataSource,
) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val request = StandaloneMusicSourceHolder.instance?.onlineStreamRequest(dataSpec.uri)
        val headers = request?.headers.orEmpty()
        // Certains clients YouTube ne tolèrent qu'une plage bornée. La taille
        // n'est pas globale : elle est attachée au flux choisi par l'extracteur.
        // `subrange(0, ...)` conserve la position courante de DataSpec, donc les
        // reprises demandent bien le chunk suivant au CDN.
        val chunkSize = request?.rangeChunkSizeBytes
            ?.takeIf { it > 0 }
            ?: CHUNK_LENGTH_BYTES
        // Les URLs audio signées de YouTube peuvent répondre 403 après une
        // lecture non bornée, même lorsque l'extracteur ne marque pas
        // explicitement le flux comme "range-only". Toutes les ouvertures
        // d'un flux résolu sont donc bornées, comme dans MetroList. La
        // position courante est conservée par DataSpec.subrange.
        val boundedLength = request?.contentLengthBytes
            ?.let { remaining -> (remaining - dataSpec.position).coerceAtLeast(0L) }
            ?.let { remaining -> minOf(chunkSize, remaining) }
            ?.takeIf { it > 0L }
        val boundedSpec = if (request == null || dataSpec.length >= 0) dataSpec
        else dataSpec.subrange(0, boundedLength ?: chunkSize)
        return upstream.open(
            if (headers.isEmpty()) boundedSpec else boundedSpec.withAdditionalHeaders(headers),
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    private companion object {
        const val CHUNK_LENGTH_BYTES = 512L * 1024L
    }
}
