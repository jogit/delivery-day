package app.deliveryday

import android.content.Context
import android.text.format.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** Turns Tesla codes into text in the phone's language (unknown codes are shown as-is). */
object Labels {
    private val status = mapOf(
        "BOOKED" to R.string.status_booked, "CONFIRMED" to R.string.status_booked,
        "ALLOCATED" to R.string.status_allocated, "IN_TRANSIT" to R.string.status_in_transit,
        "DELIVERED" to R.string.status_delivered, "CANCELLED" to R.string.status_cancelled,
    )
    private val tasks = mapOf(
        "deliveryDetails" to R.string.task_delivery_details, "incentives" to R.string.task_incentives,
        "tradeIn" to R.string.task_trade_in, "financing" to R.string.task_financing,
        "registration" to R.string.task_registration, "scheduling" to R.string.task_scheduling,
        "insurance" to R.string.task_insurance, "finalPayment" to R.string.task_final_payment,
        "deliveryAcceptance" to R.string.task_delivery_acceptance,
    )
    private val deliveryTypes = mapOf(
        "PICKUP_SERVICE_CENTER" to R.string.delivery_pickup, "DELIVERY_TO_ADDRESS" to R.string.delivery_home,
    )
    private val tradeInStatus = mapOf(
        "NO_ESTIMATE_SELF_INSPECTION" to R.string.tradein_no_offer, "OFFER_READY" to R.string.tradein_offer_ready,
        "OFFER_ACCEPTED" to R.string.tradein_offer_accepted, "OFFER_EXPIRED" to R.string.tradein_offer_expired,
        "FINAL_OFFER_PENDING" to R.string.tradein_offer_pending,
    )
    private val trims = mapOf("MTY85" to R.string.trim_mty85)
    private val paints = mapOf(
        "PPSW" to R.string.paint_ppsw, "PBSB" to R.string.paint_pbsb, "PMNG" to R.string.paint_pmng,
        "PN01" to R.string.paint_pn01, "PPSB" to R.string.paint_ppsb, "PR01" to R.string.paint_pr01,
        "PPMR" to R.string.paint_ppmr, "PMSS" to R.string.paint_pmss, "PN00" to R.string.paint_pn00,
    )
    // Approximate labels: Tesla does not publish an official option code list.
    private val options = mapOf(
        "STY5B" to R.string.opt_5_seats, "STY7B" to R.string.opt_7_seats,
        "APBS" to R.string.opt_basic_autopilot, "APF2" to R.string.opt_fsd,
        "SC04" to R.string.opt_supercharging_paid, "TW01" to R.string.opt_towing,
        "IBB6" to R.string.opt_all_black_interior, "IBB1" to R.string.opt_black_interior,
        "IPW1" to R.string.opt_black_white_interior,
        "WY18P" to R.string.opt_wheels_18_aperture, "WY19P" to R.string.opt_wheels_19, "WY20P" to R.string.opt_wheels_20,
        "CPF0" to R.string.opt_connectivity_standard, "CPF1" to R.string.opt_connectivity_premium,
    )

    private fun Context.lookup(map: Map<String, Int>, code: String?) = code?.let { c -> map[c]?.let(::getString) ?: c }

    fun status(ctx: Context, code: String) = ctx.lookup(status, code).orEmpty()
    fun task(ctx: Context, id: String) = ctx.lookup(tasks, id).orEmpty()
    fun deliveryType(ctx: Context, code: String?) = code?.let { deliveryTypes[it] }?.let(ctx::getString)
    fun tradeInStatus(ctx: Context, code: String?) = code?.let { c ->
        tradeInStatus[c]?.let(ctx::getString) ?: c.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
    }
    fun trim(ctx: Context, code: String?) = ctx.lookup(trims, code)
    /** Short trim name for headers ("RWD" from "RWD · Standard"). */
    fun trimShort(ctx: Context, code: String?) = trim(ctx, code)?.substringBefore(" ·")
    fun paint(ctx: Context, code: String?) = ctx.lookup(paints, code)
    fun option(ctx: Context, code: String) = ctx.lookup(options, code).orEmpty()
}

/** Dates, times and amounts formatted for the phone's locale. */
object Fmt {
    private fun locale() = Locale.getDefault()

    /** "10 Dec" / "Dec 10" / "10 déc." depending on the locale. */
    fun dayMonth(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(), "dMMM"), locale()))

    fun dayMonthYear(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(), "dMMMyyyy"), locale()))

    fun time(ctx: Context, ms: Long): String = DateFormat.getTimeFormat(ctx).format(java.util.Date(ms))

    fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun money(v: Double, currency: String): String =
        NumberFormat.getCurrencyInstance(locale()).apply { this.currency = Currency.getInstance(currency) }.format(v)

    /** "Updated at 15:07", "Updated yesterday at…", "Updated 8 Oct at…"; empty if never. */
    fun updated(ctx: Context, ms: Long, today: LocalDate = LocalDate.now()): String {
        if (ms == 0L) return ""
        val t = time(ctx, ms)
        return when (day(ms)) {
            today -> ctx.getString(R.string.updated_at, t)
            today.minusDays(1) -> ctx.getString(R.string.updated_yesterday, t)
            else -> ctx.getString(R.string.updated_on, dayMonth(day(ms)), t)
        }
    }

    /** "today", "yesterday", "3 days ago". */
    fun ago(ctx: Context, days: Long): String = when (days) {
        0L -> ctx.getString(R.string.ago_today)
        1L -> ctx.getString(R.string.ago_yesterday)
        else -> ctx.resources.getQuantityString(R.plurals.ago_days, days.toInt(), days.toInt())
    }
}
