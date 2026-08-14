package com.japaneixxx.odin.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 10,
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

        // MIGRATION 8 -> 9
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE person_block_fields ADD COLUMN actionUri TEXT"
                )
            }
        }
        // MIGRATION 9 -> 10
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Se a coluna antiga existia, renomeamos ou garantimos que actionData existe
                database.execSQL("ALTER TABLE person_block_fields ADD COLUMN actionData TEXT")
            }
        }

        fun getInstance(context: Context): OdinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OdinDatabase::class.java,
                    "odin_database"
                )
                    .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .fallbackToDestructiveMigration() // Recria o banco caso o schema tenha mudado
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}