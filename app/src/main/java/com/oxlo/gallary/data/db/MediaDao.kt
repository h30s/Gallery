package com.oxlo.gallary.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<MediaItemEntity>): List<Long>

    @Update
    suspend fun update(item: MediaItemEntity)

    @Query("SELECT * FROM media_items ORDER BY dateTaken DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: Long): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MediaItemEntity>

    @Query("SELECT uri FROM media_items WHERE sourceId IS NULL")
    suspend fun allLocalUris(): List<String>

    @Query("SELECT COUNT(*) FROM media_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE sourceId IS NULL")
    suspend fun localCount(): Int

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM media_items WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("DELETE FROM media_items WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("UPDATE media_items SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int)

    @Query("UPDATE media_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE media_items SET faceCount = :count WHERE id = :id")
    suspend fun setFaceCount(id: Long, count: Int)

    @Query("UPDATE media_items SET faceCount = (SELECT COUNT(*) FROM faces WHERE faces.mediaId = media_items.id) WHERE id = :id")
    suspend fun refreshFaceCount(id: Long)

    @Query(
        """
        SELECT bucketName, COUNT(*) AS itemCount, uri AS coverUri, MAX(dateTaken) AS latest
        FROM media_items
        WHERE bucketName IS NOT NULL AND bucketName != ''
        GROUP BY bucketName
        ORDER BY MAX(dateTaken) DESC
        """
    )
    fun observeAlbums(): Flow<List<AlbumSummary>>

    @Query("SELECT DISTINCT mimeType FROM media_items")
    suspend fun distinctMimeTypes(): List<String>

    @Query("SELECT * FROM media_items WHERE sourceId IS NULL AND id NOT IN (SELECT mediaId FROM faces) LIMIT :limit")
    suspend fun localItemsWithoutFaces(limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id NOT IN (SELECT mediaId FROM faces) AND isVideo = 0 LIMIT :limit")
    suspend fun itemsWithoutFaces(limit: Int): List<MediaItemEntity>

    @RawQuery(observedEntities = [MediaItemEntity::class, MediaTagCrossRef::class, TagEntity::class, FaceEntity::class, PersonEntity::class])
    fun search(query: SupportSQLiteQuery): Flow<List<MediaItemEntity>>

    @Transaction
    suspend fun upsertScanned(scanned: List<MediaItemEntity>): Int {
        val before = localCount()
        insertIgnore(scanned)
        return localCount() - before
    }
}
