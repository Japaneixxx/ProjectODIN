package com.japaneixxx.odin.data.dao

import androidx.room.*
import com.japaneixxx.odin.data.entity.BlockTemplateFieldEntity

@Dao
interface BlockTemplateFieldDao {
    @Query("SELECT * FROM block_template_fields WHERE templateId = :templateId ORDER BY orderIndex ASC, id ASC")
    suspend fun getForTemplate(templateId: Long): List<BlockTemplateFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(field: BlockTemplateFieldEntity): Long

    @Delete
    suspend fun delete(field: BlockTemplateFieldEntity)
}