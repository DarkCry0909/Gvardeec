package com.example.guardhr

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Личная карточка сотрудника. Вместо удаления используется признак isActive (архив). */
@Entity(tableName = "employees", indices = [Index(value = ["personnelNumber"], unique = true)])
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val position: String = "",
    val phone: String = "",
    val birthDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val personnelNumber: String = "",
    val hireDay: Long? = null,
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = true
)

/** Документ сотрудника со сроком действия. Прежние записи остаются в истории. */
@Entity(tableName = "docs", indices = [Index("employeeId"), Index("expiryDay")])
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

/** Пост (объект охраны) с требуемой численностью охранников. */
@Entity(tableName = "post")
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String = "",
    val requiredGuards: Int = 1
)

/** Смена: плановое время и фактические отметки о начале и окончании службы (миллисекунды). */
@Entity(tableName = "shift", indices = [Index("startAt"), Index("employeeId"), Index("postId")])
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val postId: Long,
    val startAt: Long,
    val endAt: Long,
    val checkIn: Long? = null,
    val checkOut: Long? = null
)

/** Происшествие привязано к смене, а не к сотруднику напрямую. */
@Entity(tableName = "incident", indices = [Index("shiftId")])
data class Incident(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val occurredAt: Long,
    val category: String,
    val description: String = ""
)

/** Учётная запись: хэш PIN-кода (PBKDF2) с индивидуальной солью и роль. */
@Entity(tableName = "app_user", indices = [Index(value = ["login"], unique = true)])
data class AppUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val login: String,
    val displayName: String,
    val passwordHash: String,
    val salt: String,
    val role: String,
    @ColumnInfo(defaultValue = "0") val blocked: Boolean = false
)

@Dao
interface AppDao {
    // сотрудники и документы
    @Query("SELECT * FROM employees ORDER BY fullName") fun employees(): Flow<List<Employee>>
    @Query("SELECT * FROM docs") fun allDocs(): Flow<List<EmployeeDoc>>
    @Query("SELECT * FROM employees") suspend fun employeesOnce(): List<Employee>
    @Query("SELECT * FROM docs") suspend fun docsOnce(): List<EmployeeDoc>
    @Insert suspend fun insertEmployee(e: Employee): Long
    @Update suspend fun updateEmployee(e: Employee)
    @Delete suspend fun deleteEmployee(e: Employee)
    @Query("DELETE FROM docs WHERE employeeId = :id") suspend fun deleteDocsOf(id: Long)
    @Insert suspend fun insertDoc(d: EmployeeDoc)
    @Delete suspend fun deleteDoc(d: EmployeeDoc)

    // разделы реестра
    @Query("SELECT * FROM section_items WHERE section = :s ORDER BY title") fun rows(s: String): Flow<List<SectionItem>>
    @Query("SELECT * FROM section_items") suspend fun itemsOnce(): List<SectionItem>
    @Insert suspend fun insertRow(r: SectionItem)
    @Delete suspend fun deleteRow(r: SectionItem)

    // посты, смены, происшествия
    @Query("SELECT * FROM post ORDER BY name") fun posts(): Flow<List<Post>>
    @Query("SELECT * FROM post") suspend fun postsOnce(): List<Post>
    @Insert suspend fun insertPost(p: Post): Long
    @Delete suspend fun deletePost(p: Post)
    @Query("SELECT * FROM shift ORDER BY startAt DESC") fun shifts(): Flow<List<Shift>>
    @Query("SELECT * FROM shift") suspend fun shiftsOnce(): List<Shift>
    @Insert suspend fun insertShift(s: Shift): Long
    @Update suspend fun updateShift(s: Shift)
    @Delete suspend fun deleteShift(s: Shift)
    @Query("SELECT * FROM incident ORDER BY occurredAt DESC") fun incidents(): Flow<List<Incident>>
    @Query("SELECT * FROM incident") suspend fun incidentsOnce(): List<Incident>
    @Insert suspend fun insertIncident(i: Incident): Long
    @Delete suspend fun deleteIncident(i: Incident)
    @Query("DELETE FROM incident WHERE shiftId = :shiftId") suspend fun deleteIncidentsOfShift(shiftId: Long)
    @Query("DELETE FROM incident WHERE shiftId IN (SELECT id FROM shift WHERE postId = :postId)") suspend fun deleteIncidentsOfPost(postId: Long)
    @Query("DELETE FROM incident WHERE shiftId IN (SELECT id FROM shift WHERE employeeId = :empId)") suspend fun deleteIncidentsOfEmployee(empId: Long)
    @Query("DELETE FROM shift WHERE postId = :postId") suspend fun deleteShiftsOfPost(postId: Long)
    @Query("DELETE FROM shift WHERE employeeId = :empId") suspend fun deleteShiftsOfEmployee(empId: Long)

    // пользователи
    @Query("SELECT * FROM app_user ORDER BY id") fun users(): Flow<List<AppUser>>
    @Insert suspend fun insertUser(u: AppUser): Long
    @Update suspend fun updateUser(u: AppUser)
    @Delete suspend fun deleteUser(u: AppUser)

    // полная очистка (восстановление из копии)
    @Query("DELETE FROM docs") suspend fun clearDocs()
    @Query("DELETE FROM employees") suspend fun clearEmployees()
    @Query("DELETE FROM section_items") suspend fun clearItems()
    @Query("DELETE FROM post") suspend fun clearPosts()
    @Query("DELETE FROM shift") suspend fun clearShifts()
    @Query("DELETE FROM incident") suspend fun clearIncidents()
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE employees ADD COLUMN birthDay INTEGER")
        db.execSQL("ALTER TABLE docs ADD COLUMN series TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE docs ADD COLUMN issuedBy TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE employees ADD COLUMN personnelNumber TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE employees ADD COLUMN hireDay INTEGER")
        db.execSQL("ALTER TABLE employees ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE employees SET personnelNumber = 'T-' || id")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_employees_personnelNumber ON employees (personnelNumber)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_docs_employeeId ON docs (employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_docs_expiryDay ON docs (expiryDay)")
        db.execSQL("CREATE TABLE IF NOT EXISTS post (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, address TEXT NOT NULL, requiredGuards INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS shift (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, employeeId INTEGER NOT NULL, postId INTEGER NOT NULL, startAt INTEGER NOT NULL, endAt INTEGER NOT NULL, checkIn INTEGER, checkOut INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_startAt ON shift (startAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_employeeId ON shift (employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_postId ON shift (postId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS incident (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shiftId INTEGER NOT NULL, occurredAt INTEGER NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incident_shiftId ON incident (shiftId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS app_user (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, login TEXT NOT NULL, displayName TEXT NOT NULL, passwordHash TEXT NOT NULL, salt TEXT NOT NULL, role TEXT NOT NULL, blocked INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_app_user_login ON app_user (login)")
    }
}

@Database(
    entities = [Employee::class, EmployeeDoc::class, SectionItem::class, Post::class, Shift::class, Incident::class, AppUser::class],
    version = 3, exportSchema = false
)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
