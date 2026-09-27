package com.japaneixxx.odin.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.japaneixxx.odin.data.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE id = :id LIMIT 1")
    suspend fun getPersonById(id: Long): PersonEntity?

    @Query("SELECT * FROM persons WHERE name LIKE '%' || :query || '%' OR nickname LIKE '%' || :query || '%'")
    fun searchPersons(query: String): Flow<List<PersonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    // Busca rápida para o sistema de menções (@)
    @Query("""
        SELECT * FROM persons 
        WHERE (:searchQuery = '' OR name LIKE '%' || :searchQuery || '%' OR nickname LIKE '%' || :searchQuery || '%')
        ORDER BY name ASC LIMIT 10
    """)
    suspend fun searchForMention(searchQuery: String): List<PersonEntity>

    // Função auxiliar para listar todos caso a busca venha vazia
    @Query("SELECT * FROM persons ORDER BY name ASC LIMIT 10")
    suspend fun getAllPersonsList(): List<PersonEntity>

    @Query("""
        SELECT id, name, nickname, photoPath 
        FROM persons 
        WHERE (:searchQuery = '' OR name LIKE '%' || :searchQuery || '%' OR nickname LIKE '%' || :searchQuery || '%')
        ORDER BY name ASC LIMIT 10
    """)
    fun searchForMentionCursor(searchQuery: String): android.database.Cursor

    @Query("""
    SELECT * FROM persons 
    WHERE (:searchQuery = '' 
       OR name LIKE '%' || :searchQuery || '%' 
       OR nickname LIKE '%' || :searchQuery || '%' 
       OR relationship LIKE '%' || :searchQuery || '%')
    ORDER BY id ASC
""")
    fun getPersonsCursor(searchQuery: String): android.database.Cursor
}