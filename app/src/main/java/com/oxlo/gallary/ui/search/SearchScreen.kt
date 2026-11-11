package com.oxlo.gallary.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.oxlo.gallary.data.MediaRepository
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.data.db.PersonSummary
import com.oxlo.gallary.data.db.TagSummary
import com.oxlo.gallary.domain.MediaFilter
import com.oxlo.gallary.domain.MediaKind
import com.oxlo.gallary.domain.MediaScope
import com.oxlo.gallary.domain.SortOrder
import com.oxlo.gallary.ui.components.EmptyState
import com.oxlo.gallary.ui.components.MediaGrid
import com.oxlo.gallary.ui.components.formatCount
import com.oxlo.gallary.ui.gallery.GalleryChip

@Composable
fun SearchScreen(
    filter: MediaFilter,
    results: List<MediaItemEntity>,
    tags: List<TagSummary>,
    people: List<PersonSummary>,
    repository: MediaRepository,
    columns: Int,
    onFilterChange: (MediaFilter) -> Unit,
    onOpenResult: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var sortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            TextField(
                value = filter.query,
                onValueChange = { onFilterChange(filter.copy(query = it)) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .focusRequester(focusRequester),
                placeholder = { Text("Search photos, videos, tags, people") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { onFilterChange(filter.copy(query = "")) }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                GalleryChip("Photos", filter.kind == MediaKind.PHOTOS) {
                    onFilterChange(filter.copy(kind = if (filter.kind == MediaKind.PHOTOS) MediaKind.ALL else MediaKind.PHOTOS))
                }
            }
            item {
                GalleryChip("Videos", filter.kind == MediaKind.VIDEOS) {
                    onFilterChange(filter.copy(kind = if (filter.kind == MediaKind.VIDEOS) MediaKind.ALL else MediaKind.VIDEOS))
                }
            }
            item {
                GalleryChip("Favourites", filter.onlyFavorites) {
                    onFilterChange(filter.copy(onlyFavorites = !filter.onlyFavorites))
                }
            }
            item {
                GalleryChip("★ 4+", filter.minRating >= 4) {
                    onFilterChange(filter.copy(minRating = if (filter.minRating >= 4) 0 else 4))
                }
            }
            item {
                GalleryChip("On device", filter.scope == MediaScope.LOCAL) {
                    onFilterChange(filter.copy(scope = if (filter.scope == MediaScope.LOCAL) MediaScope.ALL else MediaScope.LOCAL))
                }
            }
            item {
                GalleryChip("Remote", filter.scope == MediaScope.REMOTE) {
                    onFilterChange(filter.copy(scope = if (filter.scope == MediaScope.REMOTE) MediaScope.ALL else MediaScope.REMOTE))
                }
            }
        }

        if (tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tags, key = { it.id }) { tag ->
                    GalleryChip("#${tag.name}", tag.id in filter.tagIds) {
                        onFilterChange(filter.copy(tagIds = filter.tagIds.toggle(tag.id)))
                    }
                }
            }
        }

        if (people.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(people, key = { it.id }) { person ->
                    GalleryChip(person.name, person.id in filter.personIds) {
                        onFilterChange(filter.copy(personIds = filter.personIds.toggle(person.id)))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatCount(results.size, "result"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { sortMenu = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Sort,
                        contentDescription = "Sort",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = filter.sort.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            onClick = {
                                onFilterChange(filter.copy(sort = order))
                                sortMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (results.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Search,
                title = "No matches",
                subtitle = "Try a different word, or remove a filter. Search looks at file names, " +
                    "albums, tags and people.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            MediaGrid(
                items = results,
                repository = repository,
                columns = columns,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                onOpen = { _, index -> onOpenResult(index) },
                onLongPress = { }
            )
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (contains(id)) this - id else this + id
