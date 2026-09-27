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
        BlockTemplateEntity::class,
        NotePersonCrossRef::class
    ],
    version = 12,
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
                database.execSQL("ALTER TABLE person_block_fields ADD COLUMN actionUri TEXT")
            }
        }

        // MIGRATION 9 -> 10
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_person_cross_ref` (
                        `noteId` INTEGER NOT NULL, 
                        `personId` INTEGER NOT NULL, 
                        PRIMARY KEY(`noteId`, `personId`)
                    )
                    """.trimIndent()
                )
            }
        }

        // MIGRATION 10 -> 11
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Reservado para futuras migrações se necessário
            }
        }

        // MIGRATION 11 -> 12
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tag_cross_ref_tagId` ON `note_tag_cross_ref` (`tagId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_note_person_cross_ref_personId` ON `note_person_cross_ref` (`personId`)")
            }
        }

        fun getInstance(context: Context): OdinDatabase {
            return INSTANCE ?: synchronized(this) {

                // 🌟 ARQUITETURA LIMPA: Room usa o armazenamento padrão e seguro do próprio app
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OdinDatabase::class.java,
                    "odin_database.db" // O Android decide o caminho interno seguro (/data/data/.../databases/)
                )
                    .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .addMigrations(MIGRATION_10_11)
                    .addMigrations(MIGRATION_11_12)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}