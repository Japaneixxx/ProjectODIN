package com.japaneixxx.odin.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "person_blocks",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["personId"])]
)
data class PersonBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    val title: String,       // Ex: "Presentes", "Restrições", "Tamanhos"
    val content: String,     // Conteúdo do bloco
    val orderIndex: Int = 0  // Ordem de exibição
)