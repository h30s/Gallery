package com.oxlo.gallary.data.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.oxlo.gallary.data.db.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Thin wrapper over the Cast SDK: connects to whatever receiver the user picks
 * through the standard cast button and loads the current photo/video.
 */
class CastManager(
    private val context: Context,
    private val server: LocalMediaServer
) {

    private val castContext: CastContext?
        get() = runCatching { CastContext.getSharedInstance(context) }.getOrNull()

    val isCastAvailable: Boolean
        get() = castContext != null

    fun isConnected(): Boolean = runCatching {
        castContext?.sessionManager?.currentCastSession?.isConnected == true
    }.getOrDefault(false)

    fun deviceName(): String? = runCatching {
        castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName
    }.getOrNull()

    suspend fun cast(item: MediaItemEntity): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = castContext?.sessionManager?.currentCastSession
                ?: throw IOException("No Chromecast connected — tap the cast icon first")
            val client = session.remoteMediaClient
                ?: throw IOException("Cast session is not ready yet")

            val url = server.urlFor(item)
                ?: throw IOException("Could not start the local media server on this network")

            val metadata = MediaMetadata(
                if (item.isVideo) MediaMetadata.MEDIA_TYPE_MOVIE else MediaMetadata.MEDIA_TYPE_PHOTO
            ).apply {
                putString(MediaMetadata.KEY_TITLE, item.displayName)
            }

            val info = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(item.mimeType)
                .setMetadata(metadata)
                .build()

            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(true)
                .build()

            client.load(request)
            session.castDevice?.friendlyName ?: "Chromecast"
        }
    }

    fun stop() {
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
        server.clear()
    }
}
