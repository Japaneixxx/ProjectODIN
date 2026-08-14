package com.japaneixxx.odin.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.japaneixxx.odin.data.dao.*
import com.japaneixxx.odin.data.entity.*

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        PersonEntity::class,
        PersonBlockEntity::class,
        PersonBlockFieldEntity::class,
        BlockTemplateEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class OdinDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun personDao(): PersonDao
    abstract fun personBlockDao(): PersonBlockDao
    abstract fun blockTemplateDao(): BlockTemplateDao

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