package com.oxlo.gallary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oxlo.gallary.data.MediaRepository
import com.oxlo.gallary.data.db.MediaItemEntity

/**
 * The main media grid: square tiles grouped under month headers. Shared by the
 * library, albums, tags, people and search results.
 */
@Composable
fun MediaGrid(
    items: List<MediaItemEntity>,
    repository: MediaRepository,
    columns: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    header: (@Composable () -> Unit)? = null,
    onOpen: (MediaItemEntity, Int) -> Unit,
    onLongPress: (MediaItemEntity) -> Unit
) {
    val grouped = remember(items) { items.groupBy { monthKey(it.dateTaken) } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }

        grouped.forEach { (_, monthItems) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "header_${monthItems.first().id}_${
                monthKey(monthItems.first().dateTaken)
            }") {
                MonthHeader(
                    label = formatMonthHeader(monthItems.first().dateTaken),
                    count = monthItems.size
                )
            }

            items(items = monthItems, key = { it.id }) { item ->
                MediaThumbnail(
                    item = item,
                    repository = repository,
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onClick = { onOpen(item, items.indexOf(item)) },
                    onLongClick = { onLongPress(item) }
                )
            }
        }
    }
}

@Composable
fun MonthHeader(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = formatCount(count, "item"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
