package com.oxlo.gallary.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxlo.gallary.data.face.FaceClusterer
import com.oxlo.gallary.ui.components.CountBadge
import com.oxlo.gallary.ui.components.EmptyState
import com.oxlo.gallary.ui.components.FaceAvatar
import com.oxlo.gallary.ui.gallery.PersonNameDialog

@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel,
    onOpenPerson: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val people by viewModel.people.collectAsState()
    val unnamed by viewModel.unnamedFaces.collectAsState()
    val clusters by viewModel.clusters.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val progress by viewModel.progress.collectAsState()

    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var namingFaces by remember { mutableStateOf<List<Long>>(emptyList()) }
    var namingCluster by remember { mutableStateOf<FaceClusterer.Cluster?>(null) }
    var renamePerson by remember { mutableStateOf<Pair<Long, String>?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "People",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${people.size} named · ${unnamed.size} to tag",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { viewModel.suggestGroups() }) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Suggest groups")
            }
            IconButton(onClick = { viewModel.scanFaces() }, enabled = !scanning) {
                if (scanning) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(Icons.Rounded.PersonSearch, contentDescription = "Scan for faces")
                }
            }
        }

        if (progress != null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        if (selection.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selection.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { namingFaces = selection.toList() }) { Text("Name") }
                    IconButton(onClick = { selection = emptySet() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (people.isEmpty() && unnamed.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Face,
                title = "No faces yet",
                subtitle = "Scan your library to detect faces, then give them names to search by person.",
                actionLabel = "Scan my photos",
                onAction = { viewModel.scanFaces() },
                modifier = Modifier.fillMaxSize()
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (clusters.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel("Suggested groups", "Tap to name everyone in the group")
                }
                items(clusters, key = { "cluster_${it.faces.first().id}" }) { cluster ->
                    PersonCard(
                        path = cluster.coverPath,
                        name = "Unnamed",
                        count = cluster.faces.size,
                        onClick = { namingCluster = cluster }
                    )
                }
            }

            if (people.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel("People", "Tap to see every photo")
                }
                items(people, key = { "person_${it.id}" }) { person ->
                    PersonCard(
                        path = person.coverFacePath,
                        name = person.name,
                        count = person.faceCount,
                        onClick = { onOpenPerson(person.id) },
                        onLongClick = { renamePerson = person.id to person.name }
                    )
                }
            }

            if (unnamed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(
                        "Unnamed faces",
                        if (selection.isEmpty()) "Tap to name, long-press to select several"
                        else "Tap more faces to add to the selection"
                    )
                }
                items(unnamed, key = { "face_${it.id}" }) { face ->
                    val selected = face.id in selection
                    Box {
                        FaceAvatar(
                            path = face.cropPath,
                            name = "Unnamed",
                            size = 88.dp,
                            selected = selected,
                            onClick = {
                                if (selection.isEmpty()) {
                                    namingFaces = listOf(face.id)
                                } else {
                                    selection = if (selected) selection - face.id else selection + face.id
                                }
                            },
                            onLongClick = {
                                selection = if (selected) selection - face.id else selection + face.id
                            }
                        )
                    }
                }
            }
        }
    }

    if (namingFaces.isNotEmpty()) {
        PersonNameDialog(
            title = if (namingFaces.size == 1) "Who is this?" else "Name ${namingFaces.size} faces",
            onDismiss = { namingFaces = emptyList() },
            onSubmit = { name ->
                viewModel.tagFaces(namingFaces, name)
                namingFaces = emptyList()
                selection = emptySet()
            }
        )
    }

    namingCluster?.let { cluster ->
        PersonNameDialog(
            title = "Name this group (${cluster.faces.size})",
            onDismiss = { namingCluster = null },
            onSubmit = { name ->
                viewModel.tagCluster(cluster, name)
                namingCluster = null
            }
        )
    }

    renamePerson?.let { (id, current) ->
        PersonNameDialog(
            title = "Rename \"$current\"",
            onDismiss = { renamePerson = null },
            onSubmit = { name ->
                viewModel.renamePerson(id, name)
                renamePerson = null
            }
        )
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PersonCard(
    path: String?,
    name: String,
    count: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FaceAvatar(
            path = path,
            name = name,
            size = 88.dp,
            onClick = onClick,
            onLongClick = onLongClick
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CountBadge(count)
            Text(
                text = if (count == 1) "photo" else "photos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
