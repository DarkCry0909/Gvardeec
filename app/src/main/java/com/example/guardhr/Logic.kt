package com.example.guardhr

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

enum class DocType(val title: String, val hasExpiry: Boolean, val expiryRequired: Boolean, val hint: String) {
    PASSPORT("Паспорт", true, false, ""),
    LICENSE("Лицензия охранника", true, true, "Напоминания: за 5 мес. и за 3,5 мес. до окончания"),
    RANK("Квалификационный разряд", false, false, ""),
    MEDICAL("Медицинская справка", true, true, "Напоминание: за 2 недели до окончания"),
    WEAPON("Справка на оружие", true, true, "Напоминание: за 2 недели до окончания"),
    PERIODIC("Периодическая проверка", true, true, "Напоминания: за 2 мес. и за 1,5 мес. до окончания")
}

/** Обязательные документы: при просрочке сотрудника нельзя назначить на смену. */
val DocType.mandatory: Boolean
    get() = this == DocType.LICENSE || this == DocType.MEDICAL || this == DocType.WEAPON || this == DocType.PERIODIC

/** Даты напоминаний относительно окончания срока. */
fun DocType.reminders(expiry: LocalDate): List<Pair<LocalDate, String>> = when (this) {
    DocType.PERIODIC -> listOf(expiry.minusMonths(2) to "за 2 мес.", expiry.minusMonths(1).minusDays(15) to "за 1,5 мес.")
    DocType.MEDICAL, DocType.WEAPON -> listOf(expiry.minusDays(14) to "за 2 нед.")
    DocType.LICENSE -> listOf(expiry.minusMonths(5) to "за 5 мес.", expiry.minusMonths(3).minusDays(15) to "за 3,5 мес.")
    else -> emptyList()
}

// ------------------------------------------------------------ роли

enum class Role(val title: String) {
    ADMIN("Администратор"),
    HR("Кадровая служба"),
    CHIEF("Начальник охраны")
}

fun roleOf(s: String): Role = Role.values().find { it.name == s } ?: Role.CHIEF
/** Карточки и документы: администратор и кадровая служба; начальник охраны — только просмотр. */
fun Role.canEditCards(): Boolean = this != Role.CHIEF
/** Посты, смены, происшествия: администратор и начальник охраны; кадровая служба — только просмотр. */
fun Role.canPlan(): Boolean = this != Role.HR
fun Role.isAdmin(): Boolean = this == Role.ADMIN

// ------------------------------------------------------------ даты

val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
val DT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")

fun parseDate(s: String): LocalDate? = try { LocalDate.parse(s.trim(), DATE_FMT) } catch (e: Exception) { null }
fun parseTime(s: String): LocalTime? = try { LocalTime.parse(s.trim(), TIME_FMT) } catch (e: Exception) { null }
fun LocalDate.fmtD(): String = format(DATE_FMT)
fun daysLeft(d: LocalDate): Long = ChronoUnit.DAYS.between(LocalDate.now(), d)

fun millisOf(d: LocalDate, t: LocalTime): Long =
    LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun ldt(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
fun fmtDT(ms: Long): String = ldt(ms).format(DT_FMT)
fun fmtT(ms: Long): String = ldt(ms).format(TIME_FMT)
fun dayOf(ms: Long): LocalDate = ldt(ms).toLocalDate()

val EmployeeDoc.docType: DocType get() = DocType.valueOf(type)
val EmployeeDoc.expiry: LocalDate? get() = expiryDay?.let { LocalDate.ofEpochDay(it) }

// ------------------------------------------------------------ состояние документа

enum class DocStatus(val label: String) {
    ACTIVE("Действует"),
    EXPIRING("Истекает"),
    EXPIRED("Просрочен")
}

/** Документ считается истекающим при 0 <= d <= 30 и просроченным при d < 0 (формула 2). */
const val EXPIRING_DAYS = 30

fun statusOf(expiry: LocalDate?, today: LocalDate = LocalDate.now()): DocStatus {
    if (expiry == null) return DocStatus.ACTIVE
    val d = ChronoUnit.DAYS.between(today, expiry)
    return when {
        d < 0 -> DocStatus.EXPIRED
        d <= EXPIRING_DAYS -> DocStatus.EXPIRING
        else -> DocStatus.ACTIVE
    }
}

/**
 * Действующая запись по каждому виду документа сотрудника — с наибольшим сроком (или более новая).
 * Остальные записи — история: они не дают напоминаний и не влияют на допуск.
 */
fun currentDocs(docs: List<EmployeeDoc>): List<EmployeeDoc> =
    docs.groupBy { it.employeeId to it.type }.values.map { g ->
        g.maxWith(compareBy<EmployeeDoc>({ it.expiryDay ?: Long.MAX_VALUE }, { it.id }))
    }

/** Допуск к службе: вид просроченного обязательного документа на дату смены либо null. */
fun blockingDoc(empId: Long, docs: List<EmployeeDoc>, onDate: LocalDate): DocType? =
    currentDocs(docs.filter { it.employeeId == empId })
        .firstOrNull { it.docType.mandatory && it.expiry?.isBefore(onDate) == true }
        ?.docType

// ------------------------------------------------------------ напоминания и события календаря

data class DocAlert(
    val emp: Employee,
    val doc: EmployeeDoc,
    val expiry: LocalDate,
    val days: Long,
    val status: DocStatus
)

/** Документы, требующие внимания: просроченные, истекающие (до 30 дней) и те, по которым напоминание уже наступило. */
fun buildAlerts(emps: List<Employee>, docs: List<EmployeeDoc>): List<DocAlert> {
    val active = emps.filter { it.isActive }.associateBy { it.id }
    val today = LocalDate.now()
    return currentDocs(docs).mapNotNull { d ->
        val e = active[d.employeeId] ?: return@mapNotNull null
        val exp = d.expiry ?: return@mapNotNull null
        val st = statusOf(exp, today)
        val reminded = d.docType.reminders(exp).any { !it.first.isAfter(today) }
        if (st == DocStatus.ACTIVE && !reminded) null
        else DocAlert(e, d, exp, ChronoUnit.DAYS.between(today, exp), st)
    }.sortedBy { it.days }
}

data class Event(
    val date: LocalDate,
    val employee: String,
    val doc: DocType,
    val note: String,
    val isDeadline: Boolean
)

fun buildEvents(emps: List<Employee>, docs: List<EmployeeDoc>): List<Event> {
    val names = emps.filter { it.isActive }.associate { it.id to it.fullName }
    return currentDocs(docs).flatMap { d ->
        val exp = d.expiry ?: return@flatMap emptyList<Event>()
        val name = names[d.employeeId] ?: return@flatMap emptyList<Event>()
        val t = d.docType
        t.reminders(exp).map { (date, label) ->
            Event(date, name, t, "напоминание $label (срок до ${exp.fmtD()})", false)
        } + Event(exp, name, t, "срок действия истекает", true)
    }.sortedBy { it.date }
}

// ------------------------------------------------------------ укомплектованность поста

/** Число охранников, назначенных на пост в указанный день (по дате начала смены). */
fun assignedOnDay(post: Post, shifts: List<Shift>, day: LocalDate): Int =
    shifts.filter { it.postId == post.id && dayOf(it.startAt) == day }.map { it.employeeId }.distinct().size

/** Формула (1): K = Nф / Nп * 100. */
fun completenessPercent(fact: Int, required: Int): Int = if (required <= 0) 100 else fact * 100 / required
