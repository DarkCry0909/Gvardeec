package com.example.guardhr

import java.time.LocalDate

private fun esc(s: String): String {
    var t = s.replace("\"", "\"\"")
    if (t.startsWith("=") || t.startsWith("@")) t = "'$t" // защита от формул в Excel
    return if (t.contains(';') || t.contains('"') || t.contains('\n') || t.contains('\r')) "\"$t\"" else t
}

private fun row(vararg c: String): String = c.joinToString(";") { esc(it) } + "\r\n"
private fun Long?.dateStr(): String = this?.let { LocalDate.ofEpochDay(it).fmtD() } ?: ""

/** CSV: разделитель «;», кодировка UTF-8 с BOM — открывается в Excel. kind: emp | docs | shifts */
suspend fun buildCsv(kind: String, dao: AppDao): String {
    val sb = StringBuilder("\uFEFF")
    val emps = dao.employeesOnce()
    when (kind) {
        "emp" -> {
            sb.append(row("Табельный номер", "ФИО", "Дата рождения", "Должность", "Телефон", "Дата приёма", "Статус"))
            emps.sortedBy { it.fullName }.forEach { e ->
                sb.append(row(e.personnelNumber, e.fullName, e.birthDay.dateStr(), e.position, e.phone, e.hireDay.dateStr(),
                    if (e.isActive) "Действует" else "В архиве"))
            }
        }
        "docs" -> {
            val docs = dao.docsOnce()
            val byId = emps.associateBy { it.id }
            val currentIds = currentDocs(docs).map { it.id }.toSet()
            sb.append(row("Табельный номер", "ФИО", "Документ", "Серия", "Номер", "Кем выдан", "Дата выдачи",
                "Действует до", "Остаток, дн.", "Состояние", "Запись"))
            docs.sortedWith(compareBy({ byId[it.employeeId]?.fullName ?: "" }, { it.type }, { it.expiryDay ?: Long.MAX_VALUE }))
                .forEach { d ->
                    val e = byId[d.employeeId] ?: return@forEach
                    val exp = d.expiry
                    val cur = d.id in currentIds
                    sb.append(row(e.personnelNumber, e.fullName, d.docType.title, d.series, d.number, d.issuedBy,
                        d.issueDay.dateStr(), d.expiryDay.dateStr(),
                        if (exp != null && cur) daysLeft(exp).toString() else "",
                        if (cur && exp != null) statusOf(exp).label else if (cur) "Бессрочно" else "",
                        if (cur) "Действующая" else "История"))
                }
        }
        "shifts" -> {
            val posts = dao.postsOnce().associateBy { it.id }
            sb.append(row("Дата", "Начало", "Окончание", "Пост", "Сотрудник", "Факт. начало", "Факт. окончание"))
            val byId = emps.associateBy { it.id }
            dao.shiftsOnce().sortedBy { it.startAt }.forEach { s ->
                sb.append(row(dayOf(s.startAt).fmtD(), fmtT(s.startAt), fmtT(s.endAt), posts[s.postId]?.name ?: "",
                    byId[s.employeeId]?.fullName ?: "", s.checkIn?.let { fmtT(it) } ?: "", s.checkOut?.let { fmtT(it) } ?: ""))
            }
        }
    }
    return sb.toString()
}
