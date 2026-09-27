package com.japaneixxx.odin.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["noteId", "tagId"],
    indices = [Index(value = ["tagId"])] // <-- ADICIONE ESTA LINHA
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long
)