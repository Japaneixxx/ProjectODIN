package com.japaneixxx.odin.core.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import androidx.room.Room
import com.japaneixxx.odin.data.database.OdinDatabase

class OdinContentProvider : ContentProvider() {

    // Autoridade única no sistema operativo
    private val AUTHORITY = "com.japaneixxx.odin.core.provider"
    private val PERSONS_CODE = 100

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "persons", PERSONS_CODE)
    }

    private lateinit var database: OdinDatabase

    override fun onCreate(): Boolean {
        val context = context ?: return false

        // O Core é o único hospedeiro real da base de dados!
        database = Room.databaseBuilder(
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
        return when (uriMatcher.match(uri)) {
            PERSONS_CODE -> {
                // A query de texto vem nos selectionArgs (ex: ["João"])
                val searchQuery = selectionArgs?.firstOrNull() ?: ""

                // Repassamos para o DAO que devolve o Cursor
                val cursor = database.personDao().searchForMentionCursor(searchQuery)

                // Avisar o sistema que este cursor pertence a esta URI (para atualização reativa no futuro)
                cursor.setNotificationUri(context?.contentResolver, uri)
                cursor
            }
            else -> throw IllegalArgumentException("URI Desconhecida: $uri")
        }
    }

    // Como os clientes vão inserir os dados através dos DAOs locais usando o Room
    // os métodos de insert, update e delete no Provider podem apenas lançar exceções por agora,
    // ou devolver null. Para esta fase de menções, precisamos apenas da leitura (query).
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}