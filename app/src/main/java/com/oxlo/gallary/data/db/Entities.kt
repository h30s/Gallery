package com.oxlo.gallary.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single photo or video known to the app. Local device media and remote
 * (SMB / WebDAV / Drive) media share the same table so that tags, ratings,
 * faces and search work identically across every storage backend.
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["dateTaken"]),
        Index(value = ["rating"]),
        Index(value = ["sourceId"]),
        Index(value = ["bucketName"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** content:// for local media, remote:// for remote media. */
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val size: Long = 0,
    val dateTaken: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val bucketName: String? = null,
    /** null == on this device. */
    val sourceId: Long? = null,
    /** Path inside the remote source, e.g. /Photos/2024/img.jpg */
    val remotePath: String? = null,
    val rating: Int = 0,
    val isFavorite: Boolean = false,
    val faceCount: Int = 0,
    val indexedAt: Long = System.currentTimeMillis()
) {
    val isRemote: Boolean get() = sourceId != null
    val isPhoto: Boolean get() = !isVideo
}

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFF7C5CFF,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "media_tag_cross",
    primaryKeys = ["mediaId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class MediaTagCrossRef(
    val mediaId: Long,
    val tagId: Long
)

@Entity(
    tableName = "people",
    indices = [Index(value = ["name"], unique = true)]
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverFacePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "faces",
    indices = [Index("mediaId"), Index("personId")],
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val personId: Long? = null,
    /** Normalised bounding box (0..1) inside the upright image. */
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val smileProbability: Float = 0f,
    val eyesOpenProbability: Float = 0f,
    /** Absolute path of the cached face crop shown in the People screen. */
    val cropPath: String? = null,
    /** Coarse appearance descriptor used for "group similar faces" suggestions. */
    val descriptor: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sources",
    indices = [Index(value = ["name"], unique = true)]
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** SMB | WEBDAV | DRIVE */
    val type: String,
    val host: String = "",
    val share: String = "",
    val basePath: String = "",
    val username: String = "",
    val domain: String = "",
    val useHttps: Boolean = true,
    val port: Int = 0,
    val lastSyncAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
