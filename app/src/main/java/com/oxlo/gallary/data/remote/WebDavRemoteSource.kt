package com.oxlo.gallary.data.remote

import com.oxlo.gallary.domain.RemoteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Generic WebDAV backend — works with Nextcloud, ownCloud, Synology, Box and
 * anything else that speaks RFC 4918 PROPFIND/GET.
 */
class WebDavRemoteSource(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val client: OkHttpClient
) : RemoteSource {

    private val basePath: String = run {
        val parsed = baseUrl.trim().toHttpUrlOrNull()
            ?: throw IOException("Invalid WebDAV URL: $baseUrl")
        parsed.encodedPath.trimEnd('/')
    }

    private val authHeader: String? =
        if (username.isNotBlank()) Credentials.basic(username, password) else null

    private fun urlFor(path: String): String {
        val clean = path.trim('/')
        val suffix = if (clean.isEmpty()) "" else "/" + clean.split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return "${baseUrl.trimEnd('/')}$suffix" + if (clean.isEmpty()) "/" else ""
    }

    private fun relativePath(href: String): String {
        val decoded = URLDecoder.decode(href.substringBefore('?'), "UTF-8")
        val withoutBase = when {
            decoded.startsWith(basePath) -> decoded.removePrefix(basePath)
            else -> {
                val marker = basePath.substringAfterLast('/')
                val idx = decoded.indexOf("/$marker/")
                if (idx >= 0) decoded.substring(idx + marker.length + 1) else decoded
            }
        }
        return withoutBase.trim('/').let { "/$it" }
    }

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        val response = client.newCall(propfindRequest("")).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("WebDAV responded with ${it.code} ${it.message}")
        }
    }

    private fun propfindRequest(path: String): Request {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:getcontenttype/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())
        return Request.Builder()
            .url(urlFor(path))
            .method("PROPFIND", body)
            .header("Depth", "1")
            .apply { authHeader?.let { header("Authorization", it) } }
            .build()
    }

    override suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val response = client.newCall(propfindRequest(path)).execute()
        val bytes = response.use {
            if (!it.isSuccessful) throw IOException("WebDAV responded with ${it.code} ${it.message}")
            it.body?.bytes() ?: ByteArray(0)
        }
        val self = path.trim('/')
        parseMultiStatus(bytes)
            .filter { it.path.trim('/').isNotEmpty() && it.path.trim('/') != self }
            .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun parseMultiStatus(bytes: ByteArray): List<RemoteEntry> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), null)

        val result = mutableListOf<RemoteEntry>()
        var href: String? = null
        var length = 0L
        var modified = 0L
        var mime: String? = null
        var isDirectory = false
        var inResponse = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':').lowercase(Locale.US)) {
                    "response" -> {
                        inResponse = true
                        href = null
                        length = 0
                        modified = 0
                        mime = null
                        isDirectory = false
                    }
                    "href" -> if (inResponse) href = parser.nextText().trim()
                    "getcontentlength" -> if (inResponse) {
                        length = parser.nextText().trim().toLongOrNull() ?: 0L
                    }
                    "getlastmodified" -> if (inResponse) {
                        modified = parseHttpDate(parser.nextText().trim())
                    }
                    "getcontenttype" -> if (inResponse) mime = parser.nextText().trim()
                    "collection" -> if (inResponse) isDirectory = true
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (parser.name.substringAfter(':').equals("response", ignoreCase = true)) {
                    inResponse = false
                    val rawHref = href
                    if (!rawHref.isNullOrBlank()) {
                        val entryPath = relativePath(rawHref)
                        val name = URLDecoder.decode(
                            entryPath.trimEnd('/').substringAfterLast('/'),
                            "UTF-8"
                        )
                        if (name.isNotEmpty()) {
                            result += RemoteEntry(
                                path = entryPath,
                                name = name,
                                isDirectory = isDirectory || rawHref.endsWith("/"),
                                size = length,
                                lastModified = modified,
                                mimeType = mime
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseHttpDate(value: String): Long = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time ?: 0L
    }.getOrDefault(0L)

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlFor(path))
            .apply { authHeader?.let { header("Authorization", it) } }
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("WebDAV download failed with ${response.code}")
        }
        ResponseStream(response)
    }

    override suspend fun sizeOf(path: String): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlFor(path))
            .head()
            .apply { authHeader?.let { header("Authorization", it) } }
            .build()
        runCatching {
            client.newCall(request).execute().use { it.header("Content-Length")?.toLongOrNull() ?: -1L }
        }.getOrDefault(-1L)
    }

    private class ResponseStream(private val response: Response) : InputStream() {
        private val delegate = response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

        override fun close() {
            runCatching { delegate.close() }
            response.close()
        }
    }
}
