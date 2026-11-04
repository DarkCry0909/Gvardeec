package com.example.guardhr

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val position: String = "",
    val phone: String = "",
    val birthDay: Long? = null
)

@Entity(tableName = "docs")
data class EmployeeDoc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val type: String,
    val number: String = "",
    val issueDay: Long? = null,
    val expiryDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val series: String = "",
    @ColumnInfo(defaultValue = "''") val issuedBy: String = ""
)

/** Запись раздела реестра (Предприятия, Адреса, Документы, Охр. объекты и т.д.) */
@Entity(tableName = "section_items")
data class SectionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val section: String,
    val title: String,
    val details: String = ""
)

@Dao
interface AppDao {
    @Query("SELECT * FROM employees ORDER BY fullName") fun employees(): Flow<List<Employee>>
    @Query("SELECT * FROM docs") fun allDocs(): Flow<List<EmployeeDoc>>
    @Query("SELECT * FROM employees") suspend fun employeesOnce(): List<Employee>
    @Query("SELECT * FROM docs") suspend fun docsOnce(): List<EmployeeDoc>
    @Query("SELECT * FROM section_items") suspend fun itemsOnce(): List<SectionItem>
    @Insert suspend fun insertEmployee(e: Employee): Long
    @Delete suspend fun deleteEmployee(e: Employee)
    @Query("DELETE FROM docs WHERE employeeId = :id") suspend fun deleteDocsOf(id: Long)
    @Insert suspend fun insertDoc(d: EmployeeDoc)
    @Delete suspend fun deleteDoc(d: EmployeeDoc)
    @Query("SELECT * FROM section_items WHERE section = :s ORDER BY title") fun rows(s: String): Flow<List<SectionItem>>
    @Insert suspend fun insertRow(r: SectionItem)
    @Delete suspend fun deleteRow(r: SectionItem)
    @Query("DELETE FROM docs") suspend fun clearDocs()
    @Query("DELETE FROM employees") suspend fun clearEmployees()
    @Query("DELETE FROM section_items") suspend fun clearItems()
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE employees ADD COLUMN birthDay INTEGER")
        db.execSQL("ALTER TABLE docs ADD COLUMN series TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE docs ADD COLUMN issuedBy TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Employee::class, EmployeeDoc::class, SectionItem::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null

        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: build(c).also { inst = it }
        }

        /** База шифруется SQLCipher (AES-256); ключ — случайный, хранится под защитой Android Keystore. */
        private fun build(c: Context): AppDb {
            System.loadLibrary("sqlcipher")
            c.deleteDatabase("guard.db") // старая нешифрованная версия (если была)
            val factory = SupportOpenHelperFactory(Keys.dbPassphrase(c))
            return Room.databaseBuilder(c.applicationContext, AppDb::class.java, "guard_enc.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
