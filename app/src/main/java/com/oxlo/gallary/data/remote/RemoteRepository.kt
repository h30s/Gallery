package com.oxlo.gallary.data.remote

import android.content.Context
import com.oxlo.gallary.data.db.GallaryDatabase
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.data.db.SourceEntity
import com.oxlo.gallary.data.security.CredentialStore
import com.oxlo.gallary.domain.MediaKind
import com.oxlo.gallary.domain.RemoteEntry
import com.oxlo.gallary.domain.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Creates and caches one [RemoteSource] per configured storage backend. */
class RemoteSourceFactory(
    private val context: Context,
    private val credentials: CredentialStore
) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun create(source: SourceEntity): RemoteSource = create(source, null)

    /** [passwordOverride] lets the UI test a draft before it is saved. */
    fun create(source: SourceEntity, passwordOverride: String?): RemoteSource = when (SourceType.from(source.type)) {
        SourceType.SMB -> SmbRemoteSource(
            host = source.host,
            share = source.share,
            username = source.username,
            password = passwordOverride ?: credentials.password(source.id).orEmpty(),
            domain = source.domain,
            port = source.port
        )

        SourceType.WEBDAV -> WebDavRemoteSource(
            baseUrl = webDavUrl(source),
            username = source.username,
            password = passwordOverride ?: credentials.password(source.id).orEmpty(),
            client = httpClient
        )

        SourceType.DRIVE -> DriveRemoteSource(
            tokenProvider = { DriveAuth.accessToken(context) },
            client = httpClient
        )
    }

    private fun webDavUrl(source: SourceEntity): String {
        val host = source.host.trim()
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host.trimEnd('/')
        }
        val scheme = if (source.useHttps) "https" else "http"
        val portPart = if (source.port > 0) ":${source.port}" else ""
        val path = source.basePath.trim().trim('/')
        val suffix = if (path.isEmpty()) "" else "/$path"
        return "$scheme://${host.trimEnd('/')}$portPart$suffix"
    }
}

/**
 * Importing and streaming for every remote backend. Imported files become
 * normal rows in the media table, so tags, ratings, faces and search treat
 * them exactly like local photos.
 */
class RemoteRepository(
    private val context: Context,
    private val db: GallaryDatabase,
    private val factory: RemoteSourceFactory,
    private val credentials: CredentialStore
) {

    private val mutex = Mutex()
    private val sources = mutableMapOf<Long, RemoteSource>()

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress

    data class ImportProgress(val sourceName: String, val found: Int, val folder: String)

    private suspend fun sourceFor(sourceId: Long): RemoteSource = mutex.withLock {
        sources[sourceId] ?: run {
            val entity = db.sourceDao().getById(sourceId)
                ?: throw IOException("Source $sourceId no longer exists")
            factory.create(entity).also { sources[sourceId] = it }
        }
    }

    suspend fun test(source: SourceEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { factory.create(source).also { it.testConnection() }.close() }
    }

    /** Validates a not-yet-saved source with the password the user just typed. */
    suspend fun testDraft(source: SourceEntity, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val remote = factory.create(source, password)
                try {
                    remote.testConnection()
                } finally {
                    remote.close()
                }
            }
        }

    suspend fun browse(sourceId: Long, path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        sourceFor(sourceId).list(path)
    }

    /**
     * Walks [folderPath] and registers every photo/video it finds. Returns the
     * number of newly imported items.
     */
    suspend fun importFolder(
        sourceId: Long,
        folderPath: String,
        recursive: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        val entity = db.sourceDao().getById(sourceId) ?: throw IOException("Unknown source")
        val remote = sourceFor(sourceId)
        val queue = ArrayDeque<String>().apply { add(folderPath) }
        val rows = mutableListOf<MediaItemEntity>()
        var imported = 0
        var visitedFolders = 0

        while (queue.isNotEmpty() && imported < MAX_IMPORT_ITEMS && visitedFolders < MAX_FOLDERS) {
            val current = queue.removeFirst()
            visitedFolders++
            _importProgress.value = ImportProgress(entity.name, imported, current)

            val entries = runCatching { remote.list(current) }.getOrElse { emptyList() }
            for (entry in entries) {
                if (entry.isDirectory) {
                    if (recursive) queue.add(entry.path)
                    continue
                }
                if (!entry.isMedia) continue
                val mime = entry.mimeType ?: guessMime(entry.name)
                rows += MediaItemEntity(
                    uri = remoteUri(sourceId, entry.path),
                    displayName = entry.name,
                    mimeType = mime,
                    isVideo = mime.startsWith("video/"),
                    size = entry.size,
                    dateTaken = if (entry.lastModified > 0) entry.lastModified else System.currentTimeMillis(),
                    bucketName = entity.name,
                    sourceId = sourceId,
                    remotePath = entry.path
                )
                imported++
                if (imported >= MAX_IMPORT_ITEMS) break
            }
        }

        if (rows.isNotEmpty()) {
            rows.chunked(200).forEach { db.mediaDao().insertIgnore(it) }
        }
        db.sourceDao().markSynced(sourceId, System.currentTimeMillis())
        _importProgress.value = null
        imported
    }

    suspend fun openStream(item: MediaItemEntity): InputStream = withContext(Dispatchers.IO) {
        val sourceId = item.sourceId ?: throw IOException("Not a remote item")
        val path = item.remotePath ?: throw IOException("Missing remote path")
        sourceFor(sourceId).openStream(path)
    }

    suspend fun sizeOf(item: MediaItemEntity): Long = withContext(Dispatchers.IO) {
        val sourceId = item.sourceId ?: return@withContext -1L
        val path = item.remotePath ?: return@withContext -1L
        runCatching { sourceFor(sourceId).sizeOf(path) }.getOrDefault(-1L)
    }

    /** Downloads a remote item into the app cache (used for faces + thumbnails). */
    suspend fun download(item: MediaItemEntity): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "remote").apply { mkdirs() }
        val target = File(cacheDir, "item_${item.id}_${item.displayName.hashCode()}.bin")
        if (target.exists() && target.length() > 0) return@withContext target
        openStream(item).use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        target
    }

    suspend fun removeSource(sourceId: Long) {
        mutex.withLock {
            sources.remove(sourceId)?.close()
        }
        credentials.clear(sourceId)
        db.mediaDao().deleteBySource(sourceId)
        db.sourceDao().delete(sourceId)
    }

    suspend fun invalidate(sourceId: Long) {
        val removed = mutex.withLock { sources.remove(sourceId) }
        removed?.close()
    }

    fun remoteUri(sourceId: Long, path: String) = "remote://$sourceId$path"

    companion object {
        private const val MAX_IMPORT_ITEMS = 5000
        private const val MAX_FOLDERS = 400

        fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "avif" -> "image/avif"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "ts" -> "video/mp2t"
            else -> "application/octet-stream"
        }

        fun kindOf(mime: String): MediaKind =
            if (mime.startsWith("video/")) MediaKind.VIDEOS else MediaKind.PHOTOS
    }
}
