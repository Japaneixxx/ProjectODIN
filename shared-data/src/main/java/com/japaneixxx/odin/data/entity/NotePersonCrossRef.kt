package com.japaneixxx.odin.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "note_person_cross_ref",
    primaryKeys = ["noteId", "personId"],
    indices = [Index(value = ["personId"])]
)
data class NotePersonCrossRef(
    val noteId: Long,
    val personId: Long
)