package com.japaneixxx.odin.data.dao

import androidx.room.*
import com.japaneixxx.odin.data.entity.NoteEntity
import com.japaneixxx.odin.data.entity.NoteTagCrossRef
import com.japaneixxx.odin.data.entity.NoteWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Transaction
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesWithTags(): Flow<List<NoteWithTags>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteTagCrossRef(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM note_tag_cross_ref WHERE noteId = :noteId")
    suspend fun deleteNoteTagCrossRefsByNoteId(noteId: Long)
}