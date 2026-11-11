package com.oxlo.gallary.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.domain.MediaKind
import com.oxlo.gallary.ui.components.CastButton
import com.oxlo.gallary.ui.components.EmptyState
import com.oxlo.gallary.ui.components.MediaGrid
import com.oxlo.gallary.ui.components.formatCount
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    tags: List<com.oxlo.gallary.data.db.TagEntity>,
    onOpenViewer: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.media.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val message by viewModel.message.collectAsState()
    val faceProgress by viewModel.faceScanProgress.collectAsState()

    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val selectionMode = selection.isNotEmpty()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                if (selectionMode) {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = { selection = emptySet() },
                        onFavorite = {
                            viewModel.favoriteSelection(selection.toList())
                            selection = emptySet()
                        },
                        onTag = { showTagDialog = true },
                        onRate = { showRatingDialog = true },
                        onDelete = {
                            viewModel.removeFromLibrary(selection.toList())
                            selection = emptySet()
                        }
                    )
                } else {
                    LibraryTopBar(
                        subtitle = formatCount(items.size, "item"),
                        syncing = syncing,
                        onSearch = onOpenSearch,
                        onSettings = onOpenSettings,
                        onRefresh = { viewModel.refresh() }
                    )
                }

                AnimatedVisibility(visible = faceProgress != null) {
                    Column {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                        Text(
                            text = faceProgress.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                if (!selectionMode) {
                    FilterRow(
                        kind = filter.kind,
                        favoritesOnly = filter.onlyFavorites,
                        minRating = filter.minRating,
                        onKind = viewModel::setKind,
                        onFavorites = viewModel::toggleFavoritesOnly,
                        onRating = viewModel::setMinRating
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = if (filter.isActive) "Nothing matches" else "Your gallery is empty",
                    subtitle = if (filter.isActive) {
                        "Try clearing the filters or searching for something else."
                    } else {
                        "Grant photo access to see your device gallery, or connect a NAS or cloud drive."
                    },
                    actionLabel = if (filter.isActive) "Clear filters" else "Connect a source",
                    onAction = {
                        if (filter.isActive) viewModel.clearFilters() else onOpenSources()
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                MediaGrid(
                    items = items,
                    repository = com.oxlo.gallary.ui.rememberContainer().mediaRepository,
                    columns = settings.gridColumns,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    selectionMode = selectionMode,
                    selectedIds = selection,
                    onOpen = { _, index ->
                        if (selectionMode) {
                            selection = selection.toggle(items[index].id)
                        } else {
                            onOpenViewer(index)
                        }
                    },
                    onLongPress = { item -> selection = selection.toggle(item.id) }
                )
            }

            if (syncing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    if (showTagDialog) {
        TagPickerDialog(
            existingTags = tags,
            onDismiss = { showTagDialog = false },
            onSubmit = { name ->
                viewModel.addTag(selection.toList(), name)
                selection = emptySet()
            }
        )
    }

    if (showRatingDialog) {
        RatingDialog(
            title = "Rate ${selection.size} item${if (selection.size == 1) "" else "s"}",
            initialRating = 0,
            onDismiss = { showRatingDialog = false },
            onConfirm = { rating ->
                viewModel.rateSelection(selection.toList(), rating)
                selection = emptySet()
            }
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (contains(id)) this - id else this + id

@Composable
private fun LibraryTopBar(
    subtitle: String,
    syncing: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Gallary",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (syncing) "Updating library…" else subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
        }
        CastButton()
        IconButton(onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onBackground)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onTag: () -> Unit,
    onRate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Exit selection")
                }
                Text(
                    text = "$count selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFavorite) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Favourite")
                }
                IconButton(onClick = onTag) {
                    Icon(Icons.Rounded.Sell, contentDescription = "Tag")
                }
                IconButton(onClick = onRate) {
                    Icon(Icons.Rounded.Star, contentDescription = "Rate")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove from library",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    kind: MediaKind,
    favoritesOnly: Boolean,
    minRating: Int,
    onKind: (MediaKind) -> Unit,
    onFavorites: () -> Unit,
    onRating: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            GalleryChip(label = "All", selected = kind == MediaKind.ALL) { onKind(MediaKind.ALL) }
        }
        item {
            GalleryChip(label = "Photos", selected = kind == MediaKind.PHOTOS) { onKind(MediaKind.PHOTOS) }
        }
        item {
            GalleryChip(label = "Videos", selected = kind == MediaKind.VIDEOS) { onKind(MediaKind.VIDEOS) }
        }
        item {
            GalleryChip(label = "Favourites", selected = favoritesOnly) { onFavorites() }
        }
        item {
            GalleryChip(label = "★ 4+", selected = minRating >= 4) { onRating(4) }
        }
    }
}

@Composable
fun GalleryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = null
    )
}
