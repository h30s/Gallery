package com.oxlo.gallary.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxlo.gallary.data.remote.DriveAuth
import com.oxlo.gallary.domain.RemoteEntry
import com.oxlo.gallary.domain.SourceType
import com.oxlo.gallary.ui.components.EmptyState
import com.oxlo.gallary.ui.components.formatCount
import com.oxlo.gallary.ui.components.formatBytes
import com.oxlo.gallary.ui.components.formatFullDate

@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rows by viewModel.rows.collectAsState()
    val browser by viewModel.browser.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SourceRow?>(null) }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "NAS, WebDAV and cloud storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (busy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add source")
                }
            }
        }

        if (rows.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Cloud,
                title = "No remote sources",
                subtitle = "Connect a NAS over SMB, a WebDAV server like Nextcloud, or Google Drive. " +
                    "Imported photos show up next to your local ones.",
                actionLabel = "Add a source",
                onAction = { showAdd = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(rows, key = { it.source.id }) { row ->
                    SourceCard(
                        row = row,
                        onBrowse = { viewModel.openBrowser(row.source.id, row.source.name) },
                        onDelete = { deleteTarget = row }
                    )
                }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeMessage() },
            confirmButton = { TextButton(onClick = { viewModel.consumeMessage() }) { Text("OK") } },
            text = { Text(text) }
        )
    }

    if (showAdd) {
        AddSourceSheet(
            busy = busy,
            onDismiss = { showAdd = false },
            onTest = viewModel::testDraft,
            onSave = { draft ->
                viewModel.saveSource(draft) { id ->
                    showAdd = false
                    viewModel.openBrowser(id, draft.name.ifBlank { "Source" })
                }
            },
            onDriveSignIn = {
                runCatching { context.startActivity(DriveAuth.signInIntent(context)) }
            }
        )
    }

    browser?.let { state ->
        BrowserSheet(
            state = state,
            onDismiss = { viewModel.closeBrowser() },
            onNavigate = viewModel::navigate,
            onImport = viewModel::importCurrentFolder
        )
    }

    deleteTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove \"${row.source.name}\"?") },
            text = {
                Text(
                    "The connection and its ${row.itemCount} indexed items are removed. " +
                        "Nothing is deleted from the server."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSource(row)
                    deleteTarget = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SourceCard(row: SourceRow, onBrowse: () -> Unit, onDelete: () -> Unit) {
    val type = SourceType.from(row.source.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onBrowse() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (type) {
                    SourceType.SMB -> Icons.Rounded.Router
                    SourceType.WEBDAV -> Icons.Rounded.Storage
                    SourceType.DRIVE -> Icons.Rounded.Cloud
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.source.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (type) {
                    SourceType.SMB -> "smb://${row.source.host}/${row.source.share}"
                    SourceType.WEBDAV -> row.source.host
                    SourceType.DRIVE -> "Google Drive"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatCount(row.itemCount, "item"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Remove source",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AddSourceSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    onTest: (SourceDraft) -> Unit,
    onSave: (SourceDraft) -> Unit,
    onDriveSignIn: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(SourceDraft()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add a source", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Credentials are stored in encrypted preferences on this device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { draft = draft.copy(type = type) },
                        label = { Text(type.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            when (draft.type) {
                SourceType.SMB -> {
                    Field("Display name", draft.name) { draft = draft.copy(name = it) }
                    Field("Server (host or IP)", draft.host) { draft = draft.copy(host = it) }
                    Field("Share name", draft.share) { draft = draft.copy(share = it) }
                    Field("Folder inside the share (optional)", draft.basePath) {
                        draft = draft.copy(basePath = it)
                    }
                    Field("Username", draft.username) { draft = draft.copy(username = it) }
                    Field("Password", draft.password, isPassword = true) {
                        draft = draft.copy(password = it)
                    }
                    Field("Domain (optional)", draft.domain) { draft = draft.copy(domain = it) }
                }

                SourceType.WEBDAV -> {
                    Field("Display name", draft.name) { draft = draft.copy(name = it) }
                    Field("Server URL or host", draft.host) { draft = draft.copy(host = it) }
                    Field("Path (optional, e.g. remote.php/dav/files/me)", draft.basePath) {
                        draft = draft.copy(basePath = it)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use HTTPS", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = draft.useHttps,
                            onCheckedChange = { draft = draft.copy(useHttps = it) },
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Field("Username", draft.username) { draft = draft.copy(username = it) }
                    Field("Password", draft.password, isPassword = true) {
                        draft = draft.copy(password = it)
                    }
                }

                SourceType.DRIVE -> {
                    Text(
                        text = "Sign in with the Google account that holds your photos. " +
                            "The app only requests read-only Drive access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onDriveSignIn) {
                        Text("Sign in with Google")
                    }
                    Field("Display name", draft.name.ifBlank { "Google Drive" }) {
                        draft = draft.copy(name = it)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (draft.type != SourceType.DRIVE) {
                    OutlinedButton(
                        onClick = { onTest(draft) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Test") }
                }
                Button(
                    onClick = { onSave(draft) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BrowserSheet(
    state: BrowserState,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onImport: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(state.sourceName, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val parent = state.path.trimEnd('/').substringBeforeLast('/', "")
                        onNavigate(if (parent.isBlank()) "/" else parent)
                    }
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Up one level")
                }
                Text(
                    text = state.path,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onImport,
                enabled = !state.importing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                if (state.importing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Import this folder")
                }
            }

            when {
                state.loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.error != null -> Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.entries) { entry ->
                        BrowserRow(entry = entry, onClick = {
                            if (entry.isDirectory) onNavigate(entry.path)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserRow(entry: RemoteEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.isDirectory, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = when {
                entry.isDirectory -> Icons.Rounded.Folder
                entry.isVideo -> Icons.Rounded.Movie
                else -> Icons.Rounded.Photo
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDirectory) {
                Text(
                    text = "${formatBytes(entry.size)} · ${formatFullDate(entry.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (entry.isDirectory) {
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
