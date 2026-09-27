package com.japaneixxx.odin.wiki.util

import com.japaneixxx.odin.data.entity.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonDuplicateMatcherTest {
    private val people = listOf(
        PersonEntity(id = 1, name = "Maria Silva", nickname = "Mari"),
        PersonEntity(id = 2, name = "Joao Santos")
    )

    @Test
    fun findsMatchByFullNameIgnoringCaseAndSpaces() {
        assertEquals(1L, PersonDuplicateMatcher.findMatch(people, "  maria silva ")?.id)
    }

    @Test
    fun findsMatchByNickname() {
        assertEquals(1L, PersonDuplicateMatcher.findMatch(people, "MARI")?.id)
    }

    @Test
    fun returnsNullWhenThereIsNoMatch() {
        assertNull(PersonDuplicateMatcher.findMatch(people, "Ana"))
    }

    @Test
    fun returnsNullForBlankImportedName() {
        assertNull(PersonDuplicateMatcher.findMatch(people, "   "))
    }
}