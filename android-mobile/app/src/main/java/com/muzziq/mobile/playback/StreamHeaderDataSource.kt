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
        val headers = StandaloneMusicSourceHolder.instance?.onlineStreamHeaders(dataSpec.uri).orEmpty()
        return upstream.open(
            if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers),
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()
}
