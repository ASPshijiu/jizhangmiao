package com.android.jizhangmiao.ledger.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LedgerEntryEntity::class,
        LedgerTemplateEntity::class,
        PendingLedgerImportEntity::class,
        LedgerMetadataEntity::class,
        AutoImportHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(LedgerConverters::class)
internal abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        private const val DATABASE_NAME = "ledger_store.db"

        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { database ->
                    instance = database
                }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE ledger_metadata ADD COLUMN automationRulesJson TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE ledger_templates ADD COLUMN planType TEXT NOT NULL DEFAULT 'STANDARD'"
                )
                db.execSQL(
                    "ALTER TABLE ledger_templates ADD COLUMN installmentTotalPeriods INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE ledger_templates ADD COLUMN installmentPaidPeriods INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
