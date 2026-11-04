package com.example.guardhr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

enum class DocType(val title: String, val hasExpiry: Boolean, val hint: String) {
    PASSPORT("Паспорт", false, ""),
    LICENSE("Лицензия охранника", true, "Напоминания: за 5 мес. и за 3,5 мес. до окончания"),
    RANK("Квалификационный разряд", false, ""),
    MEDICAL("Медицинская справка", true, "Напоминание: за 2 недели до окончания"),
    WEAPON("Справка на оружие", true, "Напоминание: за 2 недели до окончания"),
    PERIODIC("Периодическая проверка", true, "Напоминания: за 2 мес. и за 1,5 мес. до окончания")
}

/** Даты напоминаний относительно окончания срока. */
fun DocType.reminders(expiry: LocalDate): List<Pair<LocalDate, String>> = when (this) {
    DocType.PERIODIC -> listOf(expiry.minusMonths(2) to "за 2 мес.", expiry.minusMonths(1).minusDays(15) to "за 1,5 мес.")
    DocType.MEDICAL, DocType.WEAPON -> listOf(expiry.minusDays(14) to "за 2 нед.")
    DocType.LICENSE -> listOf(expiry.minusMonths(5) to "за 5 мес.", expiry.minusMonths(3).minusDays(15) to "за 3,5 мес.")
    else -> emptyList()
}

val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
fun parseDate(s: String): LocalDate? = try { LocalDate.parse(s.trim(), DATE_FMT) } catch (e: Exception) { null }
fun LocalDate.fmtD(): String = format(DATE_FMT)
fun daysLeft(d: LocalDate): Long = ChronoUnit.DAYS.between(LocalDate.now(), d)

val EmployeeDoc.docType: DocType get() = DocType.valueOf(type)
val EmployeeDoc.expiry: LocalDate? get() = expiryDay?.let { LocalDate.ofEpochDay(it) }

data class Event(
    val date: LocalDate,
    val employee: String,
    val doc: DocType,
    val note: String,
    val isDeadline: Boolean
)

fun buildEvents(emps: List<Employee>, docs: List<EmployeeDoc>): List<Event> {
    val names = emps.associate { it.id to it.fullName }
    return docs.flatMap { d ->
        val exp = d.expiry ?: return@flatMap emptyList<Event>()
        val name = names[d.employeeId] ?: return@flatMap emptyList<Event>()
        val t = d.docType
        t.reminders(exp).map { (date, label) ->
            Event(date, name, t, "напоминание $label (срок до ${exp.fmtD()})", false)
        } + Event(exp, name, t, "срок действия истекает", true)
    }.sortedBy { it.date }
}
