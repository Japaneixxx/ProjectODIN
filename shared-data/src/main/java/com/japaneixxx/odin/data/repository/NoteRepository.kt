package com.japaneixxx.odin.data.repository

import com.japaneixxx.odin.data.dao.NoteDao
import com.japaneixxx.odin.data.dao.TagDao
import com.japaneixxx.odin.data.entity.NoteEntity
import com.japaneixxx.odin.data.entity.NoteTagCrossRef
import com.japaneixxx.odin.data.entity.NoteWithTags
import com.japaneixxx.odin.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val tagDao: TagDao
) {

    // Exponha usando o nome exato da função do NoteDao: getNotesWithTags()
    val notesWithTags: Flow<List<NoteWithTags>> = noteDao.getNotesWithTags()

    val allTags: Flow<List<TagEntity>> = tagDao.getAllTags()

    suspend fun insertNoteWithTags(note: NoteEntity, tagIds: List<Long>) {
        val noteId = noteDao.insertNote(note)
        tagIds.forEach { tagId ->
            noteDao.insertNoteTagCrossRef(NoteTagCrossRef(noteId = noteId, tagId = tagId))
        }
    }

    suspend fun updateNoteWithTags(note: NoteEntity, tagIds: List<Long>) {
        noteDao.updateNote(note)
        // Remove as associações antigas e insere as novas
        noteDao.deleteNoteTagCrossRefsByNoteId(note.id)
        tagIds.forEach { tagId ->
            noteDao.insertNoteTagCrossRef(NoteTagCrossRef(noteId = note.id, tagId = tagId))
        }
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    suspend fun insertTag(tag: TagEntity) {
        tagDao.insertTag(tag)
    }

    suspend fun updateTag(tag: TagEntity) {
        tagDao.updateTag(tag)
    }

    suspend fun deleteTag(tag: TagEntity) {
        tagDao.deleteTag(tag)
    }
}