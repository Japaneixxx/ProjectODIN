package com.japaneixxx.odin.data.dao

import androidx.room.*
import com.japaneixxx.odin.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    // Ajuste aqui: mude 'WHERE note_id = :noteId' para 'WHERE noteId = :noteId'
    @Query("""
        SELECT tags.* FROM tags 
        INNER JOIN note_tag_cross_ref ON tags.id = note_tag_cross_ref.tagId 
        WHERE note_tag_cross_ref.noteId = :noteId
    """)
    fun getTagsForNote(noteId: Long): Flow<List<TagEntity>>
}