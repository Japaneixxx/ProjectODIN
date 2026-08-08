package com.japaneixxx.odin.data.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class NoteWithTags(
    @Embedded
    val note: NoteEntity,

    @Relation(
        parentColumn = "id",            // Coluna id da NoteEntity
        entityColumn = "id",            // Coluna id da TagEntity
        associateBy = Junction(
            value = NoteTagCrossRef::class,
            parentColumn = "noteId",    // Nome exato da propriedade na NoteTagCrossRef
            entityColumn = "tagId"      // Nome exato da propriedade na NoteTagCrossRef
        )
    )
    val tags: List<TagEntity>
)