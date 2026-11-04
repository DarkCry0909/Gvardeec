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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val ERR = Color(0xFFC62828)

@Composable
fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { if (it.length <= 8 && it.all { c -> c.isDigit() }) onChange(it) },
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

@Composable
fun SetupPinScreen(onDone: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Гвардеец", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Создайте PIN-код (4–8 цифр). Он защищает персональные данные сотрудников.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PinField(a, "PIN-код") { a = it }
        Spacer(Modifier.height(8.dp))
        PinField(b, "Повторите PIN-код") { b = it }
        if (b.isNotEmpty() && a != b) Text("PIN-коды не совпадают", color = ERR)
        Spacer(Modifier.height(16.dp))
        Button(enabled = a.length >= 4 && a == b, onClick = { onDone(a) }) { Text("Сохранить") }
    }
}

@Composable
fun LockScreen(activity: FragmentActivity, onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val useBio = remember { Sec.biometricEnabled(ctx) && canBiometric(ctx) }
    val lockSec = (Sec.lockUntil(ctx) - now + 999) / 1000

    val unlock = { Sec.resetFails(ctx); onUnlock() }

    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    LaunchedEffect(Unit) {
        if (useBio) {
            delay(400)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) promptBiometric(activity, unlock)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Гвардеец", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        PinField(pin, "PIN-код") { pin = it }
        if (lockSec > 0) Text("Слишком много попыток. Подождите $lockSec с.", color = ERR)
        else if (msg.isNotEmpty()) Text(msg, color = ERR)
        Spacer(Modifier.height(16.dp))
        Button(enabled = lockSec <= 0 && pin.length >= 4, onClick = {
            if (Sec.checkPin(ctx, pin)) unlock()
            else { Sec.registerFail(ctx); msg = "Неверный PIN-код"; pin = ""; now = System.currentTimeMillis() }
        }) { Text("Войти") }
        if (useBio) TextButton(onClick = { promptBiometric(activity, unlock) }) { Text("Войти по отпечатку") }
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

@Composable
fun ChangePinDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var old by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Смена PIN-кода") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(old, "Текущий PIN-код") { old = it }
                PinField(a, "Новый PIN-код") { a = it }
                PinField(b, "Повторите новый PIN-код") { b = it }
                if (err.isNotEmpty()) Text(err, color = ERR)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!Sec.checkPin(ctx, old)) err = "Текущий PIN-код неверен"
                else if (a.length < 4) err = "Новый PIN-код: минимум 4 цифры"
                else if (a != b) err = "PIN-коды не совпадают"
                else { Sec.setPin(ctx, a); onDone() }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun SettingsScreen(dao: AppDao, onSecureChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bioAvailable = remember { canBiometric(ctx) }
    var bio by remember { mutableStateOf(Sec.biometricEnabled(ctx)) }
    var secure by remember { mutableStateOf(Sec.secureScreen(ctx)) }
    var status by remember { mutableStateOf("") }
    var changePin by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Оформление", style = MaterialTheme.typography.titleLarge)
        var themeMode by remember { mutableStateOf(Sec.themeMode(ctx)) }
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
            Switch(checked = bio && bioAvailable, enabled = bioAvailable, onCheckedChange = { bio = it; Sec.setBiometric(ctx, it) })
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
        Text("Резервная копия", style = MaterialTheme.typography.titleLarge)
        Text("Копия сохраняется в файл (например, в «Загрузки») и шифруется паролем, который вы зададите. " +
            "Чтобы перенести данные на новый телефон: создайте копию здесь, передайте файл на новое устройство и выберите «Восстановить из копии». " +
            "На старом телефоне данные остаются. Пароль восстановить невозможно — запомните его.")
        Button(onClick = { exportDialog = true }) { Text("Создать копию") }
        OutlinedButton(onClick = { Session.skipNextLock = true; openLauncher.launch(arrayOf("*/*")) }) { Text("Восстановить из копии") }
        if (status.isNotEmpty()) Text(status)
    }

    if (changePin) ChangePinDialog({ changePin = false }) { changePin = false; status = "PIN-код изменён." }

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
