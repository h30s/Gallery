package com.oxlo.gallary.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE tags SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Long)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: MediaTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkAll(refs: List<MediaTagCrossRef>)

    @Query("DELETE FROM media_tag_cross WHERE mediaId = :mediaId AND tagId = :tagId")
    suspend fun unlink(mediaId: Long, tagId: Long)

    @Query("DELETE FROM media_tag_cross WHERE tagId = :tagId")
    suspend fun unlinkAllForTag(tagId: Long)

    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM media_tag_cross WHERE mediaId = :mediaId) ORDER BY name COLLATE NOCASE")
    fun observeTagsForMedia(mediaId: Long): Flow<List<TagEntity>>

    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.color AS color, COUNT(c.mediaId) AS itemCount
        FROM tags t LEFT JOIN media_tag_cross c ON c.tagId = t.id
        GROUP BY t.id
        ORDER BY t.name COLLATE NOCASE
        """
    )
    fun observeTagSummaries(): Flow<List<TagSummary>>
}
