package com.japaneixxx.odin.data.provider

import android.net.Uri

object OdinDataContract {
    const val AUTHORITY = "com.japaneixxx.odin.core.provider"
    val BASE_URI: Uri = Uri.parse("content://$AUTHORITY")

    val PERSONS_URI: Uri = Uri.withAppendedPath(BASE_URI, "persons")
    val NOTES_URI: Uri = Uri.withAppendedPath(BASE_URI, "notes")
    val TAGS_URI: Uri = Uri.withAppendedPath(BASE_URI, "tags")
    val NOTE_TAGS_URI: Uri = Uri.withAppendedPath(BASE_URI, "note-tags")
    val NOTE_PERSONS_URI: Uri = Uri.withAppendedPath(BASE_URI, "note-persons")
    val BLOCKS_URI: Uri = Uri.withAppendedPath(BASE_URI, "person-blocks")
    val FIELDS_URI: Uri = Uri.withAppendedPath(BASE_URI, "person-block-fields")
    val TEMPLATES_URI: Uri = Uri.withAppendedPath(BASE_URI, "block-templates")

    const val ARG_QUERY = "query"
    const val ARG_PERSON_ID = "personId"
    const val ARG_NOTE_ID = "noteId"
    const val ARG_BLOCK_ID = "blockId"
}