package com.japaneixxx.odin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.japaneixxx.odin.data.entity.NoteEntity
import com.japaneixxx.odin.data.entity.NoteWithTags
import com.japaneixxx.odin.data.entity.TagEntity
import com.japaneixxx.odin.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

    // Estado da busca por texto
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Estado da tag selecionada para filtro (null = mostra todas)
    private val _selectedFilterTagId = MutableStateFlow<Long?>(null)
    val selectedFilterTagId: StateFlow<Long?> = _selectedFilterTagId.asStateFlow()

    // Combina a lista do banco + busca + filtro por tag reativamente
    val filteredNotesWithTags: StateFlow<List<NoteWithTags>> = combine(
        repository.notesWithTags,
        _searchQuery,
        _selectedFilterTagId
    ) { notes, query, tagId ->
        notes.filter { noteWithTags ->
            // Filtro de Texto (Título ou Conteúdo)
            val matchesQuery = query.isBlank() ||
                    noteWithTags.note.title.contains(query, ignoreCase = true) ||
                    noteWithTags.note.content.contains(query, ignoreCase = true)

            // Filtro por Tag (se tagId for null, mostra todas as notas)
            val matchesTag = tagId == null ||
                    noteWithTags.tags.any { tag -> tag.id == tagId }

            matchesQuery && matchesTag
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notes: StateFlow<List<NoteWithTags>> = filteredNotesWithTags

    val allTags: StateFlow<List<TagEntity>> = repository.allTags
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterTagId(tagId: Long?) {
        // Alterne o estado: se a mesma tag for clicada novamente, limpa o filtro (volta para null)
        _selectedFilterTagId.value = if (_selectedFilterTagId.value == tagId) null else tagId
    }

    fun insertNoteWithTags(title: String, content: String, selectedTagIds: List<Long> = emptyList()) {
        viewModelScope.launch {
            val newNote = NoteEntity(title = title, content = content)
            repository.insertNoteWithTags(newNote, selectedTagIds)
        }
    }

    fun createTag(name: String, colorHex: String = "#6200EE") {
        viewModelScope.launch {
            val tag = TagEntity(name = name, colorHex = colorHex)
            repository.insertTag(tag)
        }
    }

    fun togglePinNote(noteWithTags: NoteWithTags) {
        viewModelScope.launch {
            val updatedNote = noteWithTags.note.copy(
                isPinned = !noteWithTags.note.isPinned,
                updatedAt = System.currentTimeMillis()
            )
            val tagIds = noteWithTags.tags.map { it.id }
            repository.updateNoteWithTags(updatedNote, tagIds)
        }
    }

    fun updateNote(
        note: NoteEntity,
        updatedTitle: String,
        updatedContent: String,
        selectedTagIds: List<Long>,
        isPinned: Boolean
    ) {
        viewModelScope.launch {
            val updatedNote = note.copy(
                title = updatedTitle,
                content = updatedContent,
                isPinned = isPinned,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNoteWithTags(updatedNote, selectedTagIds)
        }
    }

    fun updateTag(tag: TagEntity, newName: String, newColorHex: String) {
        viewModelScope.launch {
            val updatedTag = tag.copy(name = newName, colorHex = newColorHex)
            repository.updateTag(updatedTag)
        }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch {
            // Se a tag que está sendo excluída for a tag ativa no filtro, reseta o filtro
            if (_selectedFilterTagId.value == tag.id) {
                _selectedFilterTagId.value = null
            }
            repository.deleteTag(tag)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }
}

class NoteViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository) as T
        }
        throw IllegalArgumentException("Classe ViewModel desconhecida")
    }
}