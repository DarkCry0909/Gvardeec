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

private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private val SECTIONS = listOf("Предприятия", "Адреса", "Документы", "Сотрудники", "Охр. объекты", "КХО (СС)", "Оружие (СС)", "Учредители")

@Composable
fun App(user: AppUser, users: List<AppUser>, onSecureChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val role = roleOf(user.role)
    val empFlow = remember { dao.employees() }
    val docFlow = remember { dao.allDocs() }
    val employees by empFlow.collectAsState(initial = emptyList())
    val docs by docFlow.collectAsState(initial = emptyList())
    val events = remember(employees, docs) { buildEvents(employees, docs) }
    val alerts = remember(employees, docs) { buildAlerts(employees, docs) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Календарь") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                icon = { Icon(Icons.Default.Menu, null) }, label = { Text("Реестр") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                icon = { Icon(Icons.Default.Person, null) }, label = { Text("Служба") })
            NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                icon = { Icon(Icons.Default.Notifications, null) }, label = { Text("Сроки") })
            NavigationBarItem(selected = tab == 4, onClick = { tab = 4 },
                icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") })
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ГВАРДЕЕЦ", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${user.displayName} · ${role.title}", color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> CalendarScreen(events)
                    1 -> RegistryScreen(dao, role, employees, docs)
                    2 -> ServiceScreen(dao, role, employees, docs)
                    3 -> RemindersScreen(alerts)
                    else -> SettingsScreen(dao, user, users, onSecureChanged)
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
fun RegistryScreen(dao: AppDao, role: Role, employees: List<Employee>, docs: List<EmployeeDoc>) {
    var idx by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = idx, edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            SECTIONS.forEachIndexed { i, s -> Tab(selected = i == idx, onClick = { idx = i }, text = { Text(s) }) }
        }
        if (SECTIONS[idx] == "Сотрудники") EmployeesScreen(dao, role, employees, docs)
        else SectionScreen(dao, SECTIONS[idx], role.canEditCards())
    }
}

/** Универсальный раздел: Предприятия, Адреса, Документы, Охр. объекты, КХО, Оружие, Учредители. */
@Composable
fun SectionScreen(dao: AppDao, section: String, canEdit: Boolean) {
    val flow = remember(section) { dao.rows(section) }
    val rows by flow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember(section) { mutableStateOf(false) }
    var del by remember(section) { mutableStateOf<SectionItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) item { Text(if (canEdit) "Записей пока нет. Нажмите «+», чтобы добавить." else "Записей пока нет.") }
            items(rows, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.Bold)
                            if (r.details.isNotBlank()) Text(r.details)
                        }
                        if (canEdit) IconButton(onClick = { del = r }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        if (canEdit) FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
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
