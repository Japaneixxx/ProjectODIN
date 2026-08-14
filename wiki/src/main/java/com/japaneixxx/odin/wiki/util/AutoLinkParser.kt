package com.japaneixxx.odin.wiki.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.japaneixxx.odin.data.entity.FieldType // IMPORTANTE PARA RECONHECER OS TIPOS

object AutoLinkParser {

    /**
     * Examina uma URL, Endereço ou Chave Pix e extrai um nome de exibição amigável.
     * Recebe o fieldType para tomar decisões baseadas no tipo de campo.
     */
    fun extractDisplayValue(context: Context?, input: String, fieldType: FieldType): String? {
        val cleanInput = input.trim()
        if (cleanInput.isBlank()) return null

        val digitsOnly = cleanInput.replace(Regex("[^0-9]"), "")

        // -------------------------------------------------------------------
        // 📍 1. MAPS / ENDEREÇO
        // -------------------------------------------------------------------
        if (cleanInput.contains("http://googleusercontent.com/maps") || cleanInput.startsWith("geo:")) {
            if (cleanInput.contains("q=")) {
                val query = cleanInput.substringAfter("q=").substringBefore("&")
                val decoded = android.net.Uri.decode(query).replace("+", " ")
                if (decoded.isNotBlank()) return formatShortAddress(decoded)
            }
            return "Localização no Mapa"
        }

        // -------------------------------------------------------------------
        // 💸 2. PIX & IDENTIFICAÇÃO DE CPF
        // -------------------------------------------------------------------
        if (cleanInput.contains("@") && !cleanInput.contains("http")) return cleanInput.lowercase()

        if (cleanInput.length == 36 && cleanInput.count { it == '-' } == 4) {
            val shortUuid = "${cleanInput.take(8)}...${cleanInput.takeLast(4)}"
            return "Chave Pix ($shortUuid)"
        }

        if (digitsOnly.length == 14) {
            return "${digitsOnly.substring(0, 2)}.${digitsOnly.substring(2, 5)}.${digitsOnly.substring(5, 8)}/${digitsOnly.substring(8, 12)}-${digitsOnly.substring(12)}"
        }

        if (digitsOnly.length == 11) {
            val hasCpfDot = cleanInput.contains(".")
            val hasCpfDash = cleanInput.contains("-") && cleanInput.substringAfterLast("-").replace(Regex("[^0-9]"), "").length == 2
            val isPureCpf = !cleanInput.contains(Regex("[\\.\\-\\(\\)]")) && digitsOnly.getOrNull(2) != '9'

            if (hasCpfDot || hasCpfDash || isPureCpf) {
                return "${digitsOnly.substring(0, 3)}.${digitsOnly.substring(3, 6)}.${digitsOnly.substring(6, 9)}-${digitsOnly.substring(9)}"
            }
        }

        // -------------------------------------------------------------------
        // 🌐 3. REDES SOCIAIS E TELEFONES
        // -------------------------------------------------------------------
        if (cleanInput.contains("instagram.com/", ignoreCase = true) || cleanInput.contains("instagr.am/", ignoreCase = true)) {
            val username = cleanInput.substringAfter("instagram.com/").substringAfter("instagr.am/").substringBefore("/").substringBefore("?")
            if (username.isNotBlank()) return "@$username"
        }
        if (cleanInput.contains("x.com/", ignoreCase = true) || cleanInput.contains("twitter.com/", ignoreCase = true)) {
            val username = cleanInput.substringAfter("x.com/").substringAfter("twitter.com/").substringBefore("/").substringBefore("?")
            if (username.isNotBlank()) return "@$username"
        }

        // WhatsApp Link
        if (cleanInput.contains("wa.me/", ignoreCase = true) || cleanInput.contains("api.whatsapp.com", ignoreCase = true)) {
            if (digitsOnly.length >= 10) {
                val phone = if (digitsOnly.length >= 12 && digitsOnly.startsWith("55")) {
                    if (digitsOnly.length == 13) digitsOnly.takeLast(11) else digitsOnly.takeLast(10)
                } else {
                    digitsOnly.takeLast(11)
                }

                // SE FOR UM CAMPO DE TELEFONE, PULA A AGENDA E FORMATA DIRETO
                if (fieldType == FieldType.PHONE) return formatPhoneNumber(phone)

                val phoneWith9 = if (phone.length == 10) "${phone.substring(0, 2)}9${phone.substring(2)}" else phone
                val phoneWithout9 = if (phone.length == 11 && phone[2] == '9') phone.substring(0, 2) + phone.substring(3) else phone

                val contactName = getContactNameByNumber(context, cleanInput)
                    ?: getContactNameByNumber(context, phone)
                    ?: getContactNameByNumber(context, phoneWith9)
                    ?: getContactNameByNumber(context, phoneWithout9)

                return contactName ?: formatPhoneNumber(phone)
            }
        }

        // Número de Celular/Telefone puro
        if (!cleanInput.contains("http")) {
            val isNormalPhone = digitsOnly.length in 10..11
            val isPhoneWithCountryCode = digitsOnly.length in 12..13 && digitsOnly.startsWith("55")

            if (isNormalPhone || isPhoneWithCountryCode) {
                val phone = if (isPhoneWithCountryCode) {
                    if (digitsOnly.length == 13) digitsOnly.takeLast(11) else digitsOnly.takeLast(10)
                } else {
                    digitsOnly
                }

                // SE FOR UM CAMPO DE TELEFONE, PULA A AGENDA E FORMATA DIRETO
                if (fieldType == FieldType.PHONE) return formatPhoneNumber(phone)

                val phoneWith9 = if (phone.length == 10) "${phone.substring(0, 2)}9${phone.substring(2)}" else phone
                val phoneWithout9 = if (phone.length == 11 && phone[2] == '9') phone.substring(0, 2) + phone.substring(3) else phone

                val contactName = getContactNameByNumber(context, cleanInput)
                    ?: getContactNameByNumber(context, phone)
                    ?: getContactNameByNumber(context, phoneWith9)
                    ?: getContactNameByNumber(context, phoneWithout9)

                return contactName ?: formatPhoneNumber(phone)
            }
        }

        if (cleanInput.startsWith("http://") || cleanInput.startsWith("https://")) {
            val domain = cleanInput.substringAfter("://").substringBefore("/").removePrefix("www.")
            if (domain.isNotBlank()) return domain
        }

        if (cleanInput.length > 15 && (cleanInput.contains(",") || cleanInput.contains("-"))) {
            return formatShortAddress(cleanInput)
        }

        return null
    }

    @SuppressLint("Range")
    private fun getContactNameByNumber(context: Context?, phoneNumber: String): String? {
        if (context == null) return null
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null

        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            var contactName: String? = null
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
            contactName
        } catch (e: Exception) {
            null
        }
    }

    private fun formatPhoneNumber(digits: String): String {
        return if (digits.length == 11) "(${digits.substring(0, 2)}) ${digits.substring(2, 7)}-${digits.substring(7)}"
        else if (digits.length == 10) "(${digits.substring(0, 2)}) ${digits.substring(2, 6)}-${digits.substring(6)}"
        else digits
    }

    private fun formatShortAddress(address: String): String {
        val parts = address.split(",")
        return if (parts.size >= 2) "${parts[0].trim()}, ${parts[1].trim().substringBefore("-").trim()}"
        else address.take(30)
    }
}