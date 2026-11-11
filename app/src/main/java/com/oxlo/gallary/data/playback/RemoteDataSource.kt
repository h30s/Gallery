package com.oxlo.gallary.data.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.oxlo.gallary.data.remote.RemoteRepository
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream

/**
 * Streams a remote (SMB / WebDAV / Drive) file straight into ExoPlayer, so
 * videos play without downloading the whole file first.
 */
class RemoteDataSource(private val remote: RemoteRepository) : BaseDataSource(true) {

    private var input: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val item = remoteItem(dataSpec.uri)
        val stream = try {
            runBlocking { remote.openStream(item) }
        } catch (error: Exception) {
            throw IOException("Cannot open ${dataSpec.uri}", error)
        }
        input = stream

        if (dataSpec.position > 0) {
            var skipped = 0L
            while (skipped < dataSpec.position) {
                val step = stream.skip(dataSpec.position - skipped)
                if (step <= 0) break
                skipped += step
            }
        }

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            val size = runBlocking { remote.sizeOf(item) }
            if (size > 0) (size - dataSpec.position).coerceAtLeast(0) else C.LENGTH_UNSET.toLong()
        }

        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun remoteItem(uri: Uri) = com.oxlo.gallary.data.db.MediaItemEntity(
        id = uri.getQueryParameter("id")?.toLongOrNull() ?: 0L,
        uri = uri.toString(),
        displayName = uri.lastPathSegment ?: "media",
        mimeType = "application/octet-stream",
        isVideo = true,
        sourceId = uri.host?.toLongOrNull(),
        remotePath = uri.path ?: "/"
    )

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val read = input?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { input?.close() }
        input = null
        uri = null
        transferEnded()
    }
}

/**
 * Routes remote:// URIs to [RemoteDataSource] and everything else (content://,
 * file://, http://) to the standard ExoPlayer data sources.
 */
class GallaryDataSourceFactory(
    private val context: Context,
    private val remote: RemoteRepository
) : DataSource.Factory {

    override fun createDataSource(): DataSource = GallaryDataSource(context, remote)
}

class GallaryDataSource(
    private val context: Context,
    private val remote: RemoteRepository
) : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val next: DataSource = if (dataSpec.uri.scheme == "remote") {
            RemoteDataSource(remote)
        } else {
            DefaultDataSource.Factory(context).createDataSource()
        }
        listeners.forEach { next.addTransferListener(it) }
        delegate = next
        return next.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = delegate?.uri

    override fun close() {
        runCatching { delegate?.close() }
        delegate = null
    }
}
