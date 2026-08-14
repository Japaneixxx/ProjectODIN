package com.japaneixxx.odin.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FieldType {
    TEXT,   // Texto livre / tamanho / marca
    DATE,   // Data com seletor (DD/MM/AAAA)
    NUMBER  // Valores numéricos (ex: Pix numérico, medições)
}

@Entity(
    tableName = "person_block_fields",
    foreignKeys = [
        ForeignKey(
            entity = PersonBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["blockId"])]
)
data class PersonBlockFieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val blockId: Long,
    val label: String,       // Ex: "Aniversário", "Camiseta", "Chave Pix"
    val value: String,       // O valor armazenado
    val fieldType: FieldType = FieldType.TEXT,
    val orderIndex: Int = 0
)