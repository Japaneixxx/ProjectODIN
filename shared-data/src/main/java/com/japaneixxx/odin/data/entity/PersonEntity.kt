package com.japaneixxx.odin.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val nickname: String? = null,
    val relationship: String? = null, // Ex: "Amigo", "Família", "Trabalho"
    val notes: String? = null,        // Gostos, restrições, ideias de presente
    val photoPath: String? = null,    // Caminho para foto de perfil
    val createdAt: Long = System.currentTimeMillis()
)