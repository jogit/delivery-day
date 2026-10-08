package app.deliveryday

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // Automatic check: at night, space them out (one every 3 hours is enough).
        val manual = inputData.getBoolean(MANUAL, false)
        if (!manual && skipAtNight(LocalDateTime.now(), Store(applicationContext).lastCheck)) return Result.success()
        return checkNow(applicationContext)
    }

    companion object {
        const val CHANNEL = "orders"
        /** Input data: check requested by the user (never postponed). */
        const val MANUAL = "manual"
        private const val TESLA_APP = "com.teslamotors.tesla"

        /** Between 11 pm and 7 am, skip the check if the last one is less than 3 hours old. */
        fun skipAtNight(now: LocalDateTime, lastCheckMs: Long): Boolean {
            val night = now.hour >= 23 || now.hour < 7
            if (!night || lastCheckMs == 0L) return false
            val last = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastCheckMs), ZoneId.systemDefault())
            return Duration.between(last, now) < Duration.ofHours(3)
        }

        /** Checks the orders and notifies on changes. Also used by the refresh buttons. */
        fun checkNow(ctx: Context): Result {
            val store = Store(ctx)
            if (store.refreshToken == null) return Result.failure()
            if (store.demo) { store.lastCheck = System.currentTimeMillis(); OrderWidget.updateAll(ctx); return Result.success() }
            val hadTasksError = store.tasksError != null
            return try {
                val new = TeslaApi(store).fetchOrders().toString()
                if (!hadTasksError && store.tasksError != null) notify(ctx, ctx.getString(R.string.notif_tasks_error_title),
                    listOf(ctx.getString(R.string.notif_tasks_error_text, store.tasksError)))
                val old = store.snapshot
                if (old != null && old != new) {
                    // Only important changes notify; technical details stay visible in the app (raw data).
                    val s = Changes.summarize(old, new)
                    if (s.important > 0) {
                        notify(ctx, ChangeText.notificationTitle(ctx, s), ChangeText.notificationLines(ctx, s))
                        store.lastNews = ChangeText.headline(ctx, s)
                        store.lastNewsAt = System.currentTimeMillis()
                        store.addHistory(s.changes.map { ChangeText.title(ctx, it) to ChangeText.detail(ctx, it) })
                    }
                }
                store.snapshot = new
                store.lastCheck = System.currentTimeMillis()
                store.lastError = null
                OrderWidget.updateAll(ctx)
                schedule(ctx)
                Result.success()
            } catch (e: AuthExpired) {
                store.needsLogin = true
                notify(ctx, ctx.getString(R.string.notif_relogin_title), listOf(ctx.getString(R.string.notif_relogin_text)))
                Result.failure()
            } catch (e: Exception) {
                store.lastError = e.message ?: e.javaClass.simpleName
                Result.retry()
            }
        }

        /**
         * Every hour while nothing moves, every 15 min (Android minimum) once it gets real:
         * VIN assigned, appointment set, or delivery window less than 3 weeks away.
         * Limits the calls to Tesla's unofficial API.
         */
        fun intervalFor(snapshot: String?, today: LocalDate = LocalDate.now()): Long {
            val hot = Orders.parse(snapshot, today).any { o ->
                o.vin != null || o.appointment ||
                    o.windowStart?.let { ChronoUnit.DAYS.between(today, it) <= 21 } == true
            }
            return if (hot) 15 else 60
        }

        /** (Re)schedules the periodic check if the interval changed (or if [force]). */
        fun schedule(ctx: Context, force: Boolean = false) {
            val store = Store(ctx)
            val minutes = intervalFor(store.snapshot)
            if (!force && store.checkInterval == minutes) return
            // Flex window: Android picks the moment within the last third of the period,
            // so calls don't always hit at the same minute.
            val req = PeriodicWorkRequestBuilder<CheckWorker>(minutes, TimeUnit.MINUTES, maxOf(5L, minutes / 3), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("check", ExistingPeriodicWorkPolicy.UPDATE, req)
            store.checkInterval = minutes
        }

        internal fun notify(ctx: Context, title: String, lines: List<String>) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH))
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_car)
                .setColor(0xFFE82127.toInt())
                .setContentTitle(title)
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.take(15).joinToString("\n")))
                .setContentIntent(open)
                .setAutoCancel(true)
                // On the lock screen, only "Delivery Day: update" — no amounts, VIN or dates.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_car).setColor(0xFFE82127.toInt())
                        .setContentTitle(ctx.getString(R.string.app_name)).setContentText(ctx.getString(R.string.notif_public)).build()
                )
                .apply {
                    // Shortcut to the official Tesla app (tasks, payment, appointment…), when installed.
                    ctx.packageManager.getLaunchIntentForPackage(TESLA_APP)?.let {
                        addAction(0, ctx.getString(R.string.open_tesla_app), PendingIntent.getActivity(ctx, 2, it, PendingIntent.FLAG_IMMUTABLE))
                    }
                }
                .build()
            NotificationManagerCompat.from(ctx).notify(System.currentTimeMillis().toInt(), n)
        }
    }
}
