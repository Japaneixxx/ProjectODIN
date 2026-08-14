package com.japaneixxx.odin.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_templates")
data class BlockTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val templateId: Long = 0,
    val title: String // Ex: "Chave Pix", "Presentes", "Tamanho de Roupa"
)