package com.japaneixxx.odin.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["noteId", "tagId"]
)
data class NoteTagCrossRef(
    @ColumnInfo(name = "noteId")
    val noteId: Long,

    @ColumnInfo(name = "tagId")
    val tagId: Long
)