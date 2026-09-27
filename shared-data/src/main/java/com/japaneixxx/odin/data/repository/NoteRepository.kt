package com.japaneixxx.odin.data.repository

import com.japaneixxx.odin.data.entity.*
import com.japaneixxx.odin.data.provider.OdinDataClient
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val dataClient: OdinDataClient
) {

    val notesWithTags: Flow<List<NoteWithTags>> = dataClient.observeNotesWithTags()
    val allTags: Flow<List<TagEntity>> = dataClient.observeTags()

    suspend fun searchPersonsForMention(query: String): List<PersonEntity> = dataClient.searchPersons(query)

    @androidx.room.Transaction
    suspend fun insertNoteWithTags(title: String, content: String, tagIds: List<Long>, mentionedPersonIds: List<Long> = emptyList()) {
        dataClient.insertNoteWithTags(NoteEntity(title = title, content = content), tagIds, mentionedPersonIds)
    }

    @androidx.room.Transaction
    suspend fun updateNoteWithTags(note: NoteEntity, newTagIds: List<Long>, mentionedPersonIds: List<Long> = emptyList()) {
        dataClient.updateNoteWithTags(note, newTagIds, mentionedPersonIds)
    }

    suspend fun deleteNote(note: NoteEntity) {
        dataClient.deleteNote(note)
    }

    suspend fun insertTag(tag: TagEntity) {
        dataClient.insertTag(tag)
    }

    suspend fun updateTag(tag: TagEntity) {
        dataClient.updateTag(tag)
    }

    suspend fun deleteTag(tag: TagEntity) {
        dataClient.deleteTag(tag)
    }
}