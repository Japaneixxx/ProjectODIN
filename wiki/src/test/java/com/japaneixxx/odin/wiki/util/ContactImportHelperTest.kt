package com.japaneixxx.odin.wiki.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactImportHelperTest {
    @Test
    fun formatsIsoDateWithYear() {
        assertEquals("25/10/1995", ContactImportHelper.formatToDdMmYyyy("1995-10-25"))
    }

    @Test
    fun formatsCompactIsoDate() {
        assertEquals("25/10/1995", ContactImportHelper.formatToDdMmYyyy("19951025"))
    }

    @Test
    fun formatsBirthdayWithoutYear() {
        assertEquals("25/10", ContactImportHelper.formatToDdMmYyyy("--10-25"))
    }
}