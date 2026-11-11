package com.oxlo.gallary.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.oxlo.gallary.data.MediaRepository
import com.oxlo.gallary.data.db.FaceEntity
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.ui.components.FaceAvatar
import com.oxlo.gallary.ui.components.InfoRow
import com.oxlo.gallary.ui.components.PersonChip
import com.oxlo.gallary.ui.components.RatingStars
import com.oxlo.gallary.ui.components.TagPill
import com.oxlo.gallary.ui.components.formatBytes
import com.oxlo.gallary.ui.components.formatDuration
import com.oxlo.gallary.ui.components.formatFullDate
import com.oxlo.gallary.ui.components.formatResolution
import com.oxlo.gallary.ui.gallery.TagPickerDialog
import com.oxlo.gallary.ui.gallery.PersonNameDialog

@Composable
fun ViewerScreen(
    items: List<MediaItemEntity>,
    startIndex: Int,
    viewModel: ViewerViewModel,
    repository: MediaRepository,
    allTags: List<com.oxlo.gallary.data.db.TagEntity>,
    onClose: () -> Unit
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()
    LaunchedEffect(currentItem.id) { viewModel.setCurrent(currentItem.id) }

    val tags by viewModel.tags.collectAsState()
    val faces by viewModel.faces.collectAsState()
    val castStatus by viewModel.castStatus.collectAsState()

    var showInfo by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var faceToName by remember { mutableStateOf<FaceEntity?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(castStatus) {
        castStatus?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeCastStatus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].id }
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPlayer(
                    uri = item.uri,
                    dataSourceFactory = viewModel.dataSourceFactory,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PhotoPage(
                    item = item,
                    repository = repository,
                    onToggleControls = { controlsVisible = !controlsVisible }
                )
            }
        }

        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentItem.displayName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} of ${items.size}",
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = { viewModel.cast(currentItem) }) {
                        Icon(Icons.Rounded.Cast, contentDescription = "Cast", tint = Color.White)
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Rounded.Info, contentDescription = "Info", tint = Color.White)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RatingStars(
                        rating = currentItem.rating,
                        onRatingChange = { viewModel.setRating(currentItem.id, it) },
                        starSize = 26.dp
                    )
                    IconButton(onClick = { viewModel.toggleFavorite(currentItem) }) {
                        Icon(
                            imageVector = if (currentItem.isFavorite) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favourite",
                            tint = if (currentItem.isFavorite) Color(0xFFFF5C8A) else Color.White
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        TagPill(
                            name = tag.name,
                            color = Color(tag.color),
                            onRemove = { viewModel.removeTag(currentItem.id, tag.id) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .pointerInput(Unit) { detectTapGestures { showTagPicker = true } }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Tag",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 3.dp)
                        )
                    }
                }

                if (faces.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        faces.take(6).forEach { face ->
                            FaceAvatar(
                                path = face.cropPath,
                                name = "Face",
                                size = 40.dp,
                                onClick = { faceToName = face }
                            )
                        }
                        Text(
                            text = if (faces.size == 1) "1 face detected · tap to tag"
                            else "${faces.size} faces detected · tap to tag",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else if (!currentItem.isVideo) {
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .pointerInput(Unit) {
                                detectTapGestures { viewModel.analyzeFaces(currentItem) }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Face,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Detect faces",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
        )
    }

    if (showTagPicker) {
        TagPickerDialog(
            existingTags = allTags,
            onDismiss = { showTagPicker = false },
            onSubmit = { name -> viewModel.addTag(listOf(currentItem.id), name) }
        )
    }

    faceToName?.let { face ->
        PersonNameDialog(
            title = "Who is this?",
            onDismiss = { faceToName = null },
            onSubmit = { name ->
                viewModel.tagFace(face.id, name)
                faceToName = null
            }
        )
    }

    if (showInfo) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = currentItem.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentItem.mimeType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                InfoRow("Date", formatFullDate(currentItem.dateTaken))
                InfoRow("Size", formatBytes(currentItem.size))
                InfoRow("Dimensions", formatResolution(currentItem.width, currentItem.height))
                if (currentItem.isVideo) {
                    InfoRow("Duration", formatDuration(currentItem.durationMs))
                }
                InfoRow("Album", currentItem.bucketName ?: "—")
                InfoRow("Storage", if (currentItem.isRemote) "Remote source" else "On this device")
                InfoRow("Rating", if (currentItem.rating == 0) "Not rated" else "${currentItem.rating} / 5")
                InfoRow("Faces", currentItem.faceCount.toString())
                if (currentItem.remotePath != null) {
                    InfoRow("Path", currentItem.remotePath)
                }
            }
        }
    }
}

@Composable
private fun PhotoPage(
    item: MediaItemEntity,
    repository: MediaRepository,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    val model by produceState<Any?>(initialValue = if (item.isRemote) null else item.uri, item.uri) {
        value = if (item.isRemote) repository.localFileFor(item) else item.uri
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (model == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            ZoomableImage(
                model = model!!,
                contentDescription = item.displayName,
                context = context,
                onTap = onToggleControls
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    model: Any,
    contentDescription: String,
    context: android.content.Context,
    onTap: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 5f)
                    scale = next
                    offset = if (next > 1f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
