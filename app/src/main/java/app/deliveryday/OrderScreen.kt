package app.deliveryday

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** The 4 main steps, in plain words (same as the widget). */
fun simpleSteps(o: OrderInfo) = listOf(true, o.vin != null, o.appointment, o.delivered)

@Composable
private fun plural(id: Int, n: Int, vararg args: Any): String =
    LocalContext.current.resources.getQuantityString(id, n, *args)

/**
 * Screen of one order, top to bottom: anything new? · the car · when? · where are we?
 * · payment · history · tasks and configuration (folded).
 */
@Composable
fun OrderView(
    o: OrderInfo, tradeInEstimate: Double?, tradeInBaseline: Double?, onTradeInEstimate: (Double?) -> Unit,
    lastNews: String?, lastNewsAt: Long, history: List<Store.Event>,
    today: LocalDate = LocalDate.now(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NewsBanner(lastNews, lastNewsAt, o.orderedOn, today)
        Hero(o)
        DeliveryCard(o, today)
        StepsCard(o)
        if (o.amountDue != null || o.paymentMethod != null || o.tradeIn) PaymentCard(o, tradeInEstimate, tradeInBaseline, onTradeInEstimate)
        HistoryCard(history, o.orderedOn)
        TasksCard(o)
        ConfigCard(o)
    }
}

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TeslaColors.Card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = TeslaColors.Muted, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, color = TeslaColors.Muted, fontSize = 12.sp) }
        }
        content()
    }
}

/** Foldable section: a one-line summary, the details on tap. */
@Composable
private fun Foldable(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TeslaColors.Card)
            .clickable { open = !open }.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), color = TeslaColors.Muted, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                Text(summary, color = TeslaColors.Text, fontSize = 14.sp)
            }
            Text(if (open) "▲" else "▼", color = TeslaColors.Muted, fontSize = 12.sp)
        }
        AnimatedVisibility(open) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
    }
}

/** Like the widget: the latest news (highlighted for 7 days) or "Nothing new since…". */
@Composable
private fun NewsBanner(news: String?, newsAt: Long, orderedOn: LocalDate?, today: LocalDate) {
    val ctx = LocalContext.current
    val newsDay = newsAt.takeIf { it > 0 }?.let(Fmt::day)
    val age = newsDay?.let { ChronoUnit.DAYS.between(it, today) }
    val fresh = news != null && age != null && age <= 7
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (fresh) TeslaColors.Amber.copy(alpha = 0.16f) else TeslaColors.Card)
            .then(if (fresh) Modifier.border(1.dp, TeslaColors.Amber.copy(alpha = 0.5f), shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (fresh) "🆕" else "😴", fontSize = 20.sp)
        Column {
            Text(
                if (fresh) news.dropWhile { !it.isLetter() } else stringResource(R.string.news_none),
                color = TeslaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            val since = if (fresh) Fmt.ago(ctx, age) else (newsDay ?: orderedOn)?.let { stringResource(R.string.news_since, Fmt.dayMonth(it)) }
            since?.let { Text(it, color = TeslaColors.Muted, fontSize = 12.sp) }
        }
    }
}

/** Compact header: the car and its identity, without taking the delivery's room. */
@Composable
private fun Hero(o: OrderInfo) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(TeslaColors.palette.heroTop, TeslaColors.palette.heroBottom)))
            .border(1.dp, TeslaColors.Line.copy(alpha = 0.5f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.model, color = TeslaColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Chip(Labels.status(ctx, o.statusCode), TeslaColors.Green)
        }
        // On its own line: next to the model it vanished with large system fonts.
        Labels.trim(ctx, o.trimCode)?.let { Text(it, color = TeslaColors.Muted, fontSize = 15.sp) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            o.paintColor?.let { color ->
                Box(Modifier.size(12.dp).clip(CircleShape).background(color).border(1.dp, TeslaColors.Line, CircleShape))
                Text(Labels.paint(ctx, o.paintCode).orEmpty(), color = TeslaColors.Text, fontSize = 13.sp)
                Text("·", color = TeslaColors.Muted, fontSize = 13.sp)
            }
            Text(o.reference, color = TeslaColors.Muted, fontSize = 13.sp)
        }
        Text(
            o.vin?.let { stringResource(R.string.vin_value, it) } ?: stringResource(R.string.vin_pending),
            color = if (o.vin != null) TeslaColors.Text else TeslaColors.Muted, fontSize = 13.sp,
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun DeliveryCard(o: OrderInfo, today: LocalDate) {
    val ctx = LocalContext.current
    Section(stringResource(R.string.section_delivery)) {
        if (o.windowStart != null && o.windowEnd != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.dayMonth(o.windowStart), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TeslaColors.Text)
                Text("  →  ", fontSize = 20.sp, color = TeslaColors.Muted)
                Text(Fmt.dayMonth(o.windowEnd), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TeslaColors.Text)
            }
            val toStart = ChronoUnit.DAYS.between(today, o.windowStart).toInt()
            val toEnd = ChronoUnit.DAYS.between(today, o.windowEnd).toInt()
            Text(
                when {
                    toStart > 0 -> plural(R.plurals.window_opens_in, toStart, toStart)
                    toEnd >= 0 -> plural(R.plurals.window_open_ends_in, toEnd, toEnd)
                    else -> stringResource(R.string.window_passed)
                },
                color = TeslaColors.Amber, fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            DeliveryTimeline(o.orderedOn ?: today.minusDays(1), today, o.windowStart, o.windowEnd)
        } else {
            Text(o.window ?: stringResource(R.string.window_unknown), fontSize = 20.sp, color = TeslaColors.Text)
        }
        HorizontalDivider(color = TeslaColors.Line)
        o.deliveryPlace?.let { InfoRow(stringResource(R.string.label_place), it) }
        Labels.deliveryType(ctx, o.deliveryTypeCode)?.let { InfoRow(stringResource(R.string.label_method), it) }
        InfoRow(stringResource(R.string.label_appointment), when {
            o.appointmentText != null -> o.appointmentText
            o.appointment -> stringResource(R.string.appointment_set_details)
            else -> stringResource(R.string.appointment_later)
        })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = TeslaColors.Muted, fontSize = 14.sp, modifier = Modifier.width(110.dp))
        Text(value, color = TeslaColors.Text, fontSize = 14.sp)
    }
}

