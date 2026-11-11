package com.oxlo.gallary.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * The standard Cast icon. It renders the device picker dialog for us, so all we
 * have to do is hand the session to [com.oxlo.gallary.data.cast.CastManager].
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.size(40.dp),
        factory = { ctx ->
            MediaRouteButton(ctx).apply {
                runCatching { CastButtonFactory.setUpMediaRouteButton(ctx, this) }
                contentDescription = "Cast to device"
            }
        }
    )
}
