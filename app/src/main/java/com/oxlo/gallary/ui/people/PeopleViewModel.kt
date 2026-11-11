package com.oxlo.gallary.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oxlo.gallary.data.db.FaceEntity
import com.oxlo.gallary.data.db.PersonSummary
import com.oxlo.gallary.data.face.FaceClusterer
import com.oxlo.gallary.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(private val container: AppContainer) : ViewModel() {

    val people: StateFlow<List<PersonSummary>> = container.mediaRepository.observePeople()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unnamedFaces: StateFlow<List<FaceEntity>> = container.mediaRepository.observeUnassignedFaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _clusters = MutableStateFlow<List<FaceClusterer.Cluster>>(emptyList())
    val clusters: StateFlow<List<FaceClusterer.Cluster>> = _clusters.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()

    fun tagFaces(faceIds: List<Long>, name: String) {
        viewModelScope.launch {
            container.mediaRepository.tagFacesWithPerson(faceIds, name)
        }
    }

    fun tagCluster(cluster: FaceClusterer.Cluster, name: String) {
        tagFaces(cluster.faces.map { it.id }, name)
        _clusters.value = _clusters.value - cluster
    }

    fun renamePerson(id: Long, name: String) {
        viewModelScope.launch { container.mediaRepository.renamePerson(id, name) }
    }

    fun deletePerson(id: Long) {
        viewModelScope.launch { container.mediaRepository.deletePerson(id) }
    }

    fun unassignFaces(faceIds: List<Long>) {
        viewModelScope.launch { container.mediaRepository.assignFaces(faceIds, null) }
    }

    fun suggestGroups() {
        val faces = unnamedFaces.value.filter { !it.descriptor.isNullOrBlank() }
        _clusters.value = FaceClusterer.cluster(faces).filter { it.faces.size > 1 }
    }

    fun clearSuggestions() {
        _clusters.value = emptyList()
    }

    fun scanFaces() {
        viewModelScope.launch {
            _scanning.value = true
            container.faceAnalyzer.scanMissing(limit = 400)
            _scanning.value = false
            _progress.value = null
        }
    }
}
