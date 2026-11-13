package com.planavora.app.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.*
import com.planavora.app.*
import com.planavora.app.R
import com.planavora.app.domain.dueDate
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

val remindersKey = booleanPreferencesKey("reminders")
val spokenKey = booleanPreferencesKey("spoken")
val darkKey = stringPreferencesKey("theme")
private val lastReminder = stringPreferencesKey("last_reminder")

class DeadlineWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PlanavoraApplication
        val settings = app.settings.data.first()
        if (settings[remindersKey] != true || LocalTime.now().hour !in 8..20) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val today = LocalDate.now().toString()
        if (settings[lastReminder] == today) return Result.success()
        val dao = app.store.dao
        val needed = dao.activeEvents().any { event ->
            val details = event.details()
            val eventToday = LocalDate.now(runCatching { ZoneId.of(details.zone) }.getOrDefault(ZoneId.systemDefault()))
            details.date == eventToday.toString() || details.rsvpDeadline == eventToday.toString() ||
                dao.items(event.id).any { row -> dueDate(row.item().detail)?.let { it <= eventToday } == true }
        }
        if (!needed) return Result.success()
        val manager = app.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return Result.success()
        manager.createNotificationChannel(NotificationChannel("deadlines", app.getString(R.string.reminders), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(1, NotificationCompat.Builder(app, "deadlines")
            .setSmallIcon(R.drawable.brand_mono).setContentTitle(app.getString(R.string.app_name))
            .setContentText(app.getString(R.string.reminder_body)).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open).setAutoCancel(true).build())
        app.settings.edit { it[lastReminder] = today }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<DeadlineWorker>(3, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("event-deadlines", ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}
