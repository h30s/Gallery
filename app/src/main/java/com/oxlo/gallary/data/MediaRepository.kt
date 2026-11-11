package com.oxlo.gallary.data

import com.oxlo.gallary.data.db.AlbumSummary
import com.oxlo.gallary.data.db.FaceEntity
import com.oxlo.gallary.data.db.GallaryDatabase
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.data.db.PersonSummary
import com.oxlo.gallary.data.db.TagEntity
import com.oxlo.gallary.data.db.TagSummary
import com.oxlo.gallary.data.local.MediaStoreSync
import com.oxlo.gallary.data.local.RemoteThumbnailCache
import com.oxlo.gallary.data.remote.RemoteRepository
import com.oxlo.gallary.domain.MediaFilter
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Single entry point the UI uses for everything media related: queries,
 * ratings, favourites, tags, faces and thumbnail resolution.
 */
class MediaRepository(
    private val db: GallaryDatabase,
    private val mediaStoreSync: MediaStoreSync,
    private val remote: RemoteRepository,
    private val thumbnails: RemoteThumbnailCache
) {

    fun search(filter: MediaFilter): Flow<List<MediaItemEntity>> = db.mediaDao().search(filter.toSql())

    fun observeById(id: Long): Flow<MediaItemEntity?> = db.mediaDao().observeById(id)

    fun observeAlbums(): Flow<List<AlbumSummary>> = db.mediaDao().observeAlbums()

    fun observeTags(): Flow<List<TagSummary>> = db.tagDao().observeTagSummaries()

    fun observeRawTags(): Flow<List<TagEntity>> = db.tagDao().observeAll()

    fun observeTagsForMedia(mediaId: Long): Flow<List<TagEntity>> = db.tagDao().observeTagsForMedia(mediaId)

    fun observePeople(): Flow<List<PersonSummary>> = db.peopleDao().observePeople()

    fun observeUnassignedFaces(limit: Int = 400): Flow<List<FaceEntity>> =
        db.peopleDao().observeUnassignedFaces(limit)

    fun observeFacesForMedia(mediaId: Long): Flow<List<FaceEntity>> =
        db.peopleDao().observeFacesForMedia(mediaId)

    fun observeFacesForPerson(personId: Long): Flow<List<FaceEntity>> =
        db.peopleDao().observeFacesForPerson(personId)

    suspend fun count(): Int = db.mediaDao().localCount()

    suspend fun refreshLocalMedia(): MediaStoreSync.SyncResult = mediaStoreSync.sync()

    suspend fun setRating(id: Long, rating: Int) {
        db.mediaDao().setRating(id, rating.coerceIn(0, 5))
    }

    suspend fun toggleFavorite(item: MediaItemEntity): Boolean {
        val next = !item.isFavorite
        db.mediaDao().setFavorite(item.id, next)
        return next
    }

    suspend fun setFavorite(ids: List<Long>, favorite: Boolean) {
        ids.forEach { db.mediaDao().setFavorite(it, favorite) }
    }

    suspend fun setRatingFor(ids: List<Long>, rating: Int) {
        ids.forEach { db.mediaDao().setRating(it, rating.coerceIn(0, 5)) }
    }

    suspend fun deleteFromLibrary(ids: List<Long>) {
        db.mediaDao().deleteByIds(ids)
    }

    // ---------------------------------------------------------------- tags

    suspend fun createTag(name: String, color: Long = DEFAULT_TAG_COLOR): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1L
        val existing = db.tagDao().findByName(clean)
        if (existing != null) return existing.id
        return db.tagDao().insert(TagEntity(name = clean, color = color))
    }

    suspend fun renameTag(id: Long, name: String) {
        if (name.isNotBlank()) db.tagDao().rename(id, name.trim())
    }

    suspend fun deleteTag(id: Long) {
        db.tagDao().unlinkAllForTag(id)
        db.tagDao().delete(id)
    }

    suspend fun tagMedia(mediaIds: List<Long>, tagName: String): Long {
        val tagId = createTag(tagName)
        if (tagId <= 0) return -1
        db.tagDao().linkAll(mediaIds.map { com.oxlo.gallary.data.db.MediaTagCrossRef(it, tagId) })
        return tagId
    }

    suspend fun attachTag(mediaIds: List<Long>, tagId: Long) {
        db.tagDao().linkAll(mediaIds.map { com.oxlo.gallary.data.db.MediaTagCrossRef(it, tagId) })
    }

    suspend fun detachTag(mediaId: Long, tagId: Long) = db.tagDao().unlink(mediaId, tagId)

    // -------------------------------------------------------------- people

    suspend fun createPerson(name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1L
        db.peopleDao().findByName(clean)?.let { return it.id }
        return db.peopleDao().insertPerson(com.oxlo.gallary.data.db.PersonEntity(name = clean))
    }

    suspend fun tagFacesWithPerson(faceIds: List<Long>, personName: String): Long {
        val personId = createPerson(personName)
        if (personId <= 0) return -1
        assignFaces(faceIds, personId)
        db.peopleDao().facesByIds(faceIds)
            .firstOrNull { !it.cropPath.isNullOrBlank() }
            ?.cropPath
            ?.let { db.peopleDao().setCoverIfEmpty(personId, it) }
        return personId
    }

    suspend fun assignFaces(faceIds: List<Long>, personId: Long?) {
        if (faceIds.isEmpty()) return
        val mediaIds = db.peopleDao().facesByIds(faceIds).map { it.mediaId }.distinct()
        db.peopleDao().assignFaces(faceIds, personId)
        mediaIds.forEach { db.mediaDao().refreshFaceCount(it) }
    }

    suspend fun renamePerson(id: Long, name: String) {
        if (name.isNotBlank()) db.peopleDao().renamePerson(id, name.trim())
    }

    suspend fun deletePerson(id: Long) {
        db.peopleDao().clearPerson(id)
        db.peopleDao().deletePerson(id)
    }

    // ---------------------------------------------------------- thumbnails

    /**
     * Model handed to Coil: the content URI for local media, a cached file for
     * remote photos, null when no thumbnail can be produced.
     */
    suspend fun thumbnailModel(item: MediaItemEntity): Any? = when {
        !item.isRemote -> item.uri
        item.isVideo -> null
        else -> thumbnails.thumbnailFile(item)
    }

    suspend fun localFileFor(item: MediaItemEntity): File? = thumbnails.localFile(item)

    companion object {
        const val DEFAULT_TAG_COLOR = 0xFF7C5CFF
    }
}
