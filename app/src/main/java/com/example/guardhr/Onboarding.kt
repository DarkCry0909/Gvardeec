package com.example.guardhr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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

/** Первый запуск: 1 — приветствие, 2 — имя, 3 — PIN-код и вход по отпечатку. */
@Composable
fun OnboardingScreen(onDone: (name: String, pin: String, bio: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val bioAvailable = remember { canBiometric(ctx) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf(bioAvailable) }
    val primary = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                Box(
                    Modifier.padding(4.dp).size(if (i == step) 12.dp else 8.dp).clip(CircleShape)
                        .background(if (i <= step) primary else MaterialTheme.colorScheme.outline)
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                0 -> {
                    Icon(Icons.Default.Lock, null, Modifier.size(72.dp), tint = primary)
                    Spacer(Modifier.height(20.dp))
                    Text("Добро пожаловать в приложение «Гвардеец»", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Учёт сотрудников частного охранного предприятия: личные карточки, документы и сроки их действия, " +
                            "календарь с напоминаниями и реестр предприятий, объектов и оружия.\n\n" +
                            "Все данные хранятся только на вашем устройстве и защищены PIN-кодом и шифрованием.",
                        textAlign = TextAlign.Center
                    )
                }
                1 -> {
                    Text("Как вас зовут?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Имя будет показано в приложении.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Ваше имя") }, singleLine = true)
                }
                else -> {
                    Text("Создайте PIN-код", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("4–8 цифр. Он защищает персональные данные сотрудников.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    PinField(a, "PIN-код") { a = it }
                    Spacer(Modifier.height(8.dp))
                    PinField(b, "Повторите PIN-код") { b = it }
                    if (b.isNotEmpty() && a != b) Text("PIN-коды не совпадают", color = Color(0xFFC62828))
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Разблокировка по отпечатку пальца")
                            Text(
                                if (bioAvailable) "Добавить вход по отпечатку?" else "Недоступно: на устройстве не настроена биометрия",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = bio && bioAvailable, enabled = bioAvailable, onCheckedChange = { bio = it })
                    }
                }
            }
        }

        when (step) {
            0 -> Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Начать работу") }
            1 -> {
                Button(enabled = name.isNotBlank(), onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Далее") }
                TextButton(onClick = { step = 0 }) { Text("Назад") }
            }
            else -> {
                Button(
                    enabled = a.length >= 4 && a == b,
                    onClick = { onDone(name.trim(), a, bio && bioAvailable) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Готово") }
                TextButton(onClick = { step = 1 }) { Text("Назад") }
            }
        }
    }
}
