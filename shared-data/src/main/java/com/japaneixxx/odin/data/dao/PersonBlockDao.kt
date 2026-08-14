package com.japaneixxx.odin.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.japaneixxx.odin.data.entity.PersonBlockEntity
import com.japaneixxx.odin.data.entity.PersonBlockFieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonBlockDao {

    // --- BLOCOS ---
    @Query("SELECT * FROM person_blocks WHERE personId = :personId ORDER BY orderIndex ASC, id ASC")
    fun getBlocksForPerson(personId: Long): Flow<List<PersonBlockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: PersonBlockEntity): Long

    @Update
    suspend fun updateBlock(block: PersonBlockEntity)

    @Delete
    suspend fun deleteBlock(block: PersonBlockEntity)

    // --- SUBCAMPOS DOS BLOCOS ---
    @Query("SELECT * FROM person_block_fields WHERE blockId = :blockId ORDER BY orderIndex ASC, id ASC")
    fun getFieldsForBlock(blockId: Long): Flow<List<PersonBlockFieldEntity>>

    @Query("SELECT * FROM person_block_fields WHERE blockId = :blockId ORDER BY orderIndex ASC, id ASC")
    suspend fun getFieldsForBlockSync(blockId: Long): List<PersonBlockFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: PersonBlockFieldEntity): Long

    @Update
    suspend fun updateField(field: PersonBlockFieldEntity)

    @Delete
    suspend fun deleteField(field: PersonBlockFieldEntity)
}