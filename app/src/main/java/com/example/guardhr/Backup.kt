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

private fun JSONArray?.objs(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

/** Учётные записи в копию не входят: они привязаны к устройству. */
suspend fun exportJson(dao: AppDao): String {
    val o = JSONObject()
    o.put("version", 2)
    o.put("employees", JSONArray().also { a ->
        dao.employeesOnce().forEach {
            a.put(
                JSONObject().put("id", it.id).put("fullName", it.fullName).put("position", it.position).put("phone", it.phone)
                    .put("birthDay", it.birthDay ?: JSONObject.NULL).put("personnelNumber", it.personnelNumber)
                    .put("hireDay", it.hireDay ?: JSONObject.NULL).put("isActive", it.isActive)
            )
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
    o.put("posts", JSONArray().also { a ->
        dao.postsOnce().forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("address", it.address).put("requiredGuards", it.requiredGuards))
        }
    })
    o.put("shifts", JSONArray().also { a ->
        dao.shiftsOnce().forEach {
            a.put(
                JSONObject().put("id", it.id).put("employeeId", it.employeeId).put("postId", it.postId)
                    .put("startAt", it.startAt).put("endAt", it.endAt)
                    .put("checkIn", it.checkIn ?: JSONObject.NULL).put("checkOut", it.checkOut ?: JSONObject.NULL)
            )
        }
    })
    o.put("incidents", JSONArray().also { a ->
        dao.incidentsOnce().forEach {
            a.put(
                JSONObject().put("id", it.id).put("shiftId", it.shiftId).put("occurredAt", it.occurredAt)
                    .put("category", it.category).put("description", it.description)
            )
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
    val emps = o.optJSONArray("employees").objs().map {
        val id = it.getLong("id")
        Employee(
            id = id, fullName = it.getString("fullName"), position = it.optString("position", ""),
            phone = it.optString("phone", ""), birthDay = it.longOrNull("birthDay"),
            personnelNumber = it.optString("personnelNumber", "").ifBlank { "T-$id" },
            hireDay = it.longOrNull("hireDay"), isActive = it.optBoolean("isActive", true)
        )
    }
    val docs = o.optJSONArray("docs").objs().map {
        EmployeeDoc(
            it.getLong("id"), it.getLong("employeeId"), it.getString("type"), it.optString("number", ""),
            it.longOrNull("issueDay"), it.longOrNull("expiryDay"),
            it.optString("series", ""), it.optString("issuedBy", "")
        )
    }.filter { d -> DocType.values().any { it.name == d.type } }
    val items = o.optJSONArray("items").objs().map {
        SectionItem(it.getLong("id"), it.getString("section"), it.getString("title"), it.optString("details", ""))
    }
    val posts = o.optJSONArray("posts").objs().map {
        Post(it.getLong("id"), it.getString("name"), it.optString("address", ""), it.optInt("requiredGuards", 1))
    }
    val shifts = o.optJSONArray("shifts").objs().map {
        Shift(it.getLong("id"), it.getLong("employeeId"), it.getLong("postId"), it.getLong("startAt"), it.getLong("endAt"),
            it.longOrNull("checkIn"), it.longOrNull("checkOut"))
    }
    val incidents = o.optJSONArray("incidents").objs().map {
        Incident(it.getLong("id"), it.getLong("shiftId"), it.getLong("occurredAt"), it.getString("category"), it.optString("description", ""))
    }

    val db = AppDb.get(ctx)
    val dao = db.dao()
    db.withTransaction {
        if (replace) {
            dao.clearIncidents(); dao.clearShifts(); dao.clearDocs(); dao.clearPosts(); dao.clearEmployees(); dao.clearItems()
            emps.forEach { dao.insertEmployee(it) }
            docs.forEach { dao.insertDoc(it) }
            items.forEach { dao.insertRow(it) }
            posts.forEach { dao.insertPost(it) }
            shifts.forEach { dao.insertShift(it) }
            incidents.forEach { dao.insertIncident(it) }
        } else {
            val oldEmps = dao.employeesOnce()
            val oldDocs = dao.docsOnce()
            val oldItems = dao.itemsOnce()
            val oldPosts = dao.postsOnce()
            val oldShifts = dao.shiftsOnce()
            val oldIncidents = dao.incidentsOnce()

            val usedNumbers = oldEmps.map { it.personnelNumber }.toMutableSet()
            val empMap = mutableMapOf<Long, Long>()
            emps.forEach { e ->
                val same = oldEmps.find { it.personnelNumber == e.personnelNumber && it.fullName == e.fullName }
                    ?: oldEmps.find { it.fullName == e.fullName && it.birthDay == e.birthDay }
                if (same != null) empMap[e.id] = same.id
                else {
                    var num = e.personnelNumber
                    while (num in usedNumbers) num += "-имп"
                    usedNumbers.add(num)
                    empMap[e.id] = dao.insertEmployee(e.copy(id = 0, personnelNumber = num))
                }
            }
            docs.forEach { d ->
                val nid = empMap[d.employeeId] ?: return@forEach
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
            val postMap = mutableMapOf<Long, Long>()
            posts.forEach { p ->
                val same = oldPosts.find { it.name == p.name && it.address == p.address }
                postMap[p.id] = same?.id ?: dao.insertPost(p.copy(id = 0))
            }
            val shiftMap = mutableMapOf<Long, Long>()
            shifts.forEach { s ->
                val ne = empMap[s.employeeId] ?: return@forEach
                val np = postMap[s.postId] ?: return@forEach
                val same = oldShifts.find { it.employeeId == ne && it.postId == np && it.startAt == s.startAt }
                shiftMap[s.id] = same?.id ?: dao.insertShift(s.copy(id = 0, employeeId = ne, postId = np))
            }
            incidents.forEach { i ->
                val ns = shiftMap[i.shiftId] ?: return@forEach
                val exists = oldIncidents.any { it.shiftId == ns && it.occurredAt == i.occurredAt && it.description == i.description }
                if (!exists) dao.insertIncident(i.copy(id = 0, shiftId = ns))
            }
        }
    }
}
