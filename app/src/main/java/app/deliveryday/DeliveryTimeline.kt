package app.deliveryday

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.ui.res.stringResource

/**
 * Timeline from the order to the end of the delivery window:
 * elapsed time in red, window in amber, months as ticks, "Today" marker.
 */
@Composable
fun DeliveryTimeline(ordered: LocalDate, today: LocalDate, windowStart: LocalDate, windowEnd: LocalDate) {
    val monthOnly = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val tOrder = stringResource(R.string.timeline_order)
    val tEarliest = stringResource(R.string.timeline_earliest)
    val tLatest = stringResource(R.string.timeline_latest)
    val tToday = stringResource(R.string.timeline_today, Fmt.dayMonth(today))
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = TeslaColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(color = TeslaColors.Muted, fontSize = 10.sp)
    val month = TextStyle(color = TeslaColors.Muted.copy(alpha = 0.7f), fontSize = 10.sp)
    val amber = TextStyle(color = TeslaColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val todayStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
        val pad = 6.dp.toPx()
        val left = pad
        val right = size.width - pad
        val axisY = 46.dp.toPx()
        // Position of a date on the axis.
        val totalDays = ChronoUnit.DAYS.between(ordered, windowEnd).coerceAtLeast(1).toFloat()
        fun x(d: LocalDate) = left + (right - left) * (ChronoUnit.DAYS.between(ordered, d) / totalDays).coerceIn(0f, 1f)

        // Track
        val trackH = 6.dp.toPx()
        drawRoundRect(TeslaColors.CardHigh, Offset(left, axisY - trackH / 2), Size(right - left, trackH), CornerRadius(trackH / 2))

        // Delivery window (thicker amber band)
        val bandH = 14.dp.toPx()
        val ws = x(windowStart); val we = x(windowEnd)
        drawRoundRect(TeslaColors.Amber.copy(alpha = 0.28f), Offset(ws, axisY - bandH / 2), Size(we - ws, bandH), CornerRadius(bandH / 2))

        // Elapsed time
        val tx = x(today)
        if (tx > left) drawRoundRect(TeslaColors.Red, Offset(left, axisY - trackH / 2), Size(tx - left, trackH), CornerRadius(trackH / 2))

        // Month ticks (1st of each month) + labels below the axis
        var m = ordered.withDayOfMonth(1).plusMonths(1)
        while (!m.isAfter(windowEnd)) {
            val mx = x(m)
            drawLine(TeslaColors.Line, Offset(mx, axisY + bandH / 2 + 2.dp.toPx()), Offset(mx, axisY + bandH / 2 + 7.dp.toPx()), 1.dp.toPx())
            val t = measurer.measure(m.format(monthOnly).replaceFirstChar(Char::titlecase), month)
            drawText(t, topLeft = Offset((mx - t.size.width / 2).coerceIn(left, right - t.size.width), axisY + bandH / 2 + 9.dp.toPx()))
            m = m.plusMonths(1)
        }

        // Markers above the axis: order, window start and end.
        fun marker(px: Float, color: Color, filled: Boolean) {
            drawCircle(TeslaColors.Card, 7.dp.toPx(), Offset(px, axisY))
            drawCircle(color, 5.dp.toPx(), Offset(px, axisY))
            if (!filled) drawCircle(TeslaColors.Card, 2.5.dp.toPx(), Offset(px, axisY))
        }
        fun above(px: Float, top: String, bottom: String, style: TextStyle, align: Float) {
            val a = measurer.measure(top, caption); val b = measurer.measure(bottom, style)
            val w = maxOf(a.size.width, b.size.width)
            val lx = (px - w * align).coerceIn(left, right - w)
            drawText(a, topLeft = Offset(lx + (w - a.size.width) * align, 0f))
            drawText(b, topLeft = Offset(lx + (w - b.size.width) * align, a.size.height.toFloat()))
        }
        marker(left, TeslaColors.Green, true)
        above(left, tOrder, Fmt.dayMonth(ordered), label, 0f)
        marker(ws, TeslaColors.Amber, false)
        above(ws, tEarliest, Fmt.dayMonth(windowStart), amber, 0.5f)
        marker(we, TeslaColors.Amber, false)
        above(we, tLatest, Fmt.dayMonth(windowEnd), amber, 1f)

        // Today: red dot with a halo, dashed line down to a pill.
        drawCircle(TeslaColors.Red.copy(alpha = 0.25f), 11.dp.toPx(), Offset(tx, axisY))
        drawCircle(Color.White, 6.dp.toPx(), Offset(tx, axisY))
        drawCircle(TeslaColors.Red, 4.dp.toPx(), Offset(tx, axisY))
        val pillTop = axisY + 40.dp.toPx()
        drawLine(
            TeslaColors.Red.copy(alpha = 0.6f), Offset(tx, axisY + 11.dp.toPx()), Offset(tx, pillTop), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
        val t = measurer.measure(tToday, todayStyle)
        val padH = 8.dp.toPx(); val padV = 4.dp.toPx()
        val pw = t.size.width + padH * 2; val ph = t.size.height + padV * 2
        val px = (tx - pw / 2).coerceIn(0f, size.width - pw)
        drawPill(px, pillTop, pw, ph)
        drawText(t, topLeft = Offset(px + padH, pillTop + padV))
    }
}

private fun DrawScope.drawPill(x: Float, y: Float, w: Float, h: Float) =
    drawRoundRect(TeslaColors.Red, Offset(x, y), Size(w, h), CornerRadius(h / 2))
