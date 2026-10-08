package app.deliveryday

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.text.DateFormatSymbols
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class TaskState { DONE, TODO, LOCKED }

/** A Tesla task ("tradeIn", "scheduling"…); [status] and [detail] are Tesla's own texts, already localized. */
data class TaskInfo(val id: String, val status: String, val detail: String, val state: TaskState)

/**
 * Everything the UI needs about one order. Enumerations are kept as Tesla codes
 * (status, paint, options, tasks…) and turned into text by [Labels], in the phone's language.
 */
data class OrderInfo(
    val reference: String,
    /** Language of Tesla's texts in this snapshot (null for old snapshots). */
    val lang: String?,
    val model: String,
    val countryCode: String?,
    val trimCode: String?,
    val statusCode: String,
    val paintCode: String?,
    val vin: String?,
    val orderedOn: LocalDate?,
    /** Delivery window as displayed by Tesla, e.g. "10 Décembre - 18 Février". */
    val window: String?,
    val windowStart: LocalDate?,
    val windowEnd: LocalDate?,
    val deliveryPlace: String?,
    val deliveryTypeCode: String?,
    val appointment: Boolean,
    /** Appointment text as given by Tesla (date, time…), once one is set. */
    val appointmentText: String?,
    val amountDue: Double?,
    val currency: String,
    val tradeIn: Boolean,
    val tradeInStatusCode: String?,
    /** Amount of Tesla's official trade-in offer, once there is one. */
    val tradeInOffer: Double?,
    val paymentMethod: String?,
    val owners: List<String>,
    val optionCodes: List<String>,
    /** Main tasks (delivery details, financing, registration) completed. */
    val paperworkDone: Boolean,
    val finalPaymentDone: Boolean,
    val delivered: Boolean,
    val tasks: List<TaskInfo>,
) {
    val paintColor: Color? get() = paintCode?.let { Orders.paintColors[it] }
}

/** Extracts the useful information from the raw snapshot built by [TeslaApi.fetchOrders]. */
object Orders {
    private val models = mapOf("my" to "Model Y", "m3" to "Model 3", "ms" to "Model S", "mx" to "Model X", "ct" to "Cybertruck")

    /** Paint swatches; names are in the string resources (paint_<code>). */
    val paintColors = mapOf(
        "PPSW" to Color(0xFFF4F4F2), "PBSB" to Color(0xFF111111), "PMNG" to Color(0xFF3B3F45),
        "PN01" to Color(0xFF4A4D52), "PPSB" to Color(0xFF1F4E8C), "PR01" to Color(0xFFA3121B),
        "PPMR" to Color(0xFFA3121B), "PMSS" to Color(0xFFB9BCC0), "PN00" to Color(0xFF9EA2A6),
    )

    /** Trim codes (source: tesla-info.com decoder). */
    val trimCodes = setOf("MTY85")

    private val modelOptionCodes = setOf("MDLY", "MDL3", "MDLS", "MDLX")

    fun parse(snapshot: String?, today: LocalDate = LocalDate.now()): List<OrderInfo> {
        if (snapshot == null) return emptyList()
        val root = runCatching { JSONObject(snapshot) }.getOrNull() ?: return emptyList()
        return root.keys().asSequence().map { rn ->
            val o = root.getJSONObject(rn)
            val order = o.optJSONObject("order") ?: JSONObject()
            val t = o.optJSONObject("tasks")?.optJSONObject("tasks") ?: JSONObject()
            fun task(id: String) = t.optJSONObject(id) ?: JSONObject()
            fun s(j: JSONObject, k: String) = j.optString(k).takeIf { it.isNotBlank() && it != "null" }

            val sched = task("scheduling")
            val pay = task("finalPayment")
            val reg = task("registration").optJSONObject("regData")
            val codes = order.optString("mktOptions").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val window = s(sched, "deliveryWindowDisplay")
            val (ws, we) = parseWindow(window, today)

            val tasks = t.keys().asSequence().mapNotNull { id -> t.optJSONObject(id)?.let { id to it } }
                .sortedBy { it.second.optInt("order", 99) }
                .map { (id, j) ->
                    val card = j.optJSONObject("card") ?: JSONObject()
                    val state = when {
                        j.optBoolean("complete") -> TaskState.DONE
                        j.optBoolean("enabled") -> TaskState.TODO
                        else -> TaskState.LOCKED
                    }
                    TaskInfo(id, s(card, "title").orEmpty(), s(card, "subtitle").orEmpty(), state)
                }.toList()

            OrderInfo(
                reference = rn,
                lang = s(o, "lang"),
                model = models[order.optString("modelCode")] ?: order.optString("modelCode").uppercase(),
                countryCode = s(order, "countryCode"),
                trimCode = codes.firstOrNull { it in trimCodes },
                statusCode = order.optString("orderStatus"),
                paintCode = codes.firstOrNull { it in paintColors },
                vin = s(order, "vin"),
                orderedOn = s(task("tradeIn"), "orderPlacedDate")?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() },
                window = window,
                windowStart = ws,
                windowEnd = we,
                deliveryPlace = s(sched, "deliveryAddressTitle"),
                deliveryTypeCode = s(sched, "deliveryType"),
                appointment = sched.optBoolean("isValidAppointment"),
                appointmentText = appointmentText(sched),
                amountDue = pay.optDouble("amountDue").takeIf { !it.isNaN() && it > 0 },
                currency = pay.optJSONObject("currencyFormat")?.optString("currencyCode")?.takeIf { it.length == 3 } ?: "EUR",
                tradeIn = task("tradeIn").optString("tradeInIntent") == "Yes",
                tradeInOffer = TradeIn.officialOffer(task("tradeIn")),
                tradeInStatusCode = s(task("tradeIn"), "status"),
                paymentMethod = s(task("financing").optJSONObject("card") ?: JSONObject(), "messageBody"),
                owners = listOfNotNull(reg?.optJSONObject("owner"), reg?.optJSONObject("coOwner"))
                    .mapNotNull { it.optJSONObject("user") }
                    .map { "${it.optString("firstName").split(" ").first().lowercase().replaceFirstChar(Char::titlecase)} ${it.optString("lastName").lowercase().replaceFirstChar(Char::titlecase)}".trim() },
                optionCodes = codes.filter { it !in paintColors && it !in trimCodes && it !in modelOptionCodes },
                paperworkDone = listOf("deliveryDetails", "financing", "registration").all { task(it).optBoolean("complete") },
                finalPaymentDone = pay.optBoolean("complete"),
                delivered = task("deliveryAcceptance").optBoolean("complete") || order.optString("orderStatus") == "DELIVERED",
                tasks = tasks,
            )
        }.toList()
    }

    /**
     * The appointment format is unknown until Tesla sets one: use "appointment…date/time"
     * fields when present, otherwise the text of Tesla's card.
     */
    private fun appointmentText(sched: JSONObject): String? {
        if (!sched.optBoolean("isValidAppointment")) return null
        val fields = sched.keys().asSequence()
            .filter { Regex("appointment.*(date|time)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .mapNotNull { k -> sched.optString(k).takeIf { it.isNotBlank() && it != "null" } }
            .toList()
        if (fields.isNotEmpty()) return fields.joinToString(" · ")
        val card = sched.optJSONObject("card") ?: return null
        return card.optString("messageBody").takeIf { it.isNotBlank() && it != "null" && it != sched.optString("deliveryWindowDisplay") }
            ?: card.optString("subtitle").takeIf { it.isNotBlank() && it != "null" }
    }

    private fun normalize(s: String) =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").trimEnd('.')

    /** Month names (full and short) of the supported languages, without accents → month number. */
    private val monthNames: List<Pair<String, Int>> by lazy {
        listOf("en", "fr", "de", "es", "it", "nl", "pt", "sv", "da", "nb", "pl").flatMap { lang ->
            val sym = DateFormatSymbols(Locale.forLanguageTag(lang))
            (sym.months.take(12) + sym.shortMonths.take(12)).mapIndexed { i, name -> normalize(name) to (i % 12) + 1 }
        }.filter { it.first.length >= 3 }.distinct()
    }

    /** "décembre" → 12, "Dec" → 12, "juil." → 7. The longest matching name wins ("juin" ≠ "juillet"). */
    private fun month(token: String): Int? {
        val t = normalize(token)
        if (t.length < 3) return null
        return monthNames.filter { (name, _) -> name == t || name.startsWith(t) || t.startsWith(name) }
            .maxByOrNull { it.first.length }?.second
    }

    /**
     * "10 Décembre - 18 Février", "Dec 10 - Feb 18", "10. Dezember – 18. Februar"… → dates.
     * Tesla gives no year: for the start take the occurrence closest to today (the window can be
     * ongoing or upcoming), for the end the first occurrence on or after the start.
     */
    internal fun parseWindow(w: String?, today: LocalDate): Pair<LocalDate?, LocalDate?> {
        if (w == null) return null to null
        val parts = w.split(Regex("\\s[-–—]\\s|[–—]|\\s-|-\\s")).map { it.trim() }.filter { it.isNotEmpty() }
        // (day, month, optional year), in any order ("10 December" or "December 10").
        fun parts(p: String): Triple<Int, Int, Int?>? {
            val tokens = p.split(Regex("[\\s,./]+")).filter { it.isNotEmpty() }
            val numbers = tokens.mapNotNull { it.toIntOrNull() }
            val m = tokens.firstNotNullOfOrNull { if (it.toIntOrNull() == null) month(it) else null } ?: return null
            val day = numbers.firstOrNull { it in 1..31 } ?: return null
            return Triple(day, m, numbers.firstOrNull { it > 1900 })
        }
        fun of(y: Int, m: Int, d: Int) = runCatching { LocalDate.of(y, m, d) }.getOrNull()

        val start = parts.getOrNull(0)?.let(::parts)?.let { (d, m, y) ->
            if (y != null) of(y, m, d)
            else (today.year - 1..today.year + 1).mapNotNull { of(it, m, d) }
                .minByOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(today, it)) }
        }
        val end = parts.getOrNull(1)?.let(::parts)?.let { (d, m, y) ->
            if (y != null) return@let of(y, m, d)
            val from = start ?: today
            var e = of(from.year, m, d) ?: return@let null
            if (e.isBefore(from)) e = e.plusYears(1)
            e
        }
        return start to end
    }
}
