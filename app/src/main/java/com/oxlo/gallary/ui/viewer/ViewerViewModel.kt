package com.oxlo.gallary.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oxlo.gallary.data.db.FaceEntity
import com.oxlo.gallary.data.db.MediaItemEntity
import com.oxlo.gallary.data.db.TagEntity
import com.oxlo.gallary.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModel(private val container: AppContainer) : ViewModel() {

    private val mediaId = MutableStateFlow<Long?>(null)

    val tags: StateFlow<List<TagEntity>> = mediaId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.mediaRepository.observeTagsForMedia(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val faces: StateFlow<List<FaceEntity>> = mediaId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.mediaRepository.observeFacesForMedia(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _castStatus = MutableStateFlow<String?>(null)
    val castStatus: StateFlow<String?> = _castStatus.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val dataSourceFactory = container.dataSourceFactory

    fun setCurrent(id: Long) {
        if (mediaId.value != id) mediaId.value = id
    }

    fun setRating(id: Long, rating: Int) {
        viewModelScope.launch { container.mediaRepository.setRating(id, rating) }
    }

    fun toggleFavorite(item: MediaItemEntity) {
        viewModelScope.launch { container.mediaRepository.toggleFavorite(item) }
    }

    fun addTag(ids: List<Long>, name: String) {
        viewModelScope.launch { container.mediaRepository.tagMedia(ids, name) }
    }

    fun removeTag(mediaId: Long, tagId: Long) {
        viewModelScope.launch { container.mediaRepository.detachTag(mediaId, tagId) }
    }

    fun tagFace(faceId: Long, personName: String) {
        viewModelScope.launch { container.mediaRepository.tagFacesWithPerson(listOf(faceId), personName) }
    }

    fun analyzeFaces(item: MediaItemEntity) {
        viewModelScope.launch {
            _busy.value = true
            container.faceAnalyzer.analyze(item)
            _busy.value = false
        }
    }

    fun cast(item: MediaItemEntity) {
        viewModelScope.launch {
            _busy.value = true
            _castStatus.value = container.castManager.cast(item).fold(
                onSuccess = { device -> "Casting to $device" },
                onFailure = { error -> error.message ?: "Could not start casting" }
            )
            _busy.value = false
        }
    }

    fun stopCasting() {
        container.castManager.stop()
        _castStatus.value = "Stopped casting"
    }

    fun consumeCastStatus() {
        _castStatus.value = null
    }

    fun clear() {
        mediaId.value = null
    }
}
