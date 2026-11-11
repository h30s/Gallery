package com.oxlo.gallary.di

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import com.oxlo.gallary.data.MediaRepository
import com.oxlo.gallary.data.cast.CastManager
import com.oxlo.gallary.data.cast.LocalMediaServer
import com.oxlo.gallary.data.db.GallaryDatabase
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.data.face.FaceAnalyzer
import com.oxlo.gallary.data.local.MediaStoreSync
import com.oxlo.gallary.data.local.RemoteThumbnailCache
import com.oxlo.gallary.data.playback.GallaryDataSourceFactory
import com.oxlo.gallary.data.remote.RemoteRepository
import com.oxlo.gallary.data.remote.RemoteSourceFactory
import com.oxlo.gallary.data.security.CredentialStore
import com.oxlo.gallary.data.settings.SettingsRepository
import com.oxlo.gallary.ui.viewer.ViewerSession
import kotlinx.coroutines.runBlocking

/**
 * Manual dependency graph — small enough that a DI framework would only add
 * build time.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: GallaryDatabase = GallaryDatabase.get(appContext)
    val credentials = CredentialStore(appContext)
    val settings = SettingsRepository(appContext)

    private val remoteSourceFactory = RemoteSourceFactory(appContext, credentials)
    val remoteRepository = RemoteRepository(appContext, database, remoteSourceFactory, credentials)

    private val mediaStoreSync = MediaStoreSync(appContext, database)
    private val thumbnailCache = RemoteThumbnailCache(appContext, remoteRepository)

    val mediaRepository = MediaRepository(
        db = database,
        mediaStoreSync = mediaStoreSync,
        remote = remoteRepository,
        thumbnails = thumbnailCache
    )

    val faceAnalyzer = FaceAnalyzer(appContext, database, mediaRepository)

    /** Routes remote:// URIs into ExoPlayer while leaving local media untouched. */
    val dataSourceFactory: DataSource.Factory = GallaryDataSourceFactory(appContext, remoteRepository)

    val localMediaServer = LocalMediaServer { item -> streamHandle(item) }

    val castManager = CastManager(appContext, localMediaServer)

    /** The list the full-screen viewer is currently paging through. */
    val viewerSession = ViewerSession()

    /**
     * Chromecast cannot read content:// URIs, so both local and remote bytes are
     * re-served over plain HTTP from the phone.
     */
    private fun streamHandle(item: MediaItemEntity): LocalMediaServer.StreamHandle? = runCatching {
        if (item.isRemote) {
            val input = runBlocking { remoteRepository.openStream(item) }
            val size = runBlocking { remoteRepository.sizeOf(item) }
            LocalMediaServer.StreamHandle(input, size, item.mimeType)
        } else {
            val input = appContext.contentResolver.openInputStream(Uri.parse(item.uri))
                ?: return null
            LocalMediaServer.StreamHandle(input, item.size, item.mimeType)
        }
    }.getOrNull()
}
