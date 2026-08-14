package com.japaneixxx.odin.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FieldType {
    TEXT,       // Texto livre comum (sem interações)
    DATE,       // Data com seletor de calendário
    NUMBER,     // Valor numérico
    LINK,       // URL genérica de site
    WHATSAPP,   // Abre conversa direta no WhatsApp
    MAPS,       // Abre o endereço no Waze / Google Maps
    PIX,        // Copia a chave Pix e/ou abre atalho de pagamento
    PHONE       // Disca ou abre chamada telefônica
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
    val label: String,           // Ex: "WhatsApp", "Endereço", "Chave Pix"
    val value: String,           // Nome visível (Ex: "(31) 99999-9999" ou "Casa")
    val actionData: String? = null, // Dado técnico real (Ex: "31999999999", "Av. Contorno...", "123456789")
    val fieldType: FieldType = FieldType.TEXT,
    val orderIndex: Int = 0
)