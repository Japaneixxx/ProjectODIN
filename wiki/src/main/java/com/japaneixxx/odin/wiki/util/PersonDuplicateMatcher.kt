package com.japaneixxx.odin.wiki.util

import com.japaneixxx.odin.data.entity.PersonEntity

object PersonDuplicateMatcher {
    fun findMatch(people: List<PersonEntity>, importedName: String): PersonEntity? {
        val normalizedName = importedName.trim()
        if (normalizedName.isEmpty()) return null

        return people.firstOrNull { person ->
            person.name.equals(normalizedName, ignoreCase = true) ||
                    person.nickname?.equals(normalizedName, ignoreCase = true) == true
        }
    }
}