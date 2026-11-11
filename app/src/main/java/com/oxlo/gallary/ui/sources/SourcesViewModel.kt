package com.oxlo.gallary.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oxlo.gallary.data.db.SourceEntity
import com.oxlo.gallary.di.AppContainer
import com.oxlo.gallary.domain.RemoteEntry
import com.oxlo.gallary.domain.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the "Add source" sheet collects before it is persisted. */
data class SourceDraft(
    val type: SourceType = SourceType.SMB,
    val name: String = "",
    val host: String = "",
    val share: String = "",
    val basePath: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val useHttps: Boolean = true,
    val port: Int = 0
) {
    fun validationError(): String? = when (type) {
        SourceType.SMB -> when {
            host.isBlank() -> "Server address is required"
            share.isBlank() -> "Share name is required"
            else -> null
        }

        SourceType.WEBDAV -> when {
            host.isBlank() -> "Server URL is required"
            else -> null
        }

        SourceType.DRIVE -> null
    }
}

data class SourceRow(
    val source: SourceEntity,
    val itemCount: Int
)

data class BrowserState(
    val sourceId: Long,
    val sourceName: String,
    val path: String,
    val entries: List<RemoteEntry> = emptyList(),
    val loading: Boolean = true,
    val importing: Boolean = false,
    val error: String? = null
)

class SourcesViewModel(private val container: AppContainer) : ViewModel() {

    private val db = container.database

    val rows: StateFlow<List<SourceRow>> = combine(
        db.sourceDao().observeAll(),
        db.sourceDao().observeCounts()
    ) { sources, counts ->
        val countMap = counts.associate { it.sourceId to it.itemCount }
        sources.map { SourceRow(it, countMap[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _browser = MutableStateFlow<BrowserState?>(null)
    val browser: StateFlow<BrowserState?> = _browser.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun saveSource(draft: SourceDraft, onSaved: (Long) -> Unit) {
        draft.validationError()?.let {
            _message.value = it
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val entity = SourceEntity(
                name = draft.name.ifBlank { defaultName(draft) },
                type = draft.type.name,
                host = draft.host.trim(),
                share = draft.share.trim(),
                basePath = draft.basePath.trim(),
                username = draft.username.trim(),
                domain = draft.domain.trim(),
                useHttps = draft.useHttps,
                port = draft.port
            )
            val id = runCatching { db.sourceDao().insert(entity) }.getOrElse {
                _busy.value = false
                _message.value = "A source with that name already exists"
                return@launch
            }
            if (draft.password.isNotBlank()) {
                container.credentials.savePassword(id, draft.password)
            }

            val test = if (draft.type == SourceType.DRIVE) {
                Result.success(Unit)
            } else {
                container.remoteRepository.test(entity.copy(id = id))
            }

            _busy.value = false
            if (test.isSuccess) {
                _message.value = "\"${entity.name}\" connected"
                onSaved(id)
            } else {
                db.sourceDao().delete(id)
                container.credentials.clear(id)
                _message.value = test.exceptionOrNull()?.message ?: "Could not connect to that source"
            }
        }
    }

    fun testDraft(draft: SourceDraft) {
        viewModelScope.launch {
            _busy.value = true
            val entity = SourceEntity(
                name = draft.name.ifBlank { defaultName(draft) },
                type = draft.type.name,
                host = draft.host.trim(),
                share = draft.share.trim(),
                basePath = draft.basePath.trim(),
                username = draft.username.trim(),
                domain = draft.domain.trim(),
                useHttps = draft.useHttps,
                port = draft.port
            )
            val result = runCatching {
                container.remoteRepository.testDraft(entity, draft.password).getOrThrow()
            }
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { "Connection successful" },
                onFailure = { it.message ?: "Connection failed" }
            )
        }
    }

    fun deleteSource(row: SourceRow) {
        viewModelScope.launch {
            container.remoteRepository.removeSource(row.source.id)
            _message.value = "\"${row.source.name}\" removed"
        }
    }

    fun openBrowser(sourceId: Long, sourceName: String) {
        _browser.value = BrowserState(sourceId, sourceName, "/", loading = true)
        loadPath(sourceId, sourceName, "/")
    }

    fun closeBrowser() {
        _browser.value = null
    }

    fun navigate(path: String) {
        val current = _browser.value ?: return
        loadPath(current.sourceId, current.sourceName, path)
    }

    private fun loadPath(sourceId: Long, sourceName: String, path: String) {
        _browser.value = _browser.value?.copy(path = path, loading = true, error = null)
        viewModelScope.launch {
            runCatching { container.remoteRepository.browse(sourceId, path) }
                .onSuccess { entries ->
                    _browser.value = BrowserState(
                        sourceId = sourceId,
                        sourceName = sourceName,
                        path = path,
                        entries = entries,
                        loading = false
                    )
                }
                .onFailure { error ->
                    _browser.value = BrowserState(
                        sourceId = sourceId,
                        sourceName = sourceName,
                        path = path,
                        loading = false,
                        error = error.message ?: "Could not open that folder"
                    )
                }
        }
    }

    fun importCurrentFolder() {
        val current = _browser.value ?: return
        viewModelScope.launch {
            _browser.value = current.copy(importing = true)
            val imported = runCatching {
                container.remoteRepository.importFolder(current.sourceId, current.path)
            }
            _browser.value = _browser.value?.copy(importing = false)
            _message.value = imported.fold(
                onSuccess = { count ->
                    if (count == 0) "No new photos or videos found in this folder"
                    else "Imported $count item${if (count == 1) "" else "s"}"
                },
                onFailure = { it.message ?: "Import failed" }
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun defaultName(draft: SourceDraft): String = when (draft.type) {
        SourceType.SMB -> draft.share.ifBlank { draft.host }.ifBlank { "NAS" }
        SourceType.WEBDAV -> draft.host.ifBlank { "WebDAV" }
        SourceType.DRIVE -> "Google Drive"
    }
}