/** The 4 steps as a horizontal timeline: linked dots, done in green, current step highlighted. */
@Composable
private fun StepsCard(o: OrderInfo) {
    val done = simpleSteps(o)
    val labels = listOf(R.string.step_ordered, R.string.step_assigned, R.string.step_appointment, R.string.step_delivered)
    val current = done.indexOfFirst { !it }
    Section(stringResource(R.string.section_progress)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            labels.forEachIndexed { i, label ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Links to the previous and next steps.
                        val before = if (i == 0) Color.Transparent else if (done[i]) TeslaColors.Green else TeslaColors.Line
                        val after = if (i == labels.lastIndex) Color.Transparent else if (done[i + 1]) TeslaColors.Green else TeslaColors.Line
                        Box(Modifier.weight(1f).height(3.dp).background(before))
                        StateDot(when { done[i] -> TaskState.DONE; i == current -> TaskState.TODO; else -> TaskState.LOCKED }, size = 28)
                        Box(Modifier.weight(1f).height(3.dp).background(after))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(label), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 15.sp,
                        color = when { done[i] -> TeslaColors.Green; i == current -> TeslaColors.Text; else -> TeslaColors.Muted },
                        fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun StateDot(state: TaskState, size: Int = 22) {
    val (bg, fg, glyph) = when (state) {
        TaskState.DONE -> Triple(TeslaColors.Green.copy(alpha = 0.18f), TeslaColors.Green, "✓")
        TaskState.TODO -> Triple(TeslaColors.Amber.copy(alpha = 0.18f), TeslaColors.Amber, "•")
        TaskState.LOCKED -> Triple(TeslaColors.CardHigh, TeslaColors.Muted, "")
    }
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(glyph, color = fg, fontSize = (size * 0.55).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PaymentCard(o: OrderInfo, estimate: Double?, baseline: Double?, onEstimate: (Double?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var input by remember(estimate) { mutableStateOf(estimate?.let { "%.0f".format(it) } ?: "") }
    val p = TradeIn.payment(o.amountDue, estimate, o.tradeInOffer, baseline)
    val ctx = LocalContext.current
    Section(stringResource(R.string.section_payment)) {
        p.remaining?.let {
            Text(
                stringResource(when {
                    p.deductedByTesla -> R.string.remaining_included
                    p.value != null -> R.string.remaining_after_trade_in
                    else -> R.string.remaining
                }),
                color = TeslaColors.Muted, fontSize = 13.sp,
            )
            Text(Fmt.money(it, o.currency), color = TeslaColors.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        o.paymentMethod?.let { InfoRow(stringResource(R.string.label_method), it) }
        if (o.tradeIn || p.value != null) {
            HorizontalDivider(color = TeslaColors.Line)
            if (p.value != null && o.amountDue != null) {
                val label = stringResource(if (p.official) R.string.trade_in_official else R.string.trade_in_estimate)
                if (p.deductedByTesla) {
                    AmountRow(label, Fmt.money(p.value, o.currency), TeslaColors.Green)
                    Text(stringResource(R.string.trade_in_deducted), color = TeslaColors.Green, fontSize = 12.sp)
                } else {
                    AmountRow(stringResource(R.string.amount_requested), Fmt.money(o.amountDue, o.currency), TeslaColors.Text)
                    AmountRow(label, "− " + Fmt.money(p.value, o.currency), TeslaColors.Green)
                }
                if (p.official && estimate != null && abs(estimate - p.value) >= 1)
                    Text(stringResource(R.string.your_estimate, Fmt.money(estimate, o.currency)), color = TeslaColors.Muted, fontSize = 12.sp)
            }
            if (p.official) {
                // The official offer replaces the estimate: nothing to type anymore.
            } else if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        input, { input = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                        label = { Text(stringResource(R.string.trade_in_input, o.currency)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        onEstimate(input.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }); editing = false
                    }) { Text(stringResource(R.string.ok)) }
                }
            } else {
                TextButton(onClick = { editing = true }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(if (estimate == null) R.string.trade_in_add else R.string.trade_in_edit), color = TeslaColors.Muted)
                }
            }
            Labels.tradeInStatus(ctx, o.tradeInStatusCode)?.let {
                Text(stringResource(R.string.trade_in_status, it), color = TeslaColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = TeslaColors.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Everything that changed since the order, most recent first. */
@Composable
private fun HistoryCard(history: List<Store.Event>, orderedOn: LocalDate?) {
    var all by rememberSaveable { mutableStateOf(false) }
    val rows = history.map { Triple(Fmt.day(it.at), it.title.dropWhile { c -> !c.isLetter() }, it.detail) } +
        listOfNotNull(orderedOn?.let { Triple(it, stringResource(R.string.history_ordered), "") })
    Section(stringResource(R.string.section_history), trailing = if (history.isEmpty()) null else plural(R.plurals.history_count, history.size, history.size)) {
        if (history.isEmpty()) Text(stringResource(R.string.history_empty), color = TeslaColors.Muted, fontSize = 13.sp)
        (if (all) rows else rows.take(4)).forEach { (day, title, detail) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Fmt.dayMonth(day), color = TeslaColors.Muted, fontSize = 13.sp, modifier = Modifier.width(60.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = TeslaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (detail.isNotBlank()) Text(detail, color = TeslaColors.Muted, fontSize = 12.sp)
                }
            }
        }
        if (rows.size > 4) TextButton(onClick = { all = !all }, contentPadding = PaddingValues(0.dp)) {
            Text(if (all) stringResource(R.string.see_less) else stringResource(R.string.see_all, rows.size), color = TeslaColors.Muted)
        }
    }
}

@Composable
private fun TasksCard(o: OrderInfo) {
    val done = o.tasks.count { it.state == TaskState.DONE }
    val todo = o.tasks.filter { it.state == TaskState.TODO }
    // What needs an action always stays visible; the rest unfolds.
    if (todo.isNotEmpty()) Section(stringResource(R.string.section_todo)) { todo.forEach { TaskRow(it) } }
    val rest = if (todo.isEmpty()) stringResource(R.string.tasks_nothing) else plural(R.plurals.tasks_todo, todo.size, todo.size)
    Foldable(stringResource(R.string.section_tasks), stringResource(R.string.tasks_summary, done, o.tasks.size, rest)) {
        o.tasks.forEach { TaskRow(it) }
    }
}

@Composable
private fun TaskRow(t: TaskInfo) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 2.dp)) { StateDot(t.state) }
        Column(Modifier.weight(1f)) {
            Text(Labels.task(LocalContext.current, t.id), color = TeslaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            // Tesla's text is only colored when it asks for an action, otherwise neutral
            // (a completed task may read "Contracts not available").
            if (t.status.isNotEmpty()) Text(t.status, color = if (t.state == TaskState.TODO) TeslaColors.Amber else TeslaColors.Muted, fontSize = 13.sp)
            if (t.detail.isNotEmpty() && t.state == TaskState.TODO) Text(t.detail, color = TeslaColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ConfigCard(o: OrderInfo) {
    val ctx = LocalContext.current
    val summary = listOfNotNull(Labels.trim(ctx, o.trimCode), Labels.paint(ctx, o.paintCode)).joinToString(" · ")
        .ifEmpty { stringResource(R.string.config_default) }
    Foldable(stringResource(R.string.section_config), summary) {
        if (o.owners.isNotEmpty()) InfoRow(stringResource(R.string.label_owners), o.owners.joinToString("\n"))
        o.orderedOn?.let { InfoRow(stringResource(R.string.label_ordered_on), Fmt.dayMonthYear(it)) }
        o.optionCodes.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { code ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TeslaColors.CardHigh).padding(10.dp)) {
                        Text(Labels.option(ctx, code), color = TeslaColors.Text, fontSize = 13.sp)
                        Text(code, color = TeslaColors.Muted, fontSize = 11.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
