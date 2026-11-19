package com.example.guardhr

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { if (it.length <= 8 && it.all { c -> c.isDigit() }) onChange(it) },
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

/** Вход: выбор пользователя (если их несколько) и PIN-код либо отпечаток пальца. */
@Composable
fun LockScreen(activity: FragmentActivity, users: List<AppUser>, onLogin: (AppUser) -> Unit) {
    val ctx = LocalContext.current
    val active = remember(users) { users.filter { !it.blocked } }
    var selected by remember { mutableIntStateOf(maxOf(0, active.indexOfFirst { it.id == Sec.lastUser(ctx) })) }
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val bioUser = remember(users) { active.find { it.id == Sec.bioUser(ctx) } }
    val useBio = remember(bioUser) { bioUser != null && canBiometric(ctx) }
    val lockSec = (Sec.lockUntil(ctx) - now + 999) / 1000
    val login: (AppUser) -> Unit = { u -> Sec.resetFails(ctx); Sec.setLastUser(ctx, u.id); onLogin(u) }

    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    // Если включён вход по отпечатку — окно отпечатка открывается сразу при запуске
    LaunchedEffect(useBio) {
        if (useBio && bioUser != null) {
            var t = 0
            while (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && t < 30) { delay(100); t++ }
            promptBiometric(activity) { login(bioUser) }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Гвардеец", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        if (active.isEmpty()) {
            Text("Нет доступных учётных записей. Обратитесь к администратору.")
        } else {
            val user = active.getOrNull(selected) ?: active.first()
            if (active.size > 1) PickerField("Пользователь", active.map { "${it.displayName} (${it.login})" }, selected) {
                selected = it; pin = ""; msg = ""
            } else Text("Здравствуйте, ${user.displayName}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            PinField(pin, "PIN-код") { pin = it }
            if (lockSec > 0) Text("Слишком много попыток. Подождите $lockSec с.", color = RED)
            else if (msg.isNotEmpty()) Text(msg, color = RED)
            Spacer(Modifier.height(16.dp))
            Button(enabled = lockSec <= 0 && pin.length >= 4, onClick = {
                if (verifyPin(pin, user.salt, user.passwordHash)) login(user)
                else { Sec.registerFail(ctx); msg = "Неверный PIN-код"; pin = ""; now = System.currentTimeMillis() }
            }) { Text("Войти") }
            if (useBio && bioUser != null) TextButton(onClick = { promptBiometric(activity) { login(bioUser) } }) {
                Text("Войти по отпечатку")
            }
        }
    }
}

@Composable
fun PasswordDialog(
    title: String, text: String, confirm: Boolean, showReplace: Boolean,
    onDismiss: () -> Unit, onOk: (String, Boolean) -> Unit
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    val ok = a.length >= 6 && (!confirm || a == b)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                OutlinedTextField(a, { a = it }, label = { Text("Пароль копии (от 6 символов)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                if (confirm) OutlinedTextField(b, { b = it }, label = { Text("Повторите пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                if (showReplace) {
                    Row(Modifier.fillMaxWidth().clickable { replace = !replace }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = replace, onCheckedChange = { replace = it })
                        Text("Заменить все текущие данные")
                    }
                    Text(
                        if (replace) "Текущие данные будут удалены и заменены данными из копии."
                        else "Данные из копии добавятся к текущим, одинаковые записи не дублируются.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onOk(a, replace) }) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

/** Смена PIN-кода пользователем (user != null — проверяется текущий) или сброс администратором (checkOld = false). */
@Composable
fun ChangePinDialog(user: AppUser, checkOld: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var old by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (checkOld) "Смена PIN-кода" else "Новый PIN-код: ${user.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (checkOld) PinField(old, "Текущий PIN-код") { old = it }
                PinField(a, "Новый PIN-код") { a = it }
                PinField(b, "Повторите новый PIN-код") { b = it }
                if (err.isNotEmpty()) Text(err, color = RED)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (checkOld && !verifyPin(old, user.salt, user.passwordHash)) err = "Текущий PIN-код неверен"
                else if (a.length < 4) err = "Новый PIN-код: минимум 4 цифры"
                else if (a != b) err = "PIN-коды не совпадают"
                else onSave(a)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun UserDialog(users: List<AppUser>, onDismiss: () -> Unit, onSave: (name: String, login: String, role: Role, pin: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var ri by remember { mutableIntStateOf(1) }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val roles = Role.values().toList()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Новый пользователь") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Имя (как показывать)") })
                OutlinedTextField(login, { login = it.filter { c -> !c.isWhitespace() } }, singleLine = true, label = { Text("Логин") })
                PickerField("Роль", roles.map { it.title }, ri) { ri = it }
                PinField(pin, "PIN-код (4–8 цифр)") { pin = it }
                if (err.isNotEmpty()) Text(err, color = RED)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || login.isBlank()) err = "Укажите имя и логин"
                else if (users.any { it.login.equals(login, ignoreCase = true) }) err = "Такой логин уже существует"
                else if (pin.length < 4) err = "PIN-код: минимум 4 цифры"
                else onSave(name.trim(), login.trim(), roles[ri], pin)
            }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun UsersSection(dao: AppDao, me: AppUser, users: List<AppUser>) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var delU by remember { mutableStateOf<AppUser?>(null) }
    var resetU by remember { mutableStateOf<AppUser?>(null) }

    Text("Пользователи", style = MaterialTheme.typography.titleLarge)
    users.forEach { u ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("${u.displayName} (${u.login})", fontWeight = FontWeight.Bold)
                Text(roleOf(u.role).title + if (u.blocked) " · доступ заблокирован" else "")
                if (u.id != me.id) {
                    TextButton(onClick = { scope.launch { dao.updateUser(u.copy(blocked = !u.blocked)) } }) {
                        Text(if (u.blocked) "Разблокировать" else "Заблокировать доступ")
                    }
                    TextButton(onClick = { resetU = u }) { Text("Задать новый PIN-код") }
                    TextButton(onClick = { delU = u }) { Text("Удалить учётную запись", color = RED) }
                } else Text("Это вы", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Button(onClick = { adding = true }) { Text("Добавить пользователя") }

    if (adding) UserDialog(users, { adding = false }) { name, login, role, pin ->
        scope.launch {
            val (salt, hash) = hashPin(pin)
            try {
                dao.insertUser(AppUser(login = login, displayName = name, passwordHash = hash, salt = salt, role = role.name))
            } catch (ex: Exception) { }
        }
        adding = false
    }
    resetU?.let { u ->
        ChangePinDialog(u, false, { resetU = null }) { pin ->
            scope.launch {
                val (salt, hash) = hashPin(pin)
                dao.updateUser(u.copy(passwordHash = hash, salt = salt))
            }
            resetU = null
        }
    }
    delU?.let { u ->
        ConfirmDialog("Удалить учётную запись «${u.displayName}»?", {
            scope.launch { dao.deleteUser(u) }
            delU = null
        }, { delU = null })
    }
}

@Composable
fun SettingsScreen(dao: AppDao, me: AppUser, users: List<AppUser>, onSecureChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val role = roleOf(me.role)
    val bioAvailable = remember { canBiometric(ctx) }
    var bio by remember { mutableStateOf(Sec.bioUser(ctx) == me.id) }
    var secure by remember { mutableStateOf(Sec.secureScreen(ctx)) }
    var themeMode by remember { mutableStateOf(Sec.themeMode(ctx)) }
    var status by remember { mutableStateOf("") }
    var changePin by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCsv by remember { mutableStateOf("") }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val pass = exportPass
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = BackupCrypto.encrypt(exportJson(dao).toByteArray(), pass.toCharArray())
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                    "Копия сохранена."
                }.getOrElse { "Не удалось сохранить копию: ${it.message}" }
            }
            exportPass = ""
        } else exportPass = ""
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val kind = pendingCsv
        pendingCsv = ""
        if (uri != null && kind.isNotEmpty()) scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val text = buildCsv(kind, dao)
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    "Файл сохранён."
                }.getOrElse { "Не удалось сохранить файл" }
            }
        }
    }
    val exportCsv: (String, String) -> Unit = { kind, name ->
        pendingCsv = kind
        Session.skipNextLock = true
        csvLauncher.launch("${name}_${LocalDate.now()}.csv")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Учётная запись", style = MaterialTheme.typography.titleLarge)
        Text("${me.displayName} · ${role.title}")
        OutlinedButton(onClick = { Session.user = null }) { Text("Выйти") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Оформление", style = MaterialTheme.typography.titleLarge)
        listOf("Как в системе", "Светлая", "Тёмная").forEachIndexed { i, label ->
            Row(
                Modifier.fillMaxWidth().clickable { themeMode = i; ThemeState.mode = i; Sec.setThemeMode(ctx, i) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = themeMode == i, onClick = { themeMode = i; ThemeState.mode = i; Sec.setThemeMode(ctx, i) })
                Text(label)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Защита", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Вход по отпечатку пальца")
                if (!bioAvailable) Text("Недоступно: на устройстве не настроена биометрия", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = bio && bioAvailable, enabled = bioAvailable, onCheckedChange = {
                bio = it
                Sec.setBioUser(ctx, if (it) me.id else 0L)
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Запретить скриншоты")
                Text("Также скрывает окно приложения в списке недавних. Отключите на время, чтобы сделать скриншоты для диплома.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = secure, onCheckedChange = { secure = it; Sec.setSecureScreen(ctx, it); onSecureChanged(it) })
        }
        OutlinedButton(onClick = { changePin = true }) { Text("Сменить PIN-код") }
        Text("База данных зашифрована (AES-256, SQLCipher). Уведомления на заблокированном экране не показывают ФИО и документы.",
            style = MaterialTheme.typography.bodySmall)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Экспорт для отчётности (CSV)", style = MaterialTheme.typography.titleLarge)
        Text("Файл открывается в Excel. Он сохраняется только в то место, которое вы выберете.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { exportCsv("emp", "sotrudniki") }) { Text("Список сотрудников") }
        OutlinedButton(onClick = { exportCsv("docs", "dokumenty") }) { Text("Документы и сроки") }
        OutlinedButton(onClick = { exportCsv("shifts", "smeny") }) { Text("График смен") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Резервная копия", style = MaterialTheme.typography.titleLarge)
        Text("Копия сохраняется в файл (например, в «Загрузки») и шифруется паролем, который вы зададите. " +
            "Чтобы перенести данные на новый телефон: создайте копию здесь, передайте файл на новое устройство и выберите «Восстановить из копии». " +
            "На старом телефоне данные остаются. Пароль восстановить невозможно — запомните его. Учётные записи в копию не входят.")
        Button(onClick = { exportDialog = true }) { Text("Создать копию") }
        if (role.isAdmin()) OutlinedButton(onClick = { Session.skipNextLock = true; openLauncher.launch(arrayOf("*/*")) }) {
            Text("Восстановить из копии")
        }
        if (status.isNotEmpty()) Text(status)

        if (role.isAdmin()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            UsersSection(dao, me, users)
        }
    }

    if (changePin) ChangePinDialog(me, true, { changePin = false }) { pin ->
        val (salt, hash) = hashPin(pin)
        val updated = me.copy(passwordHash = hash, salt = salt)
        scope.launch { dao.updateUser(updated) }
        Session.user = updated
        changePin = false
        status = "PIN-код изменён."
    }

    if (exportDialog) PasswordDialog("Пароль для копии", "Придумайте пароль, которым будет зашифрован файл.", true, false,
        onDismiss = { exportDialog = false }) { pass, _ ->
        exportPass = pass
        exportDialog = false
        Session.skipNextLock = true
        createLauncher.launch("gvardeec_${LocalDate.now()}.gvb")
    }

    restoreUri?.let { u ->
        PasswordDialog("Восстановление", "Введите пароль копии.", false, true,
            onDismiss = { restoreUri = null }) { pass, replace ->
            restoreUri = null
            scope.launch {
                status = withContext(Dispatchers.IO) {
                    runCatching {
                        val data = ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() }
                        val plain = BackupCrypto.decrypt(data, pass.toCharArray())
                        if (plain == null) "Неверный пароль или файл повреждён."
                        else { importJson(ctx, String(plain), replace); "Данные восстановлены." }
                    }.getOrElse { "Не удалось восстановить: ${it.message}" }
                }
            }
        }
    }
}
