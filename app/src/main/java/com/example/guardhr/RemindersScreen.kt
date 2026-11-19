package com.example.guardhr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Документы, требующие внимания, сгруппированные по срочности. Дублирует уведомления. */
@Composable
fun RemindersScreen(alerts: List<DocAlert>) {
    val expired = alerts.filter { it.status == DocStatus.EXPIRED }
    val expiring = alerts.filter { it.status == DocStatus.EXPIRING }
    val upcoming = alerts.filter { it.status == DocStatus.ACTIVE }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Напоминания", style = MaterialTheme.typography.titleLarge) }
        if (alerts.isEmpty()) item { Text("Документов, требующих внимания, нет. Все сроки в порядке.") }
        group("Просрочено", expired)
        group("Истекает в ближайшие $EXPIRING_DAYS дней", expiring)
        group("Приближается срок (напоминание уже наступило)", upcoming)
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(title: String, list: List<DocAlert>) {
    if (list.isEmpty()) return
    item { Text("$title: ${list.size}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
    items(list, key = { it.doc.id }) { a ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(a.emp.fullName, fontWeight = FontWeight.Bold)
                Text("${a.doc.docType.title} · срок до ${a.expiry.fmtD()}")
                StatusLine(
                    a.status, when (a.status) {
                        DocStatus.EXPIRED -> "Просрочен на ${-a.days} дн."
                        DocStatus.EXPIRING -> "Истекает, осталось ${a.days} дн."
                        DocStatus.ACTIVE -> "Осталось ${a.days} дн."
                    }
                )
            }
        }
    }
}
