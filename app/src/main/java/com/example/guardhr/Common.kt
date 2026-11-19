package com.example.guardhr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val RED = Color(0xFFC62828)
val ORANGE = Color(0xFFEF6C00)
val GREEN = Color(0xFF2E7D32)

@Composable
fun ConfirmDialog(text: String, onYes: () -> Unit, onNo: () -> Unit, confirmLabel: String = "Удалить") {
    AlertDialog(onDismissRequest = onNo, title = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text(confirmLabel) } },
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

/** Выбор из списка (кнопка с выпадающим меню). */
@Composable
fun PickerField(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selected in options.indices) "$label: ${options[selected]}" else label, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); open = false })
            }
        }
    }
}

/** Состояние обозначается значком и текстом, а не только цветом. */
@Composable
fun StatusLine(status: DocStatus, text: String) {
    val color = when (status) {
        DocStatus.EXPIRED -> RED
        DocStatus.EXPIRING -> ORANGE
        DocStatus.ACTIVE -> GREEN
    }
    val icon = when (status) {
        DocStatus.EXPIRED -> Icons.Default.Warning
        DocStatus.EXPIRING -> Icons.Default.Info
        DocStatus.ACTIVE -> Icons.Default.CheckCircle
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, color = color)
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, modifier = Modifier.padding(8.dp))
}
