package com.japaneixxx.odin.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.japaneixxx.odin.data.entity.BlockTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockTemplateDao {

    @Query("SELECT * FROM block_templates ORDER BY title ASC")
    fun getAllTemplates(): Flow<List<BlockTemplateEntity>>

    @Query("SELECT * FROM block_templates ORDER BY title ASC")
    suspend fun getAllTemplatesSync(): List<BlockTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplate(template: BlockTemplateEntity): Long
}