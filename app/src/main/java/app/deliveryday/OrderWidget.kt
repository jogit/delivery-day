package app.deliveryday

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Home screen widget, readable at a glance: when? anything new? where are we? */
class OrderWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(ctx, manager.getAppWidgetOptions(it))) }
    }

    // Before Android 12 the layout is picked here, from the size the user gave the widget.
    override fun onAppWidgetOptionsChanged(ctx: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        manager.updateAppWidget(id, views(ctx, options))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAll(ctx, refreshing = true)
            val req = OneTimeWorkRequestBuilder<CheckWorker>()
                .setInputData(workDataOf(CheckWorker.MANUAL to true))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("refresh", ExistingWorkPolicy.KEEP, req)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "app.deliveryday.REFRESH"

        /** To call after each check. */
        fun updateAll(ctx: Context, refreshing: Boolean = false) {
            val manager = AppWidgetManager.getInstance(ctx)
            manager.getAppWidgetIds(ComponentName(ctx, OrderWidget::class.java)).forEach {
                manager.updateAppWidget(it, views(ctx, manager.getAppWidgetOptions(it), refreshing))
            }
        }

        /**
         * One row: compact layout; two rows: full layout. From Android 12 the launcher switches
         * between both by itself while resizing; before, the current size picks one.
         */
        private fun views(ctx: Context, options: Bundle?, refreshing: Boolean = false): RemoteViews =
            if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(
                SizeF(180f, 40f) to build(ctx, refreshing, small = true),
                SizeF(180f, 100f) to build(ctx, refreshing, small = false),
            ))
            else build(ctx, refreshing, small = (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 110) in 1..99)

        /** "At least 12 more days", "Appointment: …"; empty without a delivery window. */
        private fun countdown(ctx: Context, o: OrderInfo, today: LocalDate): String {
            if (o.appointment) return o.appointmentText?.let { ctx.getString(R.string.w_appointment, it) } ?: ctx.getString(R.string.w_appointment_set)
            if (o.windowStart == null || o.windowEnd == null) return ""
            val toStart = ChronoUnit.DAYS.between(today, o.windowStart).toInt()
            val toEnd = ChronoUnit.DAYS.between(today, o.windowEnd).toInt()
            return when {
                toStart > 1 -> ctx.resources.getQuantityString(R.plurals.w_at_least, toStart, toStart)
                toStart == 1 -> ctx.getString(R.string.w_tomorrow)
                toEnd >= 0 -> ctx.resources.getQuantityString(R.plurals.w_soon, toEnd, toEnd)
                else -> ctx.getString(R.string.w_passed)
            }
        }

        /** "✓ Ordered › Car assigned › Appt. › Delivered": done in green, current step in bold white. */
        private fun steps(ctx: Context, o: OrderInfo): CharSequence {
            val labels = listOf(R.string.step_ordered, R.string.step_assigned, R.string.step_appointment_short, R.string.step_delivered)
            val done = simpleSteps(o)
            val current = done.indexOfFirst { !it }
            val sb = SpannableStringBuilder()
            labels.forEachIndexed { i, id ->
                if (i > 0) sb.append(" › ")
                val start = sb.length
                sb.append(if (done[i]) "✓ ${ctx.getString(id)}" else ctx.getString(id))
                val color = when {
                    done[i] -> ctx.getColor(R.color.green)
                    i == current -> ctx.getColor(R.color.text)
                    else -> ctx.getColor(R.color.muted)
                }
                sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (i == current) sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return sb
        }

        private fun build(ctx: Context, refreshing: Boolean, small: Boolean): RemoteViews {
            val v = RemoteViews(ctx.packageName, if (small) R.layout.widget_order_small else R.layout.widget_order)
            val store = Store(ctx)
            // The order still to come first (a friend may have several).
            val orders = Orders.parse(store.snapshot)
            val o = orders.firstOrNull { !it.delivered } ?: orders.firstOrNull()
            val today = LocalDate.now()
            if (!small) v.setTextViewText(R.id.w_updated, if (refreshing) ctx.getString(R.string.refreshing) else Fmt.updated(ctx, store.lastCheck, today))

            // Latest news, highlighted for a week ("🆕 VIN assigned · 2 days ago").
            val news = store.lastNews
            val newsDay = store.lastNewsAt.takeIf { it > 0 }?.let(Fmt::day)
            val newsAge = newsDay?.let { ChronoUnit.DAYS.between(it, today) }
            val freshNews = if (news != null && newsAge != null && newsAge <= 7)
                // The title's emoji ("🚗 Window…") would duplicate 🆕.
                "🆕 ${news.dropWhile { !it.isLetter() }} · ${Fmt.ago(ctx, newsAge)}" else null

            if (o == null) {
                v.setTextViewText(R.id.w_window, ctx.getString(R.string.w_not_connected))
                v.setTextViewText(R.id.w_countdown, ctx.getString(R.string.w_open_app))
                if (!small) {
                    v.setTextViewText(R.id.w_model, ctx.getString(R.string.app_name))
                    v.setTextViewText(R.id.w_news, "")
                    v.setTextViewText(R.id.w_steps, "")
                }
            } else if (small) {
                v.setTextViewText(R.id.w_window,
                    if (o.windowStart != null && o.windowEnd != null) "${Fmt.dayMonth(o.windowStart)} → ${Fmt.dayMonth(o.windowEnd)}"
                    else o.window ?: ctx.getString(R.string.w_no_window))
                v.setTextViewText(R.id.w_countdown, freshNews ?: countdown(ctx, o, today))
            } else {
                v.setTextViewText(R.id.w_model, ctx.getString(R.string.w_title, o.model) +
                    if (orders.size > 1) " (+${orders.size - 1})" else "")
                // When?
                if (o.windowStart != null && o.windowEnd != null) {
                    v.setTextViewText(R.id.w_window, ctx.getString(R.string.w_between, Fmt.dayMonth(o.windowStart), Fmt.dayMonth(o.windowEnd)))
                    v.setTextViewText(R.id.w_countdown, countdown(ctx, o, today))
                } else {
                    v.setTextViewText(R.id.w_window, o.window ?: ctx.getString(R.string.w_no_window))
                    v.setTextViewText(R.id.w_countdown, "")
                }
                // Anything new?
                if (freshNews != null) {
                    v.setTextViewText(R.id.w_news, freshNews)
                    v.setInt(R.id.w_news, "setBackgroundResource", R.drawable.widget_news_fresh)
                    v.setTextColor(R.id.w_news, ctx.getColor(R.color.text))
                } else {
                    val since = newsDay ?: o.orderedOn
                    v.setTextViewText(R.id.w_news, since?.let { ctx.getString(R.string.w_nothing_since, Fmt.dayMonth(it)) } ?: ctx.getString(R.string.news_none))
                    v.setInt(R.id.w_news, "setBackgroundResource", R.drawable.widget_news_quiet)
                    v.setTextColor(R.id.w_news, ctx.getColor(R.color.muted))
                }
                // Where are we?
                v.setTextViewText(R.id.w_steps, steps(ctx, o))
            }
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_root, open)
            val refresh = PendingIntent.getBroadcast(
                ctx, 1, Intent(ctx, OrderWidget::class.java).setAction(ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.w_refresh, refresh)
            return v
        }
    }
}
