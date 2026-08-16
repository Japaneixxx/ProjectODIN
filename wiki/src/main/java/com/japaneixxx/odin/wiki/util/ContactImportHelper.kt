package com.japaneixxx.odin.wiki.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class ImportedContact(
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val birthday: String? = null
)

object ContactImportHelper {

    @SuppressLint("Range")
    fun importFromUri(context: Context, contactUri: Uri): ImportedContact? {
        val resolver = context.contentResolver
        var contactId: String? = null
        var contactName = ""

        // 1. Pega o ID e o Nome do Contato selecionado
        resolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
            }
        }

        if (contactId == null) return null

        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        val websites = mutableListOf<String>()
        var birthday: String? = null

        // 2. Varre todos os dados associados a esse ID na base de dados do Android
        val dataCursor = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )

        dataCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE))

                when (mimeType) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val phone = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        if (!phone.isNullOrBlank()) phones.add(phone)
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        val email = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS))
                        if (!email.isNullOrBlank()) emails.add(email)
                    }
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        val address = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS))
                        if (!address.isNullOrBlank()) addresses.add(address)
                    }
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
                        val website = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Website.URL))
                        if (!website.isNullOrBlank()) websites.add(website)
                    }
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        val eventType = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE))
                        if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) {
                            val bday = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE))
                            if (!bday.isNullOrBlank()) {
                                birthday = formatToDdMmYyyy(bday) // <-- FORMATAÇÃO dd/MM/yyyy
                            }
                        }
                    }
                }
            }
        }

        return ImportedContact(
            name = contactName,
            phones = phones.distinct(),
            emails = emails.distinct(),
            addresses = addresses.distinct(),
            websites = websites.distinct(),
            birthday = birthday
        )
    }

    /**
     * Converte datas vindas da agenda (como YYYY-MM-DD ou YYYYMMDD) para dd/MM/yyyy
     */
    private fun formatToDdMmYyyy(rawDate: String): String {
        val clean = rawDate.trim()

        // Trata o formato sem ano "--MM-DD"
        if (clean.startsWith("--")) {
            val parts = clean.removePrefix("--").split("-")
            if (parts.size == 2) {
                val month = parts[0].padStart(2, '0')
                val day = parts[1].padStart(2, '0')
                return "$day/$month"
            }
        }

        // Trata o formato padrão "YYYY-MM-DD"
        if (clean.contains("-")) {
            val parts = clean.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = parts[1].padStart(2, '0')
                val day = parts[2].padStart(2, '0')
                return "$day/$month/$year"
            }
        }

        // Trata apenas números com 8 dígitos (ex: 19951025 ou 25101995)
        val digits = clean.replace(Regex("[^0-9]"), "")
        if (digits.length == 8) {
            return if (digits.startsWith("19") || digits.startsWith("20")) {
                // YYYYMMDD
                val year = digits.substring(0, 4)
                val month = digits.substring(4, 6)
                val day = digits.substring(6, 8)
                "$day/$month/$year"
            } else {
                // DDMMYYYY
                val day = digits.substring(0, 2)
                val month = digits.substring(2, 4)
                val year = digits.substring(4, 8)
                "$day/$month/$year"
            }
        }

        return clean
    }
}