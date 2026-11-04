package com.example.guardhr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val dao = AppDb.get(applicationContext).dao()
        val today = LocalDate.now()
        val todays = buildEvents(dao.employeesOnce(), dao.docsOnce()).filter { it.date == today }
        if (todays.isNotEmpty()) showNotification(applicationContext, todays)
        return Result.success()
    }
}

fun scheduleReminders(ctx: Context) {
    val now = LocalDateTime.now()
    var next = now.toLocalDate().atTime(9, 0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, req)
}

fun showNotification(ctx: Context, events: List<Event>) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("deadlines", "Сроки документов", NotificationManager.IMPORTANCE_HIGH))
    val lines = events.map { "${it.employee}: ${it.doc.title} — ${it.note}" }
    val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(ctx, "deadlines")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Сроки документов: ${events.size}")
        .setContentText(lines.first())
        .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    nm.notify(1, n)
}
