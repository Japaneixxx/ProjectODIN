package com.japaneixxx.odin.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.japaneixxx.odin.data.dao.NoteDao
import com.japaneixxx.odin.data.dao.TagDao
import com.japaneixxx.odin.data.entity.NoteEntity
import com.japaneixxx.odin.data.entity.NoteTagCrossRef
import com.japaneixxx.odin.data.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class
    ],
    version = 4,
    exportSchema = false
)
abstract class OdinDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: OdinDatabase? = null

        fun getInstance(context: Context): OdinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OdinDatabase::class.java,
                    "odin_database"
                )
                    .fallbackToDestructiveMigration() // Recria o banco caso o schema tenha mudado
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}