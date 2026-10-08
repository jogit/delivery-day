package app.deliveryday

import android.content.Context

/** Important changes between two snapshots. Turned into text by [ChangeText], in the phone's language. */
object Changes {
    enum class Kind {
        NEW_ORDER, VIN_ASSIGNED, VIN_CHANGED, STATUS, WINDOW, APPOINTMENT_SET, APPOINTMENT_CHANGED,
        PLACE, AMOUNT, TRADE_IN_STATUS, TRADE_IN_OFFER, TASK_DONE, TASK_TODO, TASK_INFO,
    }

    /** One change; [before]/[after] are the old and new orders (only [after] for a new order). */
    data class Change(val kind: Kind, val before: OrderInfo?, val after: OrderInfo, val task: TaskInfo? = null)

    /** [minor]: other raw changes (technical details), which do not notify. */
    data class Summary(val changes: List<Change>, val minor: Int) {
        val important get() = changes.size
    }

    fun summarize(old: String, new: String): Summary {
        val before = Orders.parse(old).associateBy { it.reference }
        val changes = mutableListOf<Change>()
        for (n in Orders.parse(new)) {
            val o = before[n.reference]
            if (o == null) { changes += Change(Kind.NEW_ORDER, null, n); continue }
            fun add(kind: Kind, changed: Boolean) { if (changed) changes += Change(kind, o, n) }
            // Tesla's texts can only be compared when they are in the same language.
            val sameLang = o.lang != null && o.lang == n.lang
            if (o.vin == null && n.vin != null) add(Kind.VIN_ASSIGNED, true) else add(Kind.VIN_CHANGED, o.vin != n.vin)
            add(Kind.STATUS, o.statusCode != n.statusCode)
            add(Kind.WINDOW, o.windowStart != n.windowStart || o.windowEnd != n.windowEnd ||
                (sameLang && o.windowStart == null && o.window != n.window))
            if (!o.appointment && n.appointment) add(Kind.APPOINTMENT_SET, true)
            else add(Kind.APPOINTMENT_CHANGED, sameLang && o.appointment && n.appointment && o.appointmentText != n.appointmentText)
            add(Kind.PLACE, sameLang && o.deliveryPlace != n.deliveryPlace)
            add(Kind.AMOUNT, o.amountDue != n.amountDue)
            add(Kind.TRADE_IN_STATUS, o.tradeInStatusCode != n.tradeInStatusCode)
            add(Kind.TRADE_IN_OFFER, n.tradeInOffer != null && o.tradeInOffer != n.tradeInOffer)

            val oldTasks = o.tasks.associateBy { it.id }
            for (t in n.tasks) {
                // A task Tesla just added is worth a ping if it is actionable (unless we had no tasks at all, e.g. after a /tasks failure).
                val p = oldTasks[t.id] ?: run {
                    if (oldTasks.isNotEmpty() && t.state == TaskState.TODO) changes += Change(Kind.TASK_TODO, o, n, t)
                    continue
                }
                val kind = when {
                    p.state != TaskState.DONE && t.state == TaskState.DONE -> Kind.TASK_DONE
                    p.state == TaskState.LOCKED && t.state == TaskState.TODO -> Kind.TASK_TODO
                    sameLang && p.status != t.status && t.status.isNotEmpty() -> Kind.TASK_INFO
                    else -> null
                }
                kind?.let { changes += Change(it, o, n, t) }
            }
        }
        return Summary(changes, (Differ.diff(old, new).size - changes.size).coerceAtLeast(0))
    }
}

/** Texts of the changes (notifications, widget, history), in the phone's language. */
object ChangeText {
    private val emoji = mapOf(
        Changes.Kind.NEW_ORDER to "🆕", Changes.Kind.VIN_ASSIGNED to "🔑", Changes.Kind.VIN_CHANGED to "🔑",
        Changes.Kind.STATUS to "📦", Changes.Kind.WINDOW to "🚗", Changes.Kind.APPOINTMENT_SET to "📅",
        Changes.Kind.APPOINTMENT_CHANGED to "📅", Changes.Kind.PLACE to "📍", Changes.Kind.AMOUNT to "💶",
        Changes.Kind.TRADE_IN_STATUS to "🔁", Changes.Kind.TRADE_IN_OFFER to "💶", Changes.Kind.TASK_DONE to "✅",
        Changes.Kind.TASK_TODO to "👉", Changes.Kind.TASK_INFO to "ℹ️",
    )

    /** Title without emoji, e.g. "Delivery window changed". */
    fun title(ctx: Context, c: Changes.Change): String = when (c.kind) {
        Changes.Kind.NEW_ORDER -> ctx.getString(R.string.ch_new_order)
        Changes.Kind.VIN_ASSIGNED -> ctx.getString(R.string.ch_vin_assigned)
        Changes.Kind.VIN_CHANGED -> ctx.getString(R.string.ch_vin_changed)
        Changes.Kind.STATUS -> ctx.getString(R.string.ch_status)
        Changes.Kind.WINDOW -> ctx.getString(R.string.ch_window)
        Changes.Kind.APPOINTMENT_SET -> ctx.getString(R.string.ch_appointment_set)
        Changes.Kind.APPOINTMENT_CHANGED -> ctx.getString(R.string.ch_appointment_changed)
        Changes.Kind.PLACE -> ctx.getString(R.string.ch_place)
        Changes.Kind.AMOUNT -> ctx.getString(R.string.ch_amount)
        Changes.Kind.TRADE_IN_STATUS -> ctx.getString(R.string.ch_trade_in)
        Changes.Kind.TRADE_IN_OFFER -> ctx.getString(R.string.ch_trade_in_offer)
        Changes.Kind.TASK_DONE, Changes.Kind.TASK_TODO, Changes.Kind.TASK_INFO -> Labels.task(ctx, c.task!!.id)
    }

    fun titleWithEmoji(ctx: Context, c: Changes.Change) = "${emoji[c.kind]} ${title(ctx, c)}"

    private fun arrow(a: String?, b: String?) = "${a ?: "—"} → ${b ?: "—"}"

    private fun window(ctx: Context, o: OrderInfo?) = when {
        o == null -> null
        o.windowStart != null && o.windowEnd != null -> "${Fmt.dayMonth(o.windowStart)} – ${Fmt.dayMonth(o.windowEnd)}"
        else -> o.window ?: ctx.getString(R.string.window_none)
    }

    private fun money(v: Double?, cur: String) = v?.let { Fmt.money(it, cur) }

    /** Detail line, e.g. "1 Dec – 10 Feb → 10 Dec – 18 Feb". */
    fun detail(ctx: Context, c: Changes.Change): String {
        val o = c.before; val n = c.after
        return when (c.kind) {
            Changes.Kind.NEW_ORDER -> "${n.model} · ${n.reference}"
            Changes.Kind.VIN_ASSIGNED -> n.vin.orEmpty()
            Changes.Kind.VIN_CHANGED -> arrow(o?.vin, n.vin)
            Changes.Kind.STATUS -> arrow(o?.let { Labels.status(ctx, it.statusCode) }, Labels.status(ctx, n.statusCode))
            Changes.Kind.WINDOW -> arrow(window(ctx, o), window(ctx, n))
            Changes.Kind.APPOINTMENT_SET -> listOfNotNull(n.appointmentText, n.deliveryPlace).joinToString(" · ")
                .ifEmpty { ctx.getString(R.string.open_tesla_app) }
            Changes.Kind.APPOINTMENT_CHANGED -> arrow(o?.appointmentText, n.appointmentText)
            Changes.Kind.PLACE -> arrow(o?.deliveryPlace, n.deliveryPlace)
            Changes.Kind.AMOUNT -> arrow(money(o?.amountDue, n.currency), money(n.amountDue, n.currency))
            Changes.Kind.TRADE_IN_STATUS -> arrow(Labels.tradeInStatus(ctx, o?.tradeInStatusCode), Labels.tradeInStatus(ctx, n.tradeInStatusCode))
            Changes.Kind.TRADE_IN_OFFER -> (o?.tradeInOffer?.let { "${money(it, n.currency)} → " } ?: "") + money(n.tradeInOffer, n.currency)
            Changes.Kind.TASK_DONE -> ctx.getString(R.string.ch_task_done)
            Changes.Kind.TASK_TODO -> ctx.getString(R.string.ch_task_todo, c.task!!.status)
            Changes.Kind.TASK_INFO -> c.task!!.status
        }
    }

    /** Notification title: the change itself if alone, otherwise "Car order: 3 updates". */
    fun notificationTitle(ctx: Context, s: Changes.Summary) =
        if (s.important == 1) titleWithEmoji(ctx, s.changes[0])
        else ctx.resources.getQuantityString(R.plurals.notif_updates, s.important, s.important)

    fun notificationLines(ctx: Context, s: Changes.Summary) =
        if (s.important == 1) listOf(detail(ctx, s.changes[0]))
        else s.changes.map { ctx.getString(R.string.ch_line, titleWithEmoji(ctx, it), detail(ctx, it)) }

    /** Short version for the widget banner: "VIN assigned", "VIN assigned + 2 more". */
    fun headline(ctx: Context, s: Changes.Summary): String {
        val first = title(ctx, s.changes[0])
        return if (s.important == 1) first
        else ctx.resources.getQuantityString(R.plurals.headline_more, s.important - 1, first, s.important - 1)
    }
}
