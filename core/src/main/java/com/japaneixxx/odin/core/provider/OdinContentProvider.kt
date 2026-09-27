package com.japaneixxx.odin.core.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import androidx.room.Room
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.*
import com.japaneixxx.odin.data.provider.OdinDataContract
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class OdinContentProvider : ContentProvider() {

    private val PERSONS_CODE = 100
    private val NOTES_CODE = 101
    private val TAGS_CODE = 102
    private val NOTE_TAGS_CODE = 103
    private val NOTE_PERSONS_CODE = 104
    private val BLOCKS_CODE = 105
    private val FIELDS_CODE = 106
    private val TEMPLATES_CODE = 107

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(OdinDataContract.AUTHORITY, "persons", PERSONS_CODE)
        addURI(OdinDataContract.AUTHORITY, "persons/#", PERSONS_CODE)
        addURI(OdinDataContract.AUTHORITY, "notes", NOTES_CODE)
        addURI(OdinDataContract.AUTHORITY, "notes/#", NOTES_CODE)
        addURI(OdinDataContract.AUTHORITY, "tags", TAGS_CODE)
        addURI(OdinDataContract.AUTHORITY, "tags/#", TAGS_CODE)
        addURI(OdinDataContract.AUTHORITY, "note-tags", NOTE_TAGS_CODE)
        addURI(OdinDataContract.AUTHORITY, "note-persons", NOTE_PERSONS_CODE)
        addURI(OdinDataContract.AUTHORITY, "person-blocks", BLOCKS_CODE)
        addURI(OdinDataContract.AUTHORITY, "person-blocks/#", BLOCKS_CODE)
        addURI(OdinDataContract.AUTHORITY, "person-block-fields", FIELDS_CODE)
        addURI(OdinDataContract.AUTHORITY, "person-block-fields/#", FIELDS_CODE)
        addURI(OdinDataContract.AUTHORITY, "block-templates", TEMPLATES_CODE)
    }

    private lateinit var database: OdinDatabase

    override fun onCreate(): Boolean {
        val context = context ?: return false
        database = Room.databaseBuilder<OdinDatabase>(
            context,
            OdinDatabase::class.java,
            "odin_database.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val cursor = when (uriMatcher.match(uri)) {
            PERSONS_CODE -> {
                val query = selectionArgs?.firstOrNull() ?: ""
                val id = uri.lastPathSegment?.toLongOrNull() ?: -1L
                if (selection == "id = ?" || id != -1L) {
                    database.openHelper.readableDatabase.query(SimpleSQLiteQuery("SELECT * FROM persons WHERE id = ?", arrayOf(if (id == -1L) query.toLongOrNull() ?: -1L else id)))
                } else {
                    database.openHelper.readableDatabase.query(
                        SimpleSQLiteQuery(
                            "SELECT * FROM persons WHERE (? = '' OR name LIKE '%' || ? || '%' OR nickname LIKE '%' || ? || '%' OR relationship LIKE '%' || ? || '%') ORDER BY name ASC",
                            arrayOf(query, query, query, query)
                        )
                    )
                }
            }
            NOTES_CODE -> database.openHelper.readableDatabase.query(
                SimpleSQLiteQuery("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
            )
            TAGS_CODE -> if (selectionArgs?.isNotEmpty() == true) {
                database.openHelper.readableDatabase.query(SimpleSQLiteQuery(
                    "SELECT tags.* FROM tags INNER JOIN note_tag_cross_ref ON tags.id = note_tag_cross_ref.tagId WHERE note_tag_cross_ref.noteId = ? ORDER BY tags.name ASC",
                    arrayOf(selectionArgs[0].toLongOrNull() ?: -1L)
                ))
            } else {
                database.openHelper.readableDatabase.query(SimpleSQLiteQuery("SELECT * FROM tags ORDER BY name ASC"))
            }
            NOTE_TAGS_CODE -> database.openHelper.readableDatabase.query(
                SimpleSQLiteQuery("SELECT * FROM note_tag_cross_ref WHERE noteId = ?", arrayOf(selectionArgs?.firstOrNull()?.toLongOrNull() ?: -1L))
            )
            NOTE_PERSONS_CODE -> database.openHelper.readableDatabase.query(
                SimpleSQLiteQuery("SELECT * FROM note_person_cross_ref WHERE noteId = ?", arrayOf(selectionArgs?.firstOrNull()?.toLongOrNull() ?: -1L))
            )
            BLOCKS_CODE -> database.openHelper.readableDatabase.query(
                SimpleSQLiteQuery("SELECT * FROM person_blocks WHERE personId = ? ORDER BY orderIndex ASC, id ASC", arrayOf(selectionArgs?.firstOrNull()?.toLongOrNull() ?: -1L))
            )
            FIELDS_CODE -> database.openHelper.readableDatabase.query(
                SimpleSQLiteQuery("SELECT * FROM person_block_fields WHERE blockId = ? ORDER BY orderIndex ASC, id ASC", arrayOf(selectionArgs?.firstOrNull()?.toLongOrNull() ?: -1L))
            )
            TEMPLATES_CODE -> database.openHelper.readableDatabase.query(
                SimpleSQLiteQuery("SELECT * FROM block_templates ORDER BY title ASC")
            )
            else -> throw IllegalArgumentException("URI Desconhecida: $uri")
        }
        cursor.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val newId: Long = when (uriMatcher.match(uri)) {
            PERSONS_CODE -> {
                if (values == null) return null

                val person = PersonEntity(
                    id = 0, // 0 para o Room gerar auto-incremento
                    name = values.getAsString("name") ?: "",
                    nickname = values.getAsString("nickname"),
                    relationship = values.getAsString("relationship"),
                    notes = values.getAsString("notes"),
                    photoPath = values.getAsString("photoPath"),
                    createdAt = values.getAsLong("createdAt") ?: System.currentTimeMillis()
                )

                runBlocking(Dispatchers.IO) {
                    database.personDao().insertPerson(person)
                }
            }
            NOTES_CODE -> {
                if (values == null) return null
                runBlocking(Dispatchers.IO) {
                    database.noteDao().insertNote(
                        NoteEntity(
                            title = values.getAsString("title") ?: "",
                            content = values.getAsString("content") ?: "",
                            isPinned = values.getAsBoolean("isPinned") ?: false,
                            updatedAt = values.getAsLong("updatedAt") ?: System.currentTimeMillis()
                        )
                    )
                }
            }
            TAGS_CODE -> {
                if (values == null) return null
                runBlocking(Dispatchers.IO) {
                    database.tagDao().insertTag(
                        TagEntity(
                            name = values.getAsString("name") ?: "",
                            colorHex = values.getAsString("color_hex") ?: "#6200EE"
                        )
                    )
                }
            }
            NOTE_TAGS_CODE -> {
                if (values == null) return null
                runBlocking(Dispatchers.IO) {
                    database.noteDao().insertNoteTagCrossRef(
                        NoteTagCrossRef(values.getAsLong("noteId"), values.getAsLong("tagId"))
                    )
                    0L
                }
            }
            NOTE_PERSONS_CODE -> {
                if (values == null) return null
                runBlocking(Dispatchers.IO) {
                    database.noteDao().insertNotePersonCrossRef(
                        NotePersonCrossRef(values.getAsLong("noteId"), values.getAsLong("personId"))
                    )
                    0L
                }
            }
            BLOCKS_CODE -> {
                if (values == null) return null
                runBlocking(Dispatchers.IO) {
                    database.personBlockDao().insertBlock(
                        PersonBlockEntity(
                            personId = values.getAsLong("personId"),
                            title = values.getAsString("title") ?: "",
                            content = values.getAsString("content") ?: "",
                            orderIndex = values.getAsInteger("orderIndex") ?: 0
                        )
                    )
                }
            }
            FIELDS_CODE -> {
                if (values == null) return null
                runBlocking(Dispatchers.IO) {
                    database.personBlockDao().insertField(
                        PersonBlockFieldEntity(
                            blockId = values.getAsLong("blockId"),
                            label = values.getAsString("label") ?: "",
                            value = values.getAsString("value") ?: "",
                            actionData = values.getAsString("actionData"),
                            fieldType = FieldType.valueOf(values.getAsString("fieldType") ?: FieldType.TEXT.name),
                            orderIndex = values.getAsInteger("orderIndex") ?: 0
                        )
                    )
                }
            }
            TEMPLATES_CODE -> {
                if (values == null) return null
                runBlocking(Dispatchers.IO) {
                    database.blockTemplateDao().insertTemplate(
                        BlockTemplateEntity(title = values.getAsString("title") ?: "")
                    )
                }
            }
            else -> throw IllegalArgumentException("URI Desconhecida para Insert: $uri")
        }
        context?.contentResolver?.notifyChange(uri, null)
        if (uriMatcher.match(uri) == NOTE_TAGS_CODE || uriMatcher.match(uri) == NOTE_PERSONS_CODE) {
            context?.contentResolver?.notifyChange(OdinDataContract.NOTES_URI, null)
        }
        return ContentUris.withAppendedId(uri, newId)
    }

    override fun getType(uri: Uri): String? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = uri.lastPathSegment?.toLongOrNull() ?: -1L
        runBlocking(Dispatchers.IO) {
            when (uriMatcher.match(uri)) {
                PERSONS_CODE -> database.openHelper.writableDatabase.execSQL("DELETE FROM persons WHERE id = ?", arrayOf(id))
                NOTES_CODE -> database.noteDao().deleteNote(NoteEntity(id, "", ""))
                TAGS_CODE -> database.tagDao().deleteTag(TagEntity(id, ""))
                NOTE_TAGS_CODE -> database.openHelper.writableDatabase.execSQL("DELETE FROM note_tag_cross_ref WHERE noteId = ?", selectionArgs ?: arrayOf(id.toString()))
                NOTE_PERSONS_CODE -> database.openHelper.writableDatabase.execSQL("DELETE FROM note_person_cross_ref WHERE noteId = ?", selectionArgs ?: arrayOf(id.toString()))
                BLOCKS_CODE -> database.personBlockDao().deleteBlock(PersonBlockEntity(id, 0, "", ""))
                FIELDS_CODE -> database.personBlockDao().deleteField(PersonBlockFieldEntity(id, 0, "", ""))
                else -> throw IllegalArgumentException("URI Desconhecida para Delete: $uri")
            }
        }
        context?.contentResolver?.notifyChange(uri, null)
        if (uriMatcher.match(uri) == NOTE_TAGS_CODE || uriMatcher.match(uri) == NOTE_PERSONS_CODE) {
            context?.contentResolver?.notifyChange(OdinDataContract.NOTES_URI, null)
        }
        return 1
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (values == null) return 0
        val id = uri.lastPathSegment?.toLongOrNull() ?: -1L
        runBlocking(Dispatchers.IO) {
            when (uriMatcher.match(uri)) {
                PERSONS_CODE -> database.personDao().updatePerson(
                    PersonEntity(id, values.getAsString("name") ?: "", values.getAsString("nickname"), values.getAsString("relationship"), values.getAsString("notes"), values.getAsString("photoPath"), values.getAsLong("createdAt") ?: System.currentTimeMillis())
                )
                NOTES_CODE -> {
                    val tagIds = values.getAsString("tagIds")
                        ?.split(",")
                        ?.mapNotNull { it.toLongOrNull() }
                        ?: emptyList()
                    val personIds = values.getAsString("personIds")
                        ?.split(",")
                        ?.mapNotNull { it.toLongOrNull() }
                        ?: emptyList()
                    val writableDatabase = database.openHelper.writableDatabase
                    writableDatabase.beginTransaction()
                    try {
                        database.noteDao().updateNote(
                            NoteEntity(id, values.getAsString("title") ?: "", values.getAsString("content") ?: "", values.getAsBoolean("isPinned") ?: false, values.getAsLong("updatedAt") ?: System.currentTimeMillis())
                        )
                        writableDatabase.execSQL("DELETE FROM note_tag_cross_ref WHERE noteId = ?", arrayOf(id))
                        writableDatabase.execSQL("DELETE FROM note_person_cross_ref WHERE noteId = ?", arrayOf(id))
                        tagIds.forEach { tagId ->
                            writableDatabase.execSQL(
                                "INSERT OR IGNORE INTO note_tag_cross_ref (noteId, tagId) VALUES (?, ?)",
                                arrayOf(id, tagId)
                            )
                        }
                        personIds.forEach { personId ->
                            writableDatabase.execSQL(
                                "INSERT OR IGNORE INTO note_person_cross_ref (noteId, personId) VALUES (?, ?)",
                                arrayOf(id, personId)
                            )
                        }
                        writableDatabase.setTransactionSuccessful()
                    } finally {
                        writableDatabase.endTransaction()
                    }
                }
                TAGS_CODE -> database.tagDao().updateTag(
                    TagEntity(id, values.getAsString("name") ?: "", values.getAsString("color_hex") ?: "#6200EE")
                )
                BLOCKS_CODE -> database.personBlockDao().updateBlock(
                    PersonBlockEntity(id, values.getAsLong("personId"), values.getAsString("title") ?: "", values.getAsString("content") ?: "", values.getAsInteger("orderIndex") ?: 0)
                )
                FIELDS_CODE -> database.personBlockDao().updateField(
                    PersonBlockFieldEntity(id, values.getAsLong("blockId"), values.getAsString("label") ?: "", values.getAsString("value") ?: "", values.getAsString("actionData"), FieldType.valueOf(values.getAsString("fieldType") ?: FieldType.TEXT.name), values.getAsInteger("orderIndex") ?: 0)
                )
                else -> throw IllegalArgumentException("URI Desconhecida para Update: $uri")
            }
        }
        context?.contentResolver?.notifyChange(uri, null)
        return 1
    }
}