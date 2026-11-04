package com.example.guardhr

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private val RED = Color(0xFFC62828)
private val ORANGE = Color(0xFFEF6C00)
private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private val SECTIONS = listOf("Предприятия", "Адреса", "Документы", "Сотрудники", "Охр. объекты", "КХО (СС)", "Оружие (СС)", "Учредители")

@Composable
fun App(onSecureChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val empFlow = remember { dao.employees() }
    val docFlow = remember { dao.allDocs() }
    val employees by empFlow.collectAsState(initial = emptyList())
    val docs by docFlow.collectAsState(initial = emptyList())
    val events = remember(employees, docs) { buildEvents(employees, docs) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Календарь") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                icon = { Icon(Icons.Default.Menu, null) }, label = { Text("Реестр") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") })
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                Text("ГВАРДЕЕЦ", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> CalendarScreen(events)
                    1 -> RegistryScreen(dao, employees, docs)
                    else -> SettingsScreen(dao, onSecureChanged)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Календарь

@Composable
fun CalendarScreen(events: List<Event>) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val byDate = remember(events) { events.groupBy { it.date } }
    val shown = events.filter { YearMonth.from(it.date) == month && (selected == null || it.date == selected) }
        .groupBy { it.date }
    val len = month.lengthOfMonth()
    val offset = month.atDay(1).dayOfWeek.value - 1
    val rows = (offset + len + 6) / 7

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { month = month.minusMonths(1); selected = null }) { Icon(Icons.Default.KeyboardArrowLeft, "Назад") }
                Text("${MONTHS[month.monthValue - 1]} ${month.year}", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { month = month.plusMonths(1); selected = null }) { Icon(Icons.Default.KeyboardArrowRight, "Вперёд") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
            }
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0..6) {
                        val n = r * 7 + c - offset + 1
                        if (n in 1..len) {
                            val date = month.atDay(n)
                            DayCell(n, date, byDate[date], selected == date) { selected = if (selected == date) null else date }
                        } else Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Красный — срок истекает, оранжевый — напоминание. Нажмите на день, чтобы отфильтровать список.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            if (shown.isEmpty()) Text("В этом месяце событий нет.")
        }
        shown.forEach { (date, list) ->
            item { Text(date.fmtD(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
            items(list) { e ->
                Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.employee, fontWeight = FontWeight.Bold)
                        Text("${e.doc.title}: ${e.note}", color = if (e.isDeadline) RED else ORANGE)
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.DayCell(day: Int, date: LocalDate, evs: List<Event>?, selected: Boolean, onClick: () -> Unit) {
    val bg = when {
        evs == null -> Color.Transparent
        evs.any { it.isDeadline } -> RED
        else -> ORANGE
    }
    val today = date == LocalDate.now()
    var m = Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape).background(bg)
    if (today) m = m.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
    if (selected) m = m.border(3.dp, Color.Black, CircleShape)
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text("$day", color = if (evs != null) Color.White else Color.Unspecified,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
    }
}

// ---------------------------------------------------------------- Реестр

@Composable
fun RegistryScreen(dao: AppDao, employees: List<Employee>, docs: List<EmployeeDoc>) {
    var idx by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = idx, edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            SECTIONS.forEachIndexed { i, s -> Tab(selected = i == idx, onClick = { idx = i }, text = { Text(s) }) }
        }
        if (SECTIONS[idx] == "Сотрудники") EmployeesScreen(dao, employees, docs) else SectionScreen(dao, SECTIONS[idx])
    }
}

@Composable
fun ConfirmDialog(text: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(onDismissRequest = onNo, title = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onNo) { Text("Отмена") } })
}

