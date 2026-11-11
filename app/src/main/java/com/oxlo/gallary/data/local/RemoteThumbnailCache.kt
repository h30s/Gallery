package com.oxlo.gallary.data.local

import android.content.Context
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.data.remote.RemoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coil can load a local content:// URI directly, but a remote photo has to be
 * fetched first. This keeps a bounded, self-cleaning cache of remote images so
 * the grid scrolls smoothly after the first pass.
 */
class RemoteThumbnailCache(
    context: Context,
    private val remote: RemoteRepository
) {

    private val directory = File(context.cacheDir, "thumbs").apply { mkdirs() }

    /** Returns a local file for remote images; null for videos or on failure. */
    suspend fun thumbnailFile(item: MediaItemEntity): File? = withContext(Dispatchers.IO) {
        if (!item.isRemote || item.isVideo) return@withContext null
        val file = File(directory, "thumb_${item.id}.img")
        if (file.exists() && file.length() > 0) return@withContext file
        runCatching {
            val downloaded = remote.download(item)
            downloaded.copyTo(file, overwrite = true)
            trimCache()
            file
        }.getOrNull()
    }

    /** The file the viewer / face detector should read from. */
    suspend fun localFile(item: MediaItemEntity): File? = withContext(Dispatchers.IO) {
        if (!item.isRemote) return@withContext null
        runCatching { remote.download(item) }.getOrNull()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun trimCache() {
        val files = directory.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (file in files) {
            if (total <= MAX_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    companion object {
        private const val MAX_BYTES = 250L * 1024 * 1024
    }
}
