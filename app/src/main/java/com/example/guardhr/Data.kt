package com.example.guardhr

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val position: String = "",
    val phone: String = ""
)

@Entity(tableName = "docs")
data class EmployeeDoc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val type: String,
    val number: String = "",
    val issueDay: Long? = null,
    val expiryDay: Long? = null
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
    @Insert suspend fun insertEmployee(e: Employee): Long
    @Delete suspend fun deleteEmployee(e: Employee)
    @Query("DELETE FROM docs WHERE employeeId = :id") suspend fun deleteDocsOf(id: Long)
    @Insert suspend fun insertDoc(d: EmployeeDoc)
    @Delete suspend fun deleteDoc(d: EmployeeDoc)
    @Query("SELECT * FROM section_items WHERE section = :s ORDER BY title") fun rows(s: String): Flow<List<SectionItem>>
    @Insert suspend fun insertRow(r: SectionItem)
    @Delete suspend fun deleteRow(r: SectionItem)
}

@Database(entities = [Employee::class, EmployeeDoc::class, SectionItem::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "guard.db").build().also { inst = it }
        }
    }
}
