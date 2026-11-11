package com.oxlo.gallary.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.oxlo.gallary.data.db.GallaryDatabase
import com.oxlo.gallary.data.db.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads photos and videos from MediaStore into the local index. Only metadata
 * is copied — the pixels stay where they are.
 */
class MediaStoreSync(
    private val context: Context,
    private val db: GallaryDatabase
) {

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val scanned = queryMediaStore()
        val existing = db.mediaDao().allLocalUris().toHashSet()
        val newItems = scanned.filter { it.uri !in existing }
        if (newItems.isNotEmpty()) {
            newItems.chunked(250).forEach { db.mediaDao().insertIgnore(it) }
        }

        val currentUris = scanned.mapTo(HashSet()) { it.uri }
        val removed = existing.filter { it !in currentUris }
        removed.chunked(250).forEach { chunk ->
            chunk.forEach { db.mediaDao().deleteByUri(it) }
        }

        SyncResult(added = newItems.size, removed = removed.size, total = scanned.size)
    }

    private fun queryMediaStore(): List<MediaItemEntity> {
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val items = mutableListOf<MediaItemEntity>()
        val q = context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"
        )
        q?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val mime = cursor.getString(mimeColumn) ?: continue
                val isVideo = mime.startsWith("video/")
                if (!isVideo && !mime.startsWith("image/")) continue

                val taken = cursor.getLong(takenColumn).takeIf { it > 0 }
                    ?: cursor.getLong(addedColumn) * 1000L

                items += MediaItemEntity(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    displayName = cursor.getString(nameColumn) ?: "IMG_$id",
                    mimeType = mime,
                    isVideo = isVideo,
                    size = cursor.getLong(sizeColumn),
                    dateTaken = taken,
                    durationMs = if (isVideo) cursor.getLong(durationColumn) else 0L,
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    bucketName = cursor.getString(bucketColumn) ?: "Device",
                    sourceId = null
                )
            }
        }
        return items
    }

    data class SyncResult(val added: Int, val removed: Int, val total: Int)
}
