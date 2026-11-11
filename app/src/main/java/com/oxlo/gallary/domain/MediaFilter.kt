package com.oxlo.gallary.domain

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

enum class MediaKind { ALL, PHOTOS, VIDEOS }

enum class MediaScope { ALL, LOCAL, REMOTE }

enum class SortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME("Name A–Z"),
    RATING("Highest rated"),
    LARGEST("Largest file")
}

/**
 * Every screen that shows media (library, albums, tags, people, search) feeds a
 * filter into [MediaRepository.search], which turns it into one SQL query.
 */
data class MediaFilter(
    val query: String = "",
    val kind: MediaKind = MediaKind.ALL,
    val scope: MediaScope = MediaScope.ALL,
    val minRating: Int = 0,
    val onlyFavorites: Boolean = false,
    val tagIds: Set<Long> = emptySet(),
    val personIds: Set<Long> = emptySet(),
    val bucketName: String? = null,
    val sourceId: Long? = null,
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val sort: SortOrder = SortOrder.NEWEST
) {
    val isActive: Boolean
        get() = query.isNotBlank() || kind != MediaKind.ALL || scope != MediaScope.ALL ||
            minRating > 0 || onlyFavorites || tagIds.isNotEmpty() || personIds.isNotEmpty() ||
            bucketName != null || sourceId != null || fromDate != null || toDate != null

    fun withQuery(value: String) = copy(query = value)

    /** Builds the query used by [com.oxlo.gallary.data.db.MediaDao.search]. */
    fun toSql(): SupportSQLiteQuery {
        val args = mutableListOf<Any?>()
        val where = mutableListOf<String>()

        if (query.isNotBlank()) {
            val like = "%${query.trim()}%"
            where += """
                (m.displayName LIKE ? OR m.bucketName LIKE ?
                 OR EXISTS (SELECT 1 FROM media_tag_cross c JOIN tags t ON t.id = c.tagId
                            WHERE c.mediaId = m.id AND t.name LIKE ?)
                 OR EXISTS (SELECT 1 FROM faces f JOIN people p ON p.id = f.personId
                            WHERE f.mediaId = m.id AND p.name LIKE ?))
            """.trimIndent()
            repeat(4) { args += like }
        }

        when (kind) {
            MediaKind.PHOTOS -> where += "m.isVideo = 0"
            MediaKind.VIDEOS -> where += "m.isVideo = 1"
            MediaKind.ALL -> Unit
        }

        when (scope) {
            MediaScope.LOCAL -> where += "m.sourceId IS NULL"
            MediaScope.REMOTE -> where += "m.sourceId IS NOT NULL"
            MediaScope.ALL -> Unit
        }

        if (minRating > 0) {
            where += "m.rating >= ?"
            args += minRating
        }
        if (onlyFavorites) where += "m.isFavorite = 1"
        if (bucketName != null) {
            where += "m.bucketName = ?"
            args += bucketName
        }
        if (sourceId != null) {
            where += "m.sourceId = ?"
            args += sourceId
        }
        if (fromDate != null) {
            where += "m.dateTaken >= ?"
            args += fromDate
        }
        if (toDate != null) {
            where += "m.dateTaken <= ?"
            args += toDate
        }
        if (tagIds.isNotEmpty()) {
            where += "EXISTS (SELECT 1 FROM media_tag_cross c WHERE c.mediaId = m.id AND c.tagId IN (${tagIds.joinToString(",") { "?" }}))"
            args.addAll(tagIds.toList())
        }
        if (personIds.isNotEmpty()) {
            where += "EXISTS (SELECT 1 FROM faces f WHERE f.mediaId = m.id AND f.personId IN (${personIds.joinToString(",") { "?" }}))"
            args.addAll(personIds.toList())
        }

        val order = when (sort) {
            SortOrder.NEWEST -> "m.dateTaken DESC"
            SortOrder.OLDEST -> "m.dateTaken ASC"
            SortOrder.NAME -> "m.displayName COLLATE NOCASE ASC"
            SortOrder.RATING -> "m.rating DESC, m.dateTaken DESC"
            SortOrder.LARGEST -> "m.size DESC"
        }

        val sql = buildString {
            append("SELECT m.* FROM media_items m")
            if (where.isNotEmpty()) {
                append(" WHERE ")
                append(where.joinToString(" AND "))
            }
            append(" ORDER BY ")
            append(order)
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
