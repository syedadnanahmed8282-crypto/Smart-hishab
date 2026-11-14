package com.smarthisab.app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders")
data class FolderE(
    @PrimaryKey val id: String,
    val name: String,
    val startDay: Long,
    val expectedEndDay: Long? = null,
    val endDay: Long? = null,
    val closed: Boolean = false,
    val incomeGoal: Double? = null,
    val expenseLimit: Double? = null,
    val wageRate: Double = 50.0,
    val dailyTarget: Int? = null,
    val deletedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val dirty: Boolean = true,
    val version: Int = 0,
    /** main = মালের হিসাব, alt = দৈনিক খরচ, prem = আয়-ব্যয় */
    val mode: String = "main",
)

@Entity(tableName = "entries", indices = [Index("folderId", "day")])
data class EntryE(
    @PrimaryKey val id: String,
    val folderId: String,
    val day: Long,
    val quantity: Int,
    val expense: Double,
    val rate: Double,
    val note: String? = null,
    val deletedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val dirty: Boolean = true,
    val version: Int = 0,
    /** ফোল্ডারের ধরন (main/alt/prem) — এন্ট্রিতেও রাখা হয়, যাতে ধরন অনুযায়ী আলাদা থাকে */
    val mode: String = "main",
    /** শুধু alt/prem: "income" বা "expense" */
    val kind: String = "expense",
    val category: String? = null,
    /** শুধু alt/prem: এন্ট্রির টাকার পরিমাণ */
    val amount: Double = 0.0,
)

@Entity(tableName = "kv")
data class KvE(@PrimaryKey val key: String, val value: String)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY startDay DESC")
    fun observe(): Flow<List<FolderE>>
    @Query("SELECT * FROM folders WHERE id = :id") suspend fun get(id: String): FolderE?
    @Query("SELECT * FROM folders WHERE dirty = 1") suspend fun dirty(): List<FolderE>
    @Query("SELECT * FROM folders") suspend fun all(): List<FolderE>
    @Upsert suspend fun upsert(f: FolderE)
    @Query("DELETE FROM folders") suspend fun clear()
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL ORDER BY day DESC")
    fun observe(): Flow<List<EntryE>>
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun get(id: String): EntryE?
    @Query("SELECT * FROM entries WHERE folderId = :f AND day = :d AND deletedAt IS NULL AND mode = 'main' LIMIT 1")
    suspend fun byDay(f: String, d: Long): EntryE?
    @Query("SELECT * FROM entries WHERE folderId = :f AND deletedAt IS NULL") suspend fun ofFolder(f: String): List<EntryE>
    @Query("SELECT * FROM entries WHERE dirty = 1") suspend fun dirty(): List<EntryE>
    @Query("SELECT * FROM entries") suspend fun all(): List<EntryE>
    @Upsert suspend fun upsert(e: EntryE)
    @Query("DELETE FROM entries WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM entries") suspend fun clear()
}

@Dao
interface KvDao {
    @Query("SELECT value FROM kv WHERE `key` = :k") fun observe(k: String): Flow<String?>
    @Query("SELECT value FROM kv WHERE `key` = :k") suspend fun get(k: String): String?
    @Upsert suspend fun put(e: KvE)
    @Query("DELETE FROM kv WHERE `key` = :k") suspend fun remove(k: String)
    @Query("DELETE FROM kv") suspend fun clear()
}

/** সংস্করণ নম্বর যোগ (দুই ফোনের বদল সংঘর্ষ ধরার জন্য) */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE entries ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
    }
}

/** তিন ধরনের হিসাব: আগের সব ফোল্ডার ও এন্ট্রি নিজে থেকে 'main' (মালের হিসাব) হয়ে যায়, কিছু মোছা বা বদলানো হয় না */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN mode TEXT NOT NULL DEFAULT 'main'")
        db.execSQL("ALTER TABLE entries ADD COLUMN mode TEXT NOT NULL DEFAULT 'main'")
        db.execSQL("ALTER TABLE entries ADD COLUMN kind TEXT NOT NULL DEFAULT 'expense'")
        db.execSQL("ALTER TABLE entries ADD COLUMN category TEXT")
        db.execSQL("ALTER TABLE entries ADD COLUMN amount REAL NOT NULL DEFAULT 0")
    }
}

@Database(entities = [FolderE::class, EntryE::class, KvE::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun entries(): EntryDao
    abstract fun kv(): KvDao
}
