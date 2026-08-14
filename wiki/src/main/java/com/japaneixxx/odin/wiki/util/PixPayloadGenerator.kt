package com.japaneixxx.odin.wiki.util

import java.text.Normalizer
import java.util.regex.Pattern

object PixPayloadGenerator {

    /**
     * Gera um Payload Pix (BRCode) válido offline a partir de uma chave e nome.
     */
    fun generatePayload(pixKey: String, merchantName: String, city: String = "BRASIL"): String {
        // Formata e identifica perfeitamente o tipo de chave
        val cleanKey = formatPixKey(pixKey)
        val cleanName = removeAccentsAndSpecialChars(merchantName).take(25)
        val cleanCity = removeAccentsAndSpecialChars(city).take(15)

        // Montagem dos blocos de dados (TLV - Tag, Length, Value)
        val payloadFormatIndicator = "000201"
        val pointOfInitiationMethod = "010211" // 11 = Reutilizável

        // Informações da Conta do Comerciante (Banco Central do Brasil)
        val gui = "0014br.gov.bcb.pix"
        val keyTag = "01${cleanKey.length.toString().padStart(2, '0')}$cleanKey"
        val merchantAccountInfoValue = gui + keyTag
        val merchantAccountInformation = "26${merchantAccountInfoValue.length.toString().padStart(2, '0')}$merchantAccountInfoValue"

        val merchantCategoryCode = "52040000" // 0000 = Não especificado
        val transactionCurrency = "5303986" // 986 = Moeda (Real - BRL)

        val countryCode = "5802BR"

        val merchantNameTag = "59${cleanName.length.toString().padStart(2, '0')}$cleanName"
        val merchantCityTag = "60${cleanCity.length.toString().padStart(2, '0')}$cleanCity"

        // Template de Dados Adicionais
        val txid = "0503***" // *** = ID gerado pelo banco na hora
        val additionalDataValue = txid
        val additionalDataField = "62${additionalDataValue.length.toString().padStart(2, '0')}$additionalDataValue"

        // Concatena tudo antes do CRC
        val payloadWithoutCrc = payloadFormatIndicator +
                pointOfInitiationMethod +
                merchantAccountInformation +
                merchantCategoryCode +
                transactionCurrency +
                countryCode +
                merchantNameTag +
                merchantCityTag +
                additionalDataField +
                "6304" // Prefixo do CRC

        // Calcula e anexa o CRC16 final
        return payloadWithoutCrc + calculateCRC16(payloadWithoutCrc)
    }

    // Inteligência para destrinchar e formatar exatamente o tipo de chave
    private fun formatPixKey(key: String): String {
        val original = key.trim()

        // 1. E-mail (Se tem @, não mexe, apenas converte para minúsculas)
        if (original.contains("@")) return original.lowercase()

        // 2. Chave Aleatória / EVP (Tem letras e hífens, formato UUID)
        if (original.contains("-") && original.any { it.isLetter() }) {
            return original.lowercase()
        }

        // Extrai apenas os números para avaliar
        val digitsOnly = original.replace(Regex("[^0-9]"), "")

        // 3. CNPJ (14 dígitos exatos)
        if (digitsOnly.length == 14) return digitsOnly

        // 4. Telemóvel com código de país já digitado (+55)
        if (original.startsWith("+")) return "+$digitsOnly"

        // 5. O Grande Desempate: CPF (11) vs Telemóvel (11)
        if (digitsOnly.length == 11) {
            // Se a pessoa usou pontos ou hífens (ex: 123.456.789-00), é CPF garantido.
            if (original.contains(".") || original.contains("-")) {
                return digitsOnly
            }

            // Se a pessoa usou parênteses (ex: (31) 99999-9999), é Telemóvel garantido.
            if (original.contains("(") || original.contains(")")) {
                return "+55$digitsOnly"
            }

            // Se for número puro, olhamos o 3º dígito:
            // Telemóveis no Brasil começam sempre por '9' a seguir ao DDD (ex: 31 9...)
            return if (digitsOnly[2] == '9') {
                "+55$digitsOnly"
            } else {
                digitsOnly // É um CPF puro sem formatação
            }
        }

        // 6. Telefone Fixo ou sem o 9 extra (10 dígitos)
        if (digitsOnly.length == 10) return "+55$digitsOnly"

        // Para tudo o resto, devolve apenas os números
        return digitsOnly
    }

    private fun removeAccentsAndSpecialChars(str: String): String {
        val normalized = Normalizer.normalize(str, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        val withoutAccents = pattern.matcher(normalized).replaceAll("")
        // Remove tudo que não seja letra, número ou espaço (o banco odeia caracteres estranhos no nome)
        return withoutAccents.replace(Regex("[^a-zA-Z0-9 ]"), "").uppercase().trim()
    }

    // Algoritmo CRC16-CCITT obrigatório do Banco Central
    private fun calculateCRC16(payload: String): String {
        var crc = 0xFFFF
        val polynomial = 0x1021
        val bytes = payload.toByteArray(Charsets.US_ASCII)

        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xFF shl 8)
            for (i in 0..7) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor polynomial
                } else {
                    crc shl 1
                }
            }
        }
        crc = crc and 0xFFFF
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}