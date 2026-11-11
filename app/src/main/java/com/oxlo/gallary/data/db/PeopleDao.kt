package com.oxlo.gallary.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PeopleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFace(face: FaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFaces(faces: List<FaceEntity>): List<Long>

    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    suspend fun allPeople(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): PersonEntity?

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.coverFacePath AS coverFacePath,
               COUNT(f.id) AS faceCount
        FROM people p LEFT JOIN faces f ON f.personId = p.id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE
        """
    )
    fun observePeople(): Flow<List<PersonSummary>>

    @Query("SELECT * FROM faces WHERE personId IS NULL AND cropPath IS NOT NULL ORDER BY createdAt DESC LIMIT :limit")
    fun observeUnassignedFaces(limit: Int): Flow<List<FaceEntity>>

    @Query("SELECT * FROM faces WHERE personId = :personId ORDER BY createdAt DESC")
    fun observeFacesForPerson(personId: Long): Flow<List<FaceEntity>>

    @Query("SELECT * FROM faces WHERE mediaId = :mediaId")
    fun observeFacesForMedia(mediaId: Long): Flow<List<FaceEntity>>

    @Query("SELECT * FROM faces WHERE mediaId = :mediaId")
    suspend fun facesForMedia(mediaId: Long): List<FaceEntity>

    @Query("SELECT * FROM faces WHERE id = :id")
    suspend fun faceById(id: Long): FaceEntity?

    @Query("SELECT * FROM faces WHERE id IN (:ids)")
    suspend fun facesByIds(ids: List<Long>): List<FaceEntity>

    @Query("UPDATE faces SET personId = NULL WHERE personId = :personId")
    suspend fun clearPerson(personId: Long)

    @Query("SELECT * FROM faces WHERE mediaId = :mediaId AND personId IS NULL")
    suspend fun unassignedFacesForMedia(mediaId: Long): List<FaceEntity>

    @Query("SELECT COUNT(*) FROM faces")
    suspend fun faceCount(): Int

    @Query("SELECT COUNT(*) FROM faces WHERE mediaId = :mediaId")
    suspend fun countForMedia(mediaId: Long): Int

    @Query("UPDATE faces SET personId = :personId WHERE id IN (:faceIds)")
    suspend fun assignFaces(faceIds: List<Long>, personId: Long?)

    @Query("UPDATE faces SET personId = :personId WHERE mediaId = :mediaId")
    suspend fun assignAllFacesInMedia(mediaId: Long, personId: Long)

    @Query("UPDATE people SET coverFacePath = :path WHERE id = :personId AND (coverFacePath IS NULL OR coverFacePath = '')")
    suspend fun setCoverIfEmpty(personId: Long, path: String)

    @Query("UPDATE people SET name = :name WHERE id = :id")
    suspend fun renamePerson(id: Long, name: String)

    @Query("UPDATE people SET coverFacePath = :path WHERE id = :id")
    suspend fun setCover(id: Long, path: String?)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Query("DELETE FROM faces WHERE mediaId = :mediaId")
    suspend fun deleteFacesForMedia(mediaId: Long)

    @Query("SELECT DISTINCT mediaId FROM faces WHERE personId = :personId")
    fun observeMediaIdsForPerson(personId: Long): Flow<List<Long>>
}
