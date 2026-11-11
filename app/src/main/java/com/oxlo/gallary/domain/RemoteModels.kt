package com.oxlo.gallary.domain

/** A folder or file entry returned by a remote storage backend. */
data class RemoteEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val mimeType: String? = null
) {
    val isVideo: Boolean
        get() = mimeType?.startsWith("video/") == true || VIDEO_EXTENSIONS.any {
            name.endsWith(it, ignoreCase = true)
        }

    val isMedia: Boolean
        get() = mimeType?.startsWith("image/") == true || isVideo ||
            IMAGE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    companion object {
        val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif")
        val VIDEO_EXTENSIONS = listOf("mp4", "mkv", "mov", "webm", "avi", "m4v", "3gp", "ts")
    }
}

enum class SourceType(val label: String, val description: String) {
    SMB("SMB / NAS", "Windows shares, Synology, TrueNAS, routers"),
    WEBDAV("WebDAV", "Nextcloud, ownCloud, Synology WebDAV, Box"),
    DRIVE("Google Drive", "Photos and videos stored in your Drive");

    companion object {
        fun from(value: String): SourceType = entries.firstOrNull { it.name == value } ?: WEBDAV
    }
}

/** Where the bytes of a media item can be read from, right now. */
sealed interface MediaSource {
    data class Local(val uri: String) : MediaSource
    data class Remote(val sourceId: Long, val path: String) : MediaSource
}
