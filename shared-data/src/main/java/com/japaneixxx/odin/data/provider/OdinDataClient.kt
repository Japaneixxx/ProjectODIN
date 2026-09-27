package com.japaneixxx.odin.data.provider

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.japaneixxx.odin.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class OdinDataClient(private val resolver: ContentResolver) {

    fun observeNotesWithTags(): Flow<List<NoteWithTags>> = observe(OdinDataContract.NOTES_URI) {
        queryNotes().map { note ->
            NoteWithTags(note, queryTags(note.id))
        }
    }

    fun observeTags(): Flow<List<TagEntity>> = observe(OdinDataContract.TAGS_URI) { queryTags() }

    fun observeBlocks(personId: Long): Flow<List<PersonBlockEntity>> =
        observe(OdinDataContract.BLOCKS_URI) { queryBlocks(personId) }

    fun observeFields(blockId: Long): Flow<List<PersonBlockFieldEntity>> =
        observe(OdinDataContract.FIELDS_URI) { queryFields(blockId) }

    suspend fun searchPersons(query: String): List<PersonEntity> = withContext(Dispatchers.IO) {
        queryPersons(query)
    }

    suspend fun getPerson(personId: Long): PersonEntity? = withContext(Dispatchers.IO) {
        queryPersonsById(personId).firstOrNull()
    }

    suspend fun getTemplates(): List<BlockTemplateEntity> = withContext(Dispatchers.IO) {
        resolver.query(OdinDataContract.TEMPLATES_URI, null, null, null, null)?.use { cursor ->
            buildList {
                val id = cursor.getColumnIndexOrThrow("templateId")
                val title = cursor.getColumnIndexOrThrow("title")
                while (cursor.moveToNext()) add(BlockTemplateEntity(cursor.getLong(id), cursor.getString(title)))
            }
        } ?: emptyList()
    }

    suspend fun insertPerson(person: PersonEntity): Long = insert(
        OdinDataContract.PERSONS_URI,
        ContentValues().apply {
            put("name", person.name)
            put("nickname", person.nickname)
            put("relationship", person.relationship)
            put("notes", person.notes)
            put("photoPath", person.photoPath)
            put("createdAt", person.createdAt)
        }
    )

    suspend fun updatePerson(person: PersonEntity) = update(OdinDataContract.PERSONS_URI, person.id, ContentValues().apply {
        put("name", person.name)
        put("nickname", person.nickname)
        put("relationship", person.relationship)
        put("notes", person.notes)
        put("photoPath", person.photoPath)
        put("createdAt", person.createdAt)
    })

    suspend fun deletePerson(person: PersonEntity) = delete(OdinDataContract.PERSONS_URI, person.id)

    suspend fun insertNoteWithTags(note: NoteEntity, tagIds: List<Long>, personIds: List<Long>): Long {
        val noteId = insert(OdinDataContract.NOTES_URI, ContentValues().apply {
            put("title", note.title)
            put("content", note.content)
            put("isPinned", note.isPinned)
            put("updatedAt", note.updatedAt)
        })
        tagIds.forEach { insertLink(OdinDataContract.NOTE_TAGS_URI, noteId, "tagId", it) }
        personIds.forEach { insertLink(OdinDataContract.NOTE_PERSONS_URI, noteId, "personId", it) }
        return noteId
    }

    suspend fun updateNoteWithTags(note: NoteEntity, tagIds: List<Long>, personIds: List<Long>) {
        update(OdinDataContract.NOTES_URI, note.id, ContentValues().apply {
            put("title", note.title)
            put("content", note.content)
            put("isPinned", note.isPinned)
            put("updatedAt", note.updatedAt)
        })
        deleteLinks(OdinDataContract.NOTE_TAGS_URI, note.id, "noteId")
        deleteLinks(OdinDataContract.NOTE_PERSONS_URI, note.id, "noteId")
        tagIds.forEach { insertLink(OdinDataContract.NOTE_TAGS_URI, note.id, "tagId", it) }
        personIds.forEach { insertLink(OdinDataContract.NOTE_PERSONS_URI, note.id, "personId", it) }
    }

    suspend fun deleteNote(note: NoteEntity) = delete(OdinDataContract.NOTES_URI, note.id)

    suspend fun insertTag(tag: TagEntity): Long = insert(OdinDataContract.TAGS_URI, ContentValues().apply {
        put("name", tag.name)
        put("color_hex", tag.colorHex)
    })

    suspend fun updateTag(tag: TagEntity) = update(OdinDataContract.TAGS_URI, tag.id, ContentValues().apply {
        put("name", tag.name)
        put("color_hex", tag.colorHex)
    })

    suspend fun deleteTag(tag: TagEntity) = delete(OdinDataContract.TAGS_URI, tag.id)

    suspend fun insertBlock(block: PersonBlockEntity): Long = insert(OdinDataContract.BLOCKS_URI, ContentValues().apply {
        put("personId", block.personId)
        put("title", block.title)
        put("content", block.content)
        put("orderIndex", block.orderIndex)
    })

    suspend fun updateBlock(block: PersonBlockEntity) = update(OdinDataContract.BLOCKS_URI, block.id, ContentValues().apply {
        put("personId", block.personId)
        put("title", block.title)
        put("content", block.content)
        put("orderIndex", block.orderIndex)
    })

    suspend fun deleteBlock(block: PersonBlockEntity) = delete(OdinDataContract.BLOCKS_URI, block.id)

    suspend fun insertField(field: PersonBlockFieldEntity): Long = insert(OdinDataContract.FIELDS_URI, ContentValues().apply {
        put("blockId", field.blockId)
        put("label", field.label)
        put("value", field.value)
        put("actionData", field.actionData)
        put("fieldType", field.fieldType.name)
        put("orderIndex", field.orderIndex)
    })

    suspend fun updateField(field: PersonBlockFieldEntity) = update(OdinDataContract.FIELDS_URI, field.id, ContentValues().apply {
        put("blockId", field.blockId)
        put("label", field.label)
        put("value", field.value)
        put("actionData", field.actionData)
        put("fieldType", field.fieldType.name)
        put("orderIndex", field.orderIndex)
    })

    suspend fun deleteField(field: PersonBlockFieldEntity) = delete(OdinDataContract.FIELDS_URI, field.id)

    suspend fun insertTemplate(template: BlockTemplateEntity): Long = insert(OdinDataContract.TEMPLATES_URI, ContentValues().apply {
        put("title", template.title)
    })

    private suspend fun insert(uri: android.net.Uri, values: ContentValues): Long = withContext(Dispatchers.IO) {
        resolver.insert(uri, values)?.let(ContentUris::parseId) ?: -1L
    }

    private suspend fun update(uri: android.net.Uri, id: Long, values: ContentValues) = withContext(Dispatchers.IO) {
        resolver.update(ContentUris.withAppendedId(uri, id), values, null, null)
    }

    private suspend fun delete(uri: android.net.Uri, id: Long) = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(uri, id), null, null)
    }

    private suspend fun insertLink(uri: android.net.Uri, sourceId: Long, targetColumn: String, targetId: Long) {
        withContext(Dispatchers.IO) {
            resolver.insert(uri, ContentValues().apply {
                put("noteId", sourceId)
                put(targetColumn, targetId)
            })
        }
    }

    private suspend fun deleteLinks(uri: android.net.Uri, sourceId: Long, column: String) {
        withContext(Dispatchers.IO) {
            resolver.delete(uri, "$column = ?", arrayOf(sourceId.toString()))
        }
    }

    private fun <T> observe(uri: android.net.Uri, loader: () -> T): Flow<T> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(loader())
            }
        }
        resolver.registerContentObserver(uri, true, observer)
        trySend(loader())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun queryNotes(): List<NoteEntity> = resolver.query(OdinDataContract.NOTES_URI, null, null, null, null)?.use { cursor ->
        buildList {
            val id = cursor.getColumnIndexOrThrow("id")
            val title = cursor.getColumnIndexOrThrow("title")
            val content = cursor.getColumnIndexOrThrow("content")
            val pinned = cursor.getColumnIndexOrThrow("isPinned")
            val updated = cursor.getColumnIndexOrThrow("updatedAt")
            while (cursor.moveToNext()) add(NoteEntity(cursor.getLong(id), cursor.getString(title), cursor.getString(content), cursor.getInt(pinned) != 0, cursor.getLong(updated)))
        }
    } ?: emptyList()

    private fun queryTags(noteId: Long? = null): List<TagEntity> = resolver.query(
        OdinDataContract.TAGS_URI,
        null,
        null,
        noteId?.let { arrayOf(it.toString()) },
        null
    )?.use { cursor ->
        buildList {
            val id = cursor.getColumnIndexOrThrow("id")
            val name = cursor.getColumnIndexOrThrow("name")
            val color = cursor.getColumnIndexOrThrow("color_hex")
            while (cursor.moveToNext()) add(TagEntity(cursor.getLong(id), cursor.getString(name), cursor.getString(color)))
        }
    } ?: emptyList()

    private fun queryPersons(query: String): List<PersonEntity> = resolver.query(OdinDataContract.PERSONS_URI, null, null, arrayOf(query), null)?.use(::readPersons) ?: emptyList()

    private fun queryPersonsById(personId: Long): List<PersonEntity> = resolver.query(OdinDataContract.PERSONS_URI, null, "id = ?", arrayOf(personId.toString()), null)?.use(::readPersons) ?: emptyList()

    private fun readPersons(cursor: android.database.Cursor): List<PersonEntity> = buildList {
        val id = cursor.getColumnIndexOrThrow("id")
        val name = cursor.getColumnIndexOrThrow("name")
        val nickname = cursor.getColumnIndexOrThrow("nickname")
        val relationship = cursor.getColumnIndexOrThrow("relationship")
        val notes = cursor.getColumnIndexOrThrow("notes")
        val photo = cursor.getColumnIndexOrThrow("photoPath")
        val created = cursor.getColumnIndexOrThrow("createdAt")
        while (cursor.moveToNext()) add(PersonEntity(cursor.getLong(id), cursor.getString(name), cursor.getString(nickname), cursor.getString(relationship), cursor.getString(notes), cursor.getString(photo), cursor.getLong(created)))
    }

    private fun queryBlocks(personId: Long): List<PersonBlockEntity> = queryEntities(OdinDataContract.BLOCKS_URI, arrayOf(personId.toString())) { cursor ->
        PersonBlockEntity(cursor.getLong(cursor.getColumnIndexOrThrow("id")), personId, cursor.getString(cursor.getColumnIndexOrThrow("title")), cursor.getString(cursor.getColumnIndexOrThrow("content")), cursor.getInt(cursor.getColumnIndexOrThrow("orderIndex")))
    }

    private fun queryFields(blockId: Long): List<PersonBlockFieldEntity> = queryEntities(OdinDataContract.FIELDS_URI, arrayOf(blockId.toString())) { cursor ->
        PersonBlockFieldEntity(cursor.getLong(cursor.getColumnIndexOrThrow("id")), blockId, cursor.getString(cursor.getColumnIndexOrThrow("label")), cursor.getString(cursor.getColumnIndexOrThrow("value")), cursor.getString(cursor.getColumnIndexOrThrow("actionData")), FieldType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("fieldType"))), cursor.getInt(cursor.getColumnIndexOrThrow("orderIndex")))
    }

    private fun <T> queryEntities(uri: android.net.Uri, args: Array<String>, mapper: (android.database.Cursor) -> T): List<T> = resolver.query(uri, null, null, args, null)?.use { cursor ->
        buildList { while (cursor.moveToNext()) add(mapper(cursor)) }
    } ?: emptyList()
}