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

@Composable
fun EmployeesScreen(dao: AppDao, role: Role, employees: List<Employee>, docs: List<EmployeeDoc>) {
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = employees.find { it.id == openId }
    if (open != null) EmployeeDetail(dao, role, open, employees, docs.filter { it.employeeId == open.id }) { openId = null }
    else EmployeeList(dao, role, employees, docs) { openId = it }
}

@Composable
fun EmployeeList(dao: AppDao, role: Role, employees: List<Employee>, docs: List<EmployeeDoc>, onOpen: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 — действующие, 1 — все, 2 — архив
    val statusByEmp = remember(docs) {
        currentDocs(docs).mapNotNull { d -> d.expiry?.let { d.employeeId to statusOf(it) } }
            .groupBy({ it.first }, { it.second })
    }
    val withDocs = remember(docs) { docs.map { it.employeeId }.toSet() }
    val q = query.trim().lowercase()
    val shown = employees.filter { e ->
        (filter == 1 || (filter == 0 && e.isActive) || (filter == 2 && !e.isActive)) &&
            (q.isEmpty() || e.fullName.lowercase().contains(q) || e.personnelNumber.lowercase().contains(q))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Поиск: ФИО или табельный №") },
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Действующие", "Все", "Архив").forEachIndexed { i, l ->
                    if (filter == i) Button(onClick = { filter = i }) { Text(l) }
                    else OutlinedButton(onClick = { filter = i }) { Text(l) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shown.isEmpty()) item {
                    Text(
                        if (employees.isEmpty()) "Сотрудников пока нет." + (if (role.canEditCards()) " Нажмите «+», чтобы добавить." else "")
                        else "Ничего не найдено."
                    )
                }
                items(shown, key = { it.id }) { e ->
                    val st = statusByEmp[e.id].orEmpty()
                    val expired = st.count { it == DocStatus.EXPIRED }
                    val expiring = st.count { it == DocStatus.EXPIRING }
                    Card(Modifier.fillMaxWidth().clickable { onOpen(e.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.fullName, fontWeight = FontWeight.Bold)
                            Text(listOf("Таб. № ${e.personnelNumber}", e.position).filter { it.isNotBlank() }.joinToString(" · "))
                            when {
                                !e.isActive -> Text("В архиве")
                                expired > 0 -> StatusLine(DocStatus.EXPIRED, "Просрочено документов: $expired")
                                expiring > 0 -> StatusLine(DocStatus.EXPIRING, "Истекает документов: $expiring")
                                e.id in withDocs -> StatusLine(DocStatus.ACTIVE, "Документы в порядке")
                                else -> Text("Документы не добавлены")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
        if (role.canEditCards()) FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Добавить")
        }
    }
    if (adding) EmployeeDialog(null, employees.associate { it.personnelNumber to it.id }, { adding = false }) { emp ->
        scope.launch { try { dao.insertEmployee(emp) } catch (ex: Exception) { } }
        adding = false
    }
}

@Composable
fun EmployeeDetail(dao: AppDao, role: Role, e: Employee, all: List<Employee>, docs: List<EmployeeDoc>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val canEdit = role.canEditCards()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var renewDoc by remember { mutableStateOf<EmployeeDoc?>(null) }
    var delDoc by remember { mutableStateOf<EmployeeDoc?>(null) }
    var archive by remember { mutableStateOf(false) }
    var delEmp by remember { mutableStateOf(false) }
    val current = remember(docs) { currentDocs(docs) }
    val currentIds = remember(current) { current.map { it.id }.toSet() }
    val rank6 = current.any { it.docType == DocType.RANK && it.number.trim() == "6" }
    val hasWeapon = current.any { it.docType == DocType.WEAPON }
    val available = DocType.values().filter { it != DocType.WEAPON || rank6 }

    val saveDoc: (DocType, String, String, String, Long?, Long?) -> Unit = { type, series, number, issuedBy, issue, expiry ->
        scope.launch {
            dao.insertDoc(
                EmployeeDoc(employeeId = e.id, type = type.name, number = number, issueDay = issue,
                    expiryDay = expiry, series = series, issuedBy = issuedBy)
            )
        }
        adding = false
        renewDoc = null
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                Text(e.fullName, style = MaterialTheme.typography.titleLarge)
            }
        }
        item { Text("Табельный номер: ${e.personnelNumber}") }
        item { Text(listOf(e.position, e.phone).filter { it.isNotBlank() }.joinToString(" · ")) }
        e.birthDay?.let { item { Text("Дата рождения: ${LocalDate.ofEpochDay(it).fmtD()}") } }
        e.hireDay?.let { item { Text("Дата приёма: ${LocalDate.ofEpochDay(it).fmtD()}") } }
        if (!e.isActive) item { Text("Карточка в архиве: сотрудник не показывается в сменах и напоминаниях.", color = ORANGE) }
        if (rank6 && !hasWeapon) item { Text("6 разряд: нужна справка на оружие — добавьте документ.", color = RED) }
        if (canEdit) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }) { Text("Добавить документ") }
                OutlinedButton(onClick = { editing = true }) { Text("Изменить") }
            }
        }
        items(docs.sortedWith(compareBy({ it.type }, { -(it.expiryDay ?: Long.MAX_VALUE) })), key = { it.id }) { d ->
            val t = d.docType
            val isCurrent = d.id in currentIds
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
                        val exp = d.expiry
                        if (exp != null) {
                            if (!isCurrent) {
                                Text("Действовал до: ${exp.fmtD()} — в истории, заменён новой записью", style = MaterialTheme.typography.bodySmall)
                            } else {
                                val n = daysLeft(exp)
                                val st = statusOf(exp)
                                StatusLine(
                                    st, when (st) {
                                        DocStatus.EXPIRED -> "Просрочен: срок истёк ${exp.fmtD()}"
                                        DocStatus.EXPIRING -> "Истекает ${exp.fmtD()} (осталось $n дн.)"
                                        DocStatus.ACTIVE -> "Действует до ${exp.fmtD()} (осталось $n дн.)"
                                    }
                                )
                            }
                        }
                        if (canEdit && isCurrent && t.hasExpiry) TextButton(onClick = { renewDoc = d }) { Text("Продлить: внести новую запись") }
                    }
                    if (canEdit) IconButton(onClick = { delDoc = d }) { Icon(Icons.Default.Delete, "Удалить") }
                }
            }
        }
        if (canEdit) item {
            if (e.isActive) TextButton(onClick = { archive = true }) { Text("Архивировать сотрудника", color = RED) }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { dao.updateEmployee(e.copy(isActive = true)) } }) { Text("Вернуть из архива") }
                if (role.isAdmin()) TextButton(onClick = { delEmp = true }) { Text("Удалить навсегда", color = RED) }
            }
        }
    }

    if (adding) AddDocDialog(available, null, { adding = false }, saveDoc)
    renewDoc?.let { d -> AddDocDialog(listOf(d.docType), d, { renewDoc = null }, saveDoc) }
    if (editing) EmployeeDialog(e, all.associate { it.personnelNumber to it.id }, { editing = false }) { emp ->
        scope.launch { try { dao.updateEmployee(emp) } catch (ex: Exception) { } }
        editing = false
    }
    delDoc?.let { d ->
        ConfirmDialog("Удалить документ «${d.docType.title}»?", { scope.launch { dao.deleteDoc(d) }; delDoc = null }, { delDoc = null })
    }
    if (archive) ConfirmDialog(
        "Архивировать сотрудника? Он исчезнет из смен и напоминаний, история сохранится.",
        { scope.launch { dao.updateEmployee(e.copy(isActive = false)) }; archive = false }, { archive = false }, "Архивировать"
    )
    if (delEmp) ConfirmDialog("Удалить сотрудника, его документы, смены и происшествия навсегда?", {
        scope.launch {
            dao.deleteIncidentsOfEmployee(e.id)
            dao.deleteShiftsOfEmployee(e.id)
            dao.deleteDocsOf(e.id)
            dao.deleteEmployee(e)
        }
        delEmp = false; onBack()
    }, { delEmp = false })
}

@Composable
fun EmployeeDialog(initial: Employee?, numbers: Map<String, Long>, onDismiss: () -> Unit, onSave: (Employee) -> Unit) {
    val parts = remember { (initial?.fullName ?: "").split(" ").filter { it.isNotBlank() } }
    var last by remember { mutableStateOf(parts.getOrElse(0) { "" }) }
    var first by remember { mutableStateOf(parts.getOrElse(1) { "" }) }
    var middle by remember { mutableStateOf(parts.drop(2).joinToString(" ")) }
    var number by remember { mutableStateOf(initial?.personnelNumber ?: "") }
    var birth by remember { mutableStateOf(initial?.birthDay?.let { LocalDate.ofEpochDay(it).fmtD() } ?: "") }
    var hire by remember { mutableStateOf(initial?.hireDay?.let { LocalDate.ofEpochDay(it).fmtD() } ?: "") }
    var position by remember { mutableStateOf(initial?.position ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Новый сотрудник" else "Карточка сотрудника") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(last, { last = it }, singleLine = true, label = { Text("Фамилия") })
                OutlinedTextField(first, { first = it }, singleLine = true, label = { Text("Имя") })
                OutlinedTextField(middle, { middle = it }, singleLine = true, label = { Text("Отчество") })
                OutlinedTextField(number, { number = it }, singleLine = true, label = { Text("Табельный номер") })
                OutlinedTextField(birth, { birth = it }, singleLine = true, label = { Text("Дата рождения (дд.мм.гггг)") })
                OutlinedTextField(hire, { hire = it }, singleLine = true, label = { Text("Дата приёма (дд.мм.гггг)") })
                OutlinedTextField(position, { position = it }, singleLine = true, label = { Text("Должность") })
                OutlinedTextField(phone, { phone = it }, singleLine = true, label = { Text("Телефон") })
                error?.let { Text(it, color = RED) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val num = number.trim()
                val b = if (birth.isBlank()) null else parseDate(birth)
                val h = if (hire.isBlank()) null else parseDate(hire)
                if (last.isBlank() || first.isBlank()) error = "Укажите фамилию и имя"
                else if (num.isEmpty()) error = "Укажите табельный номер"
                else if (numbers[num] != null && numbers[num] != initial?.id) error = "Табельный номер уже используется"
                else if (birth.isNotBlank() && b == null) error = "Введите дату рождения в формате дд.мм.гггг"
                else if (hire.isNotBlank() && h == null) error = "Введите дату приёма в формате дд.мм.гггг"
                else onSave(
                    Employee(
                        id = initial?.id ?: 0,
                        fullName = listOf(last, first, middle).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" "),
                        position = position.trim(), phone = phone.trim(),
                        birthDay = b?.toEpochDay(), personnelNumber = num, hireDay = h?.toEpochDay(),
                        isActive = initial?.isActive ?: true
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

/** initial — документ, продлеваемый новой записью: серия, номер и орган выдачи подставляются из него. */
@Composable
fun AddDocDialog(
    available: List<DocType>, initial: EmployeeDoc?, onDismiss: () -> Unit,
    onSave: (DocType, String, String, String, Long?, Long?) -> Unit
) {
    var type by remember { mutableStateOf(available.first()) }
    var series by remember { mutableStateOf(initial?.series ?: "") }
    var number by remember { mutableStateOf(initial?.number ?: "") }
    var issuedBy by remember { mutableStateOf(initial?.issuedBy ?: "") }
    var issue by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isRank = type == DocType.RANK

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Новый документ" else "Новая запись: ${type.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (available.size > 1) available.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { type = t }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(t.title)
                    }
                }
                if (initial != null) Text("Прежняя запись останется в истории.", style = MaterialTheme.typography.bodySmall)
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
                if (issue.isNotBlank() && i == null) error = "Введите дату в формате дд.мм.гггг"
                else if (type.hasExpiry && (type.expiryRequired || expiry.isNotBlank()) && x == null) error = "Введите срок действия в формате дд.мм.гггг"
                else if (isRank && number.trim() !in listOf("4", "5", "6")) error = "Разряд: 4, 5 или 6"
                else onSave(type, if (isRank) "" else series.trim(), number.trim(), if (isRank) "" else issuedBy.trim(), i?.toEpochDay(), x?.toEpochDay())
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
