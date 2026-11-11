package com.oxlo.gallary.data.remote

import com.oxlo.gallary.domain.RemoteEntry
import java.io.InputStream

/**
 * Common contract implemented by every remote storage backend (SMB/NAS,
 * WebDAV, Google Drive). Paths are backend specific but always start with "/"
 * except for the Drive backend, where they are folder-id chains.
 */
interface RemoteSource {

    /** Throws on failure so the UI can show the real reason. */
    suspend fun testConnection()

    suspend fun list(path: String): List<RemoteEntry>

    /** Stream of the file bytes; the caller must close it. */
    suspend fun openStream(path: String): InputStream

    /** Size in bytes, or -1 when unknown. */
    suspend fun sizeOf(path: String): Long = -1L

    fun close() = Unit
}