@Composable
fun FormDialog(title: String, labels: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val vals = remember { mutableStateListOf<String>().apply { repeat(labels.size) { add("") } } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, l ->
                    OutlinedTextField(vals[i], { vals[i] = it }, label = { Text(l) }, singleLine = labels.size == 3 || i == 0)
                }
            }
        },
        confirmButton = { TextButton(enabled = vals[0].isNotBlank(), onClick = { onSave(vals.map { it.trim() }) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

/** Универсальный раздел: Предприятия, Адреса, Документы, Охр. объекты, КХО, Оружие, Учредители. */
@Composable
fun SectionScreen(dao: AppDao, section: String) {
    val flow = remember(section) { dao.rows(section) }
    val rows by flow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember(section) { mutableStateOf(false) }
    var del by remember(section) { mutableStateOf<SectionItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) item { Text("Записей пока нет. Нажмите «+», чтобы добавить.") }
            items(rows, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.Bold)
                            if (r.details.isNotBlank()) Text(r.details)
                        }
                        IconButton(onClick = { del = r }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Добавить")
        }
    }
    if (adding) FormDialog(section, listOf("Название", "Описание / реквизиты"), { adding = false }) { v ->
        scope.launch { dao.insertRow(SectionItem(section = section, title = v[0], details = v[1])) }
        adding = false
    }
    del?.let { r ->
        ConfirmDialog("Удалить «${r.title}»?", { scope.launch { dao.deleteRow(r) }; del = null }, { del = null })
    }
}

// ---------------------------------------------------------------- Сотрудники

@Composable
fun EmployeesScreen(dao: AppDao, employees: List<Employee>, docs: List<EmployeeDoc>) {
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = employees.find { it.id == openId }
    if (open != null) EmployeeDetail(dao, open, docs.filter { it.employeeId == open.id }) { openId = null }
    else EmployeeList(dao, employees, docs) { openId = it }
}

@Composable
fun EmployeeList(dao: AppDao, employees: List<Employee>, docs: List<EmployeeDoc>, onOpen: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (employees.isEmpty()) item { Text("Сотрудников пока нет. Нажмите «+», чтобы добавить.") }
            items(employees, key = { it.id }) { e ->
                val nearest = docs.filter { it.employeeId == e.id }.mapNotNull { it.expiry }.minOrNull()
                Card(Modifier.fillMaxWidth().clickable { onOpen(e.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.fullName, fontWeight = FontWeight.Bold)
                        if (e.position.isNotBlank()) Text(e.position)
                        if (nearest != null) {
                            val n = daysLeft(nearest)
                            Text("Ближайший срок: ${nearest.fmtD()}" + if (n < 0) " (истёк)" else " (через $n дн.)",
                                color = if (n < 0) RED else if (n <= 60) ORANGE else Color.Unspecified)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Добавить")
        }
    }
    if (adding) EmployeeDialog({ adding = false }) { emp ->
        scope.launch { dao.insertEmployee(emp) }
        adding = false
    }
}

@Composable
fun EmployeeDetail(dao: AppDao, e: Employee, docs: List<EmployeeDoc>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var delDoc by remember { mutableStateOf<EmployeeDoc?>(null) }
    var delEmp by remember { mutableStateOf(false) }
    val rank6 = docs.any { it.docType == DocType.RANK && it.number.trim() == "6" }
    val hasWeapon = docs.any { it.docType == DocType.WEAPON }
    val available = DocType.values().filter { it != DocType.WEAPON || rank6 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                Text(e.fullName, style = MaterialTheme.typography.titleLarge)
            }
        }
        item { Text(listOf(e.position, e.phone).filter { it.isNotBlank() }.joinToString(" · ")) }
        e.birthDay?.let { item { Text("Дата рождения: ${LocalDate.ofEpochDay(it).fmtD()}") } }
        if (rank6 && !hasWeapon) item { Text("6 разряд: нужна справка на оружие — добавьте документ.", color = RED) }
        item { Button(onClick = { adding = true }) { Text("Добавить документ") } }
        items(docs.sortedBy { it.type }, key = { it.id }) { d ->
            val t = d.docType
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontWeight = FontWeight.Bold)
                        if (d.number.isNotBlank() || d.series.isNotBlank()) {
                            Text(
                                if (t == DocType.RANK) "Разряд: ${d.number}"
                                else listOf(if (d.series.isNotBlank()) "Серия ${d.series}" else "", if (d.number.isNotBlank()) "№ ${d.number}" else "")
                                    .filter { it.isNotBlank() }.joinToString(" ")
                            )
                        }
                        if (d.issuedBy.isNotBlank()) Text("Кем выдан: ${d.issuedBy}")
                        d.issueDay?.let { Text("Выдан: ${LocalDate.ofEpochDay(it).fmtD()}") }
                        d.expiry?.let {
                            val n = daysLeft(it)
                            Text("Действует до: ${it.fmtD()}" + if (n < 0) " (истёк)" else " (осталось $n дн.)",
                                color = if (n < 0) RED else if (n <= 60) ORANGE else Color.Unspecified)
                        }
                    }
                    IconButton(onClick = { delDoc = d }) { Icon(Icons.Default.Delete, "Удалить") }
                }
            }
        }
        item { TextButton(onClick = { delEmp = true }) { Text("Удалить сотрудника", color = RED) } }
    }

    if (adding) AddDocDialog(available, { adding = false }) { type, series, number, issuedBy, issue, expiry ->
        scope.launch {
            dao.insertDoc(EmployeeDoc(employeeId = e.id, type = type.name, number = number, issueDay = issue,
                expiryDay = expiry, series = series, issuedBy = issuedBy))
        }
        adding = false
    }
    delDoc?.let { d -> ConfirmDialog("Удалить документ «${d.docType.title}»?", { scope.launch { dao.deleteDoc(d) }; delDoc = null }, { delDoc = null }) }
    if (delEmp) ConfirmDialog("Удалить сотрудника и все его документы?", {
        scope.launch { dao.deleteDocsOf(e.id); dao.deleteEmployee(e) }
        delEmp = false; onBack()
    }, { delEmp = false })
}

