package com.japaneixxx.odin.data.dto

data class PersonMentionItemDto(
    val id: Long,
    val name: String,
    val nickname: String?,
    val photoPath: String?
) {
    // Retorna o texto formatado para exibição rápida: apelido com prioridade ou nome
    val displayName: String
        get() = if (!nickname.isNullOrBlank()) nickname else name
}