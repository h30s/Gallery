package com.oxlo.gallary.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oxlo.gallary.data.db.AlbumSummary
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.data.settings.AppSettings
import com.oxlo.gallary.di.AppContainer
import com.oxlo.gallary.domain.MediaFilter
import com.oxlo.gallary.domain.MediaKind
import com.oxlo.gallary.domain.MediaScope
import com.oxlo.gallary.domain.SortOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the library state for the whole session, so the full-screen viewer can
 * page through exactly the list the user was looking at.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(private val container: AppContainer) : ViewModel() {

    private val _filter = MutableStateFlow(MediaFilter())
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()

    val media: StateFlow<List<MediaItemEntity>> = _filter
        .flatMapLatest { container.mediaRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<AlbumSummary>> = container.mediaRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val faceScanProgress: StateFlow<String?> = container.faceAnalyzer.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refresh(announce = false)
    }

    fun refresh(announce: Boolean = true) {
        viewModelScope.launch {
            _syncing.value = true
            val result = runCatching { container.mediaRepository.refreshLocalMedia() }
            _syncing.value = false
            if (announce) {
                _message.value = result.fold(
                    onSuccess = { "Library updated · ${it.total} items" },
                    onFailure = { "Could not read the device gallery" }
                )
            }
            if (result.isSuccess && settings.value.autoFaceDetection) {
                runCatching { container.faceAnalyzer.scanMissing(limit = 40) }
            }
        }
    }

    fun setKind(kind: MediaKind) {
        _filter.value = _filter.value.copy(kind = if (_filter.value.kind == kind) MediaKind.ALL else kind)
    }

    fun setScope(scope: MediaScope) {
        _filter.value = _filter.value.copy(scope = scope)
    }

    fun toggleFavoritesOnly() {
        _filter.value = _filter.value.copy(onlyFavorites = !_filter.value.onlyFavorites)
    }

    fun setMinRating(rating: Int) {
        _filter.value = _filter.value.copy(minRating = if (_filter.value.minRating == rating) 0 else rating)
    }

    fun setQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun setSort(sort: SortOrder) {
        _filter.value = _filter.value.copy(sort = sort)
    }

    fun clearFilters() {
        _filter.value = MediaFilter(sort = _filter.value.sort)
    }

    fun filterByAlbum(bucketName: String?) {
        _filter.value = _filter.value.copy(bucketName = bucketName)
    }

    fun filterByTag(tagId: Long) {
        _filter.value = _filter.value.copy(tagIds = setOf(tagId))
    }

    fun filterByPerson(personId: Long) {
        _filter.value = _filter.value.copy(personIds = setOf(personId))
    }

    fun setRating(item: MediaItemEntity, rating: Int) {
        viewModelScope.launch { container.mediaRepository.setRating(item.id, rating) }
    }

    fun rateSelection(ids: List<Long>, rating: Int) {
        viewModelScope.launch { container.mediaRepository.setRatingFor(ids, rating) }
    }

    fun toggleFavorite(item: MediaItemEntity) {
        viewModelScope.launch { container.mediaRepository.toggleFavorite(item) }
    }

    fun favoriteSelection(ids: List<Long>) {
        viewModelScope.launch { container.mediaRepository.setFavorite(ids, true) }
    }

    fun addTag(ids: List<Long>, name: String) {
        viewModelScope.launch {
            container.mediaRepository.tagMedia(ids, name)
            _message.value = "Tagged ${ids.size} item${if (ids.size == 1) "" else "s"} with \"$name\""
        }
    }

    fun removeFromLibrary(ids: List<Long>) {
        viewModelScope.launch {
            container.mediaRepository.deleteFromLibrary(ids)
            _message.value = "Removed ${ids.size} item${if (ids.size == 1) "" else "s"} from the library"
        }
    }

    fun scanFaces() {
        viewModelScope.launch {
            container.faceAnalyzer.scanMissing(limit = 200)
            _message.value = "Face scan finished"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { container.settings.setGridColumns(columns) }
    }
}