@Composable
fun EmployeeDialog(onDismiss: () -> Unit, onSave: (Employee) -> Unit) {
    var last by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var middle by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Новый сотрудник") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(last, { last = it }, singleLine = true, label = { Text("Фамилия") })
                OutlinedTextField(first, { first = it }, singleLine = true, label = { Text("Имя") })
                OutlinedTextField(middle, { middle = it }, singleLine = true, label = { Text("Отчество") })
                OutlinedTextField(birth, { birth = it }, singleLine = true, label = { Text("Дата рождения (дд.мм.гггг)") })
                OutlinedTextField(position, { position = it }, singleLine = true, label = { Text("Должность") })
                OutlinedTextField(phone, { phone = it }, singleLine = true, label = { Text("Телефон") })
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton(enabled = last.isNotBlank() && first.isNotBlank(), onClick = {
                val b = if (birth.isBlank()) null else parseDate(birth)
                if (birth.isNotBlank() && b == null) error = "Неверная дата рождения (дд.мм.гггг)"
                else onSave(
                    Employee(
                        fullName = listOf(last, first, middle).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" "),
                        position = position.trim(), phone = phone.trim(), birthDay = b?.toEpochDay()
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun AddDocDialog(available: List<DocType>, onDismiss: () -> Unit, onSave: (DocType, String, String, String, Long?, Long?) -> Unit) {
    var type by remember { mutableStateOf(available.first()) }
    var series by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var issuedBy by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isRank = type == DocType.RANK

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Новый документ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                available.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { type = t }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(t.title)
                    }
                }
                if (type.hint.isNotBlank()) Text(type.hint, style = MaterialTheme.typography.bodySmall)
                if (!isRank) OutlinedTextField(series, { series = it }, singleLine = true, label = { Text("Серия") })
                OutlinedTextField(number, { number = it }, singleLine = true,
                    label = { Text(if (isRank) "Разряд (4, 5 или 6)" else "Номер") })
                if (!isRank) OutlinedTextField(issuedBy, { issuedBy = it }, label = { Text("Кем выдан") })
                OutlinedTextField(issue, { issue = it }, singleLine = true, label = { Text("Дата выдачи (дд.мм.гггг)") })
                if (type.hasExpiry) OutlinedTextField(expiry, { expiry = it }, singleLine = true,
                    label = { Text(if (type.expiryRequired) "Действует до (дд.мм.гггг)" else "Действует до (если указан)") })
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val i = if (issue.isBlank()) null else parseDate(issue)
                val x = if (type.hasExpiry && expiry.isNotBlank()) parseDate(expiry) else null
                if (issue.isNotBlank() && i == null) error = "Неверная дата выдачи"
                else if (type.hasExpiry && (type.expiryRequired || expiry.isNotBlank()) && x == null) error = "Укажите срок действия в формате дд.мм.гггг"
                else if (isRank && number.trim() !in listOf("4", "5", "6")) error = "Разряд: 4, 5 или 6"
                else onSave(type, if (isRank) "" else series.trim(), number.trim(), if (isRank) "" else issuedBy.trim(), i?.toEpochDay(), x?.toEpochDay())
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
