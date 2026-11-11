package com.oxlo.gallary.data.remote

import com.oxlo.gallary.domain.RemoteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Google Drive REST v3 backend. Paths are folder-id chains ("/id/id"), which
 * keeps lookups O(1) and avoids re-resolving names on every request.
 */
class DriveRemoteSource(
    private val tokenProvider: suspend () -> String,
    private val client: OkHttpClient
) : RemoteSource {

    private val api = "https://www.googleapis.com/drive/v3/files"

    private suspend fun authHeader(): String = "Bearer ${tokenProvider()}"

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        val url = api.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", "1")
            .addQueryParameter("fields", "files(id)")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) {
                throw IOException("Google Drive responded with ${it.code} ${it.message}")
            }
        }
    }

    override suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val parentId = path.trim('/').substringAfterLast('/').ifBlank { "root" }
        val url = api.toHttpUrl().newBuilder()
            .addQueryParameter("q", "'$parentId' in parents and trashed = false")
            .addQueryParameter("pageSize", "1000")
            .addQueryParameter("orderBy", "folder,name")
            .addQueryParameter("fields", "files(id,name,mimeType,size,modifiedTime)")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .build()

        val body = client.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("Google Drive list failed with ${it.code}")
            it.body?.string().orEmpty()
        }

        val files = runCatching {
            JSONObject(body).optJSONArray("files")
        }.getOrNull() ?: return@withContext emptyList()

        buildList {
            for (index in 0 until files.length()) {
                val json = files.optJSONObject(index) ?: continue
                val id = json.optString("id")
                val name = json.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                val mime = json.optString("mimeType")
                val isFolder = mime == FOLDER_MIME
                add(
                    RemoteEntry(
                        path = if (path.isBlank()) "/$id" else "$path/$id",
                        name = name,
                        isDirectory = isFolder,
                        size = json.optString("size").toLongOrNull() ?: 0L,
                        lastModified = parseDriveDate(json.optString("modifiedTime")),
                        mimeType = if (isFolder) null else mime.ifBlank { null }
                    )
                )
            }
        }.sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        val fileId = path.trim('/').substringAfterLast('/')
        if (fileId.isBlank()) throw IOException("Missing Drive file id")
        val url = "$api/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Google Drive download failed with ${response.code}")
        }
        DriveStream(response)
    }

    override suspend fun sizeOf(path: String): Long = withContext(Dispatchers.IO) {
        val fileId = path.trim('/').substringAfterLast('/')
        if (fileId.isBlank()) return@withContext -1L
        val url = "$api/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "size")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .build()
        runCatching {
            client.newCall(request).execute().use {
                JSONObject(it.body?.string().orEmpty()).optString("size").toLongOrNull() ?: -1L
            }
        }.getOrDefault(-1L)
    }

    private fun parseDriveDate(value: String): Long {
        if (value.isBlank()) return 0L
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }

    private class DriveStream(private val response: Response) : InputStream() {
        private val delegate = response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

        override fun close() {
            runCatching { delegate.close() }
            response.close()
        }
    }

    companion object {
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    }
}
