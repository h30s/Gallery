package com.oxlo.gallary.data.db

/** Lightweight projections used by the Albums / Tags / People screens. */
data class AlbumSummary(
    val bucketName: String,
    val itemCount: Int,
    val coverUri: String,
    val latest: Long
)

data class TagSummary(
    val id: Long,
    val name: String,
    val color: Long,
    val itemCount: Int
)

data class PersonSummary(
    val id: Long,
    val name: String,
    val coverFacePath: String?,
    val faceCount: Int
)

data class CountByDay(
    val day: String,
    val itemCount: Int
)

data class SourceCount(
    val sourceId: Long,
    val itemCount: Int
)
