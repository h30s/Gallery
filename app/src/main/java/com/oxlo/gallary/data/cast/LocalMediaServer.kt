package com.oxlo.gallary.data.cast

import com.oxlo.gallary.data.db.MediaItemEntity
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/**
 * A tiny HTTP server bound to the phone's LAN address. A Chromecast cannot read
 * content:// URIs, so local files (and remote files) are re-served over HTTP for
 * the duration of the cast session.
 */
class LocalMediaServer(
    private val streamProvider: (MediaItemEntity) -> StreamHandle?
) : NanoHTTPD(0) {

    data class StreamHandle(val input: InputStream, val length: Long, val mimeType: String)

    private val registry = mutableMapOf<String, MediaItemEntity>()
    private val lock = Any()

    fun ensureStarted() {
        if (isAlive) return
        runCatching { start(HTTP_READ_TIMEOUT_MS, false) }
    }

    fun urlFor(item: MediaItemEntity): String? {
        ensureStarted()
        if (!isAlive) return null
        val host = lanAddress() ?: return null
        val token = UUID.randomUUID().toString().replace("-", "")
        synchronized(lock) { registry[token] = item }
        return "http://$host:$listeningPort/media/$token"
    }

    fun clear() {
        synchronized(lock) { registry.clear() }
    }

    override fun serve(session: IHTTPSession): Response {
        val token = session.uri.trim('/').substringAfterLast('/')
        val item = synchronized(lock) { registry[token] }
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Unknown media")

        val handle = streamProvider(item)
            ?: return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Media unavailable")

        val total = handle.length
        if (total <= 0) {
            // Unknown size (some MediaStore rows report 0) — stream it instead.
            return newChunkedResponse(Response.Status.OK, handle.mimeType, handle.input)
        }
        val rangeHeader = session.headers["range"]
        var start = 0L
        var end = if (total > 0) total - 1 else 0L
        var status = Response.Status.OK

        if (!rangeHeader.isNullOrBlank() && total > 0) {
            val parts = rangeHeader.removePrefix("bytes=").split('-')
            start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            end = parts.getOrNull(1)?.toLongOrNull() ?: (total - 1)
            if (start < 0) start = 0
            if (end >= total) end = total - 1
            if (start > end) start = 0
            status = Response.Status.PARTIAL_CONTENT
        }

        if (start > 0) {
            runCatching { handle.input.skip(start) }
        }

        val length = if (total > 0) end - start + 1 else 0L
        val response = newFixedLengthResponse(status, handle.mimeType, handle.input, length)
        response.addHeader("Accept-Ranges", "bytes")
        if (status == Response.Status.PARTIAL_CONTENT) {
            response.addHeader("Content-Range", "bytes $start-$end/$total")
        }
        return response
    }

    private fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses?.toList().orEmpty().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    companion object {
        private const val HTTP_READ_TIMEOUT_MS = 60_000
    }
}
