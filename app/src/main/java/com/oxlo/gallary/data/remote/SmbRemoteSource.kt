package com.oxlo.gallary.data.remote

import com.oxlo.gallary.domain.RemoteEntry
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.Properties

/**
 * SMB2/SMB3 access to a NAS share via jcifs-ng. All calls are pushed to the IO
 * dispatcher because jcifs is blocking.
 */
class SmbRemoteSource(
    private val host: String,
    private val share: String,
    private val username: String,
    private val password: String,
    private val domain: String = "",
    private val port: Int = 445
) : RemoteSource {

    private val baseUrl: String = buildString {
        append("smb://")
        append(host.trim().trimEnd('/'))
        if (port > 0 && port != 445) {
            append(':').append(port)
        }
        append('/').append(share.trim().trim('/'))
    }

    private val context: CIFSContext by lazy {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.disableSMB1", "false")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
            setProperty("jcifs.smb.client.connTimeout", "15000")
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
            setProperty("jcifs.netbios.cachePolicy", "0")
            setProperty("jcifs.resolveOrder", "DNS")
        }
        val base = BaseContext(PropertyConfiguration(props))
        val authenticator = if (domain.isNotBlank()) {
            NtlmPasswordAuthenticator(domain, username, password)
        } else {
            NtlmPasswordAuthenticator(username, password)
        }
        base.withCredentials(authenticator)
    }

    private fun urlFor(path: String): String {
        val clean = path.trim('/')
        return if (clean.isEmpty()) "$baseUrl/" else "$baseUrl/$clean"
    }

    private fun pathOf(file: SmbFile): String {
        val full = file.path.substringBefore("?")
        val relative = full.removePrefix(baseUrl).trim('/')
        return "/$relative"
    }

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        val root = SmbFile(urlFor(""), context)
        if (!root.exists()) throw IOException("Share '$share' on $host is not reachable")
    }

    override suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val dir = SmbFile(urlFor(path), context)
        if (!dir.exists()) throw IOException("Folder not found: $path")
        val children = dir.listFiles() ?: emptyArray()
        children.mapNotNull { file ->
            runCatching {
                val filePath = pathOf(file)
                val isDir = file.isDirectory
                RemoteEntry(
                    path = filePath,
                    name = filePath.trimEnd('/').substringAfterLast('/'),
                    isDirectory = isDir,
                    size = if (isDir) 0 else file.length(),
                    lastModified = file.lastModified(),
                    mimeType = null
                )
            }.getOrNull()
        }
            .filter { it.name.isNotBlank() }
            .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        SmbFileInputStream(SmbFile(urlFor(path), context))
    }

    override suspend fun sizeOf(path: String): Long = withContext(Dispatchers.IO) {
        runCatching { SmbFile(urlFor(path), context).length() }.getOrDefault(-1L)
    }

    override fun close() {
        runCatching { context.close() }
    }
}
