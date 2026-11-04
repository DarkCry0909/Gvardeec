package com.example.guardhr

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Файл копии: "GVB1" + соль(16) + IV(12) + AES-256-GCM(JSON). Ключ — PBKDF2 от пароля копии. */
object BackupCrypto {
    private val MAGIC = "GVB1".toByteArray()

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(pbkdf2(password, salt), "AES"), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    /** null — неверный пароль или повреждённый файл. */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray? {
        if (data.size < 4 + 16 + 12 + 16) return null
        if (!data.copyOfRange(0, 4).contentEquals(MAGIC)) return null
        return try {
            val salt = data.copyOfRange(4, 20)
            val iv = data.copyOfRange(20, 32)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(pbkdf2(password, salt), "AES"), GCMParameterSpec(128, iv))
            c.doFinal(data, 32, data.size - 32)
        } catch (e: Exception) {
            null
        }
    }
}

suspend fun exportJson(dao: AppDao): String {
    val o = JSONObject()
    o.put("version", 1)
    o.put("employees", JSONArray().also { a ->
        dao.employeesOnce().forEach {
            a.put(JSONObject().put("id", it.id).put("fullName", it.fullName).put("position", it.position).put("phone", it.phone)
                    .put("birthDay", it.birthDay ?: JSONObject.NULL))
        }
    })
    o.put("docs", JSONArray().also { a ->
        dao.docsOnce().forEach {
            a.put(
                JSONObject().put("id", it.id).put("employeeId", it.employeeId).put("type", it.type)
                    .put("number", it.number).put("series", it.series).put("issuedBy", it.issuedBy)
                    .put("issueDay", it.issueDay ?: JSONObject.NULL)
                    .put("expiryDay", it.expiryDay ?: JSONObject.NULL)
            )
        }
    })
    o.put("items", JSONArray().also { a ->
        dao.itemsOnce().forEach {
            a.put(JSONObject().put("id", it.id).put("section", it.section).put("title", it.title).put("details", it.details))
        }
    })
    return o.toString()
}

/**
 * replace = true — данные полностью заменяются содержимым копии;
 * replace = false — данные из копии добавляются к текущим (совпадающие записи не дублируются).
 * Сначала всё разбирается, и только потом база меняется.
 */
suspend fun importJson(ctx: Context, json: String, replace: Boolean) {
    val o = JSONObject(json)
    fun JSONArray.objs() = (0 until length()).map { getJSONObject(it) }
    val emps = o.getJSONArray("employees").objs().map {
        Employee(it.getLong("id"), it.getString("fullName"), it.getString("position"), it.getString("phone"),
            if (it.isNull("birthDay")) null else it.getLong("birthDay"))
    }
    val docs = o.getJSONArray("docs").objs().map {
        EmployeeDoc(
            it.getLong("id"), it.getLong("employeeId"), it.getString("type"), it.getString("number"),
            if (it.isNull("issueDay")) null else it.getLong("issueDay"),
            if (it.isNull("expiryDay")) null else it.getLong("expiryDay"),
            it.optString("series", ""), it.optString("issuedBy", "")
        )
    }.filter { d -> DocType.values().any { it.name == d.type } }
    val items = o.getJSONArray("items").objs().map {
        SectionItem(it.getLong("id"), it.getString("section"), it.getString("title"), it.getString("details"))
    }
    val db = AppDb.get(ctx)
    val dao = db.dao()
    db.withTransaction {
        if (replace) {
            dao.clearDocs(); dao.clearEmployees(); dao.clearItems()
            emps.forEach { dao.insertEmployee(it) }
            docs.forEach { dao.insertDoc(it) }
            items.forEach { dao.insertRow(it) }
        } else {
            val oldEmps = dao.employeesOnce()
            val oldDocs = dao.docsOnce()
            val oldItems = dao.itemsOnce()
            val idMap = mutableMapOf<Long, Long>()
            emps.forEach { e ->
                val same = oldEmps.find { it.fullName == e.fullName && it.birthDay == e.birthDay }
                idMap[e.id] = same?.id ?: dao.insertEmployee(e.copy(id = 0))
            }
            docs.forEach { d ->
                val nid = idMap[d.employeeId] ?: return@forEach
                val exists = oldDocs.any {
                    it.employeeId == nid && it.type == d.type && it.series == d.series &&
                        it.number == d.number && it.expiryDay == d.expiryDay
                }
                if (!exists) dao.insertDoc(d.copy(id = 0, employeeId = nid))
            }
            items.forEach { r ->
                val exists = oldItems.any { it.section == r.section && it.title == r.title && it.details == r.details }
                if (!exists) dao.insertRow(r.copy(id = 0))
            }
        }
    }
}
