package com.japaneixxx.odin.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "block_template_fields",
    foreignKeys = [
        ForeignKey(
            entity = BlockTemplateEntity::class,
            parentColumns = ["templateId"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class BlockTemplateFieldEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val label: String,
    val fieldType: FieldType = FieldType.TEXT,
    val orderIndex: Int = 0
)