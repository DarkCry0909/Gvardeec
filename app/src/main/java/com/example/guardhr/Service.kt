package com.example.guardhr

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

val INCIDENT_CATEGORIES = listOf(
    "Нарушение пропускного режима", "Кража / хищение", "Пожар / ЧС", "Конфликт / нарушение порядка",
    "Нарушение со стороны сотрудника", "Неисправность оборудования", "Прочее"
)

/** Раздел «Служба»: смены, посты и происшествия. */
@Composable
fun ServiceScreen(dao: AppDao, role: Role, employees: List<Employee>, docs: List<EmployeeDoc>) {
    val postFlow = remember { dao.posts() }
    val shiftFlow = remember { dao.shifts() }
    val incFlow = remember { dao.incidents() }
    val posts by postFlow.collectAsState(initial = emptyList())
    val shifts by shiftFlow.collectAsState(initial = emptyList())
    val incidents by incFlow.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val canPlan = role.canPlan()

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            listOf("Смены", "Посты", "Происшествия").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> ShiftsScreen(dao, canPlan, posts, shifts, employees, docs)
            1 -> PostsScreen(dao, canPlan, posts, shifts)
            else -> IncidentsScreen(dao, canPlan, posts, shifts, employees, incidents)
        }
    }
}

// ---------------------------------------------------------------- Посты

@Composable
fun PostsScreen(dao: AppDao, canPlan: Boolean, posts: List<Post>, shifts: List<Shift>) {
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf(LocalDate.now()) }
    var adding by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Post?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { day = day.minusDays(1) }) { Icon(Icons.Default.KeyboardArrowLeft, "Предыдущий день") }
                    Text("Укомплектованность на ${day.fmtD()}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { day = day.plusDays(1) }) { Icon(Icons.Default.KeyboardArrowRight, "Следующий день") }
                }
            }
            if (posts.isEmpty()) item { Text(if (canPlan) "Постов пока нет. Нажмите «+», чтобы добавить." else "Постов пока нет.") }
            items(posts, key = { it.id }) { p ->
                val fact = assignedOnDay(p, shifts, day)
                val k = completenessPercent(fact, p.requiredGuards)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            if (p.address.isNotBlank()) Text(p.address)
                            Text("Назначено: $fact из ${p.requiredGuards} · $k %")
                            if (k < 100) StatusLine(DocStatus.EXPIRED, "Недокомплект") else StatusLine(DocStatus.ACTIVE, "Укомплектован")
                        }
                        if (canPlan) IconButton(onClick = { del = p }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        if (canPlan) FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Добавить")
        }
    }
    if (adding) PostDialog({ adding = false }) { post ->
        scope.launch { dao.insertPost(post) }
        adding = false
    }
    del?.let { p ->
        ConfirmDialog("Удалить пост «${p.name}» вместе со сменами и происшествиями?", {
            scope.launch {
                dao.deleteIncidentsOfPost(p.id)
                dao.deleteShiftsOfPost(p.id)
                dao.deletePost(p)
            }
            del = null
        }, { del = null })
    }
}

@Composable
fun PostDialog(onDismiss: () -> Unit, onSave: (Post) -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var req by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Новый пост") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Наименование поста или объекта") })
                OutlinedTextField(address, { address = it }, label = { Text("Адрес объекта") })
                OutlinedTextField(req, { req = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                    label = { Text("Требуется охранников на смену") })
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = req.toIntOrNull()
                if (name.isBlank()) error = "Укажите наименование поста"
                else if (n == null || n < 1) error = "Численность: от 1 до 99"
                else onSave(Post(name = name.trim(), address = address.trim(), requiredGuards = n))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

// ---------------------------------------------------------------- Смены

@Composable
fun ShiftsScreen(
    dao: AppDao, canPlan: Boolean, posts: List<Post>, shifts: List<Shift>,
    employees: List<Employee>, docs: List<EmployeeDoc>
) {
    val scope = rememberCoroutineScope()
    val empName = remember(employees) { employees.associate { it.id to it.fullName } }
    val postName = remember(posts) { posts.associate { it.id to it.name } }
    val activeEmps = remember(employees) { employees.filter { it.isActive } }
    var adding by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Shift?>(null) }
    var message by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (message.isNotEmpty()) item { Text(message, color = RED) }
            if (shifts.isEmpty()) item {
                Text(if (canPlan) "Смен пока нет. Сначала добавьте пост, затем нажмите «+»." else "Смен пока нет.")
            }
            items(shifts, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${fmtDT(s.startAt)} – ${fmtT(s.endAt)}", fontWeight = FontWeight.Bold)
                                Text(postName[s.postId] ?: "Пост удалён")
                                Text(empName[s.employeeId] ?: "Сотрудник удалён")
                            }
                            if (canPlan) IconButton(onClick = { del = s }) { Icon(Icons.Default.Delete, "Удалить") }
                        }
                        val ci = s.checkIn
                        val co = s.checkOut
                        when {
                            co != null && ci != null -> StatusLine(DocStatus.ACTIVE, "Завершена: факт ${fmtT(ci)} – ${fmtT(co)}")
                            ci != null -> StatusLine(DocStatus.EXPIRING, "На службе с ${fmtT(ci)}")
                            else -> Text("Запланирована")
                        }
                        if (canPlan && co == null) {
                            if (ci == null) Button(onClick = {
                                val blocked = blockingDoc(s.employeeId, docs, LocalDate.now())
                                if (blocked != null) message = "У сотрудника просрочен документ: ${blocked.title}"
                                else { message = ""; scope.launch { dao.updateShift(s.copy(checkIn = System.currentTimeMillis())) } }
                            }) { Text("Начать службу") }
                            else Button(onClick = { scope.launch { dao.updateShift(s.copy(checkOut = System.currentTimeMillis())) } }) {
                                Text("Завершить службу")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        if (canPlan) FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Добавить")
        }
    }
    if (adding) {
        if (posts.isEmpty() || activeEmps.isEmpty()) {
            AlertDialog(onDismissRequest = { adding = false }, title = { Text("Нельзя назначить смену") },
                text = { Text("Для смены нужны хотя бы один действующий сотрудник и один пост.") },
                confirmButton = { TextButton(onClick = { adding = false }) { Text("Понятно") } })
        } else ShiftDialog(activeEmps, posts,
            validate = { e, date ->
                blockingDoc(e.id, docs, date)?.let { "У сотрудника просрочен документ: ${it.title}" }
            },
            onDismiss = { adding = false }
        ) { e, p, start, end ->
            scope.launch { dao.insertShift(Shift(employeeId = e.id, postId = p.id, startAt = start, endAt = end)) }
            message = ""
            adding = false
        }
    }
    del?.let { s ->
        ConfirmDialog("Удалить смену ${fmtDT(s.startAt)}?", {
            scope.launch { dao.deleteIncidentsOfShift(s.id); dao.deleteShift(s) }
            del = null
        }, { del = null })
    }
}

@Composable
fun ShiftDialog(
    emps: List<Employee>, posts: List<Post>, validate: (Employee, LocalDate) -> String?,
    onDismiss: () -> Unit, onSave: (Employee, Post, Long, Long) -> Unit
) {
    var ei by remember { mutableIntStateOf(-1) }
    var pi by remember { mutableIntStateOf(-1) }
    var date by remember { mutableStateOf(LocalDate.now().fmtD()) }
    var time by remember { mutableStateOf("08:00") }
    var hours by remember { mutableStateOf("12") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Назначение на смену") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField("Сотрудник", emps.map { it.fullName }, ei) { ei = it }
                PickerField("Пост", posts.map { it.name }, pi) { pi = it }
                OutlinedTextField(date, { date = it }, singleLine = true, label = { Text("Дата (дд.мм.гггг)") })
                OutlinedTextField(time, { time = it }, singleLine = true, label = { Text("Начало (чч:мм)") })
                OutlinedTextField(hours, { hours = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                    label = { Text("Продолжительность, часов") })
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = parseDate(date)
                val t = parseTime(time)
                val h = hours.toIntOrNull()
                if (ei < 0 || pi < 0) error = "Выберите сотрудника и пост"
                else if (d == null) error = "Введите дату в формате дд.мм.гггг"
                else if (t == null) error = "Введите время в формате чч:мм"
                else if (h == null || h < 1 || h > 24) error = "Продолжительность: от 1 до 24 часов"
                else {
                    val msg = validate(emps[ei], d)
                    if (msg != null) error = msg
                    else {
                        val start = millisOf(d, t)
                        onSave(emps[ei], posts[pi], start, start + h * 3_600_000L)
                    }
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

// ---------------------------------------------------------------- Происшествия

@Composable
fun IncidentsScreen(
    dao: AppDao, canPlan: Boolean, posts: List<Post>, shifts: List<Shift>,
    employees: List<Employee>, incidents: List<Incident>
) {
    val scope = rememberCoroutineScope()
    val empName = remember(employees) { employees.associate { it.id to it.fullName } }
    val postName = remember(posts) { posts.associate { it.id to it.name } }
    val shiftById = remember(shifts) { shifts.associateBy { it.id } }
    val shiftLabel: (Shift) -> String = { s ->
        "${fmtDT(s.startAt)} · ${postName[s.postId] ?: "?"} · ${empName[s.employeeId] ?: "?"}"
    }
    var adding by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Incident?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (incidents.isEmpty()) item { Text(if (canPlan) "Происшествий не зарегистрировано. Нажмите «+», чтобы добавить запись." else "Происшествий не зарегистрировано.") }
            items(incidents, key = { it.id }) { i ->
                val s = shiftById[i.shiftId]
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${fmtDT(i.occurredAt)} · ${i.category}", fontWeight = FontWeight.Bold)
                            if (s != null) Text("${postName[s.postId] ?: "Пост удалён"} · ${empName[s.employeeId] ?: "Сотрудник удалён"}")
                            if (i.description.isNotBlank()) Text(i.description)
                        }
                        if (canPlan) IconButton(onClick = { del = i }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        if (canPlan) FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Добавить")
        }
    }
    if (adding) {
        if (shifts.isEmpty()) {
            AlertDialog(onDismissRequest = { adding = false }, title = { Text("Нет смен") },
                text = { Text("Происшествие регистрируется в рамках смены. Сначала добавьте смену.") },
                confirmButton = { TextButton(onClick = { adding = false }) { Text("Понятно") } })
        } else IncidentDialog(shifts, shiftLabel, { adding = false }) { inc ->
            scope.launch { dao.insertIncident(inc) }
            adding = false
        }
    }
    del?.let { i ->
        ConfirmDialog("Удалить запись о происшествии?", { scope.launch { dao.deleteIncident(i) }; del = null }, { del = null })
    }
}

@Composable
fun IncidentDialog(shifts: List<Shift>, label: (Shift) -> String, onDismiss: () -> Unit, onSave: (Incident) -> Unit) {
    val now = remember { LocalDateTime.now() }
    var si by remember { mutableIntStateOf(-1) }
    var ci by remember { mutableIntStateOf(-1) }
    var date by remember { mutableStateOf(now.toLocalDate().fmtD()) }
    var time by remember { mutableStateOf(now.toLocalTime().format(TIME_FMT)) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Происшествие") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField("Смена", shifts.map(label), si) { si = it }
                PickerField("Категория", INCIDENT_CATEGORIES, ci) { ci = it }
                OutlinedTextField(date, { date = it }, singleLine = true, label = { Text("Дата (дд.мм.гггг)") })
                OutlinedTextField(time, { time = it }, singleLine = true, label = { Text("Время (чч:мм)") })
                OutlinedTextField(text, { text = it }, label = { Text("Описание события и принятые меры") })
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = parseDate(date)
                val t = parseTime(time)
                if (si < 0 || ci < 0) error = "Выберите смену и категорию"
                else if (d == null) error = "Введите дату в формате дд.мм.гггг"
                else if (t == null) error = "Введите время в формате чч:мм"
                else onSave(Incident(shiftId = shifts[si].id, occurredAt = millisOf(d, t), category = INCIDENT_CATEGORIES[ci], description = text.trim()))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
