package app.deliveryday

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Vehicle trade-in: Tesla's official offer, typed estimate, and what is left to pay. */
object TradeIn {

    /**
     * Amount of the official offer in the "tradeIn" task, if Tesla made one.
     * The exact field name is unknown until an offer exists: look for a number
     * > 0 under a key like offer/quote/estimate…amount/value/price. The trade-in bonus
     * (tradeInUpliftDetails) is ignored.
     */
    fun officialOffer(tradeIn: JSONObject): Double? {
        val found = mutableListOf<Pair<String, Double>>()
        fun walk(v: Any?, path: String) {
            when (v) {
                is JSONObject -> v.keys().forEach { k -> if (!k.startsWith("tradeInUplift")) walk(v.opt(k), if (path.isEmpty()) k else "$path.$k") }
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), "$path[$i]")
                is Number -> v.toDouble().takeIf { it in 100.0..500_000.0 }?.let { found += path to it }
                is String -> v.replace(Regex("[\\s\\u202f€]"), "").replace(',', '.').toDoubleOrNull()
                    ?.takeIf { it in 100.0..500_000.0 }?.let { found += path to it }
            }
        }
        walk(tradeIn, "")
        val money = Regex("(amount|value|price|montant)", RegexOption.IGNORE_CASE)
        val offer = Regex("(offer|quote|estimate|appraisal)", RegexOption.IGNORE_CASE)
        // Prefer fields that explicitly mention an offer AND an amount.
        return found.firstOrNull { (p, _) -> offer.containsMatchIn(p) && money.containsMatchIn(p.substringAfterLast('.')) }?.second
            ?: found.firstOrNull { (p, _) -> offer.containsMatchIn(p.substringAfterLast('.')) }?.second
    }

    data class Payment(
        /** Trade-in value used (Tesla's offer first, otherwise the estimate). */
        val value: Double?,
        val official: Boolean,
        /** Tesla already deducted the trade-in from its amount: don't subtract it twice. */
        val deductedByTesla: Boolean,
        /** What is actually left to pay. */
        val remaining: Double?,
    )

    /**
     * [baseline] = amount requested by Tesla when the estimate was typed (trade-in not deducted).
     * If the current amount dropped by at least 80 % of the trade-in since, Tesla deducted it.
     */
    fun payment(amountDue: Double?, estimate: Double?, offer: Double?, baseline: Double?): Payment {
        val value = offer ?: estimate
        val deducted = amountDue != null && baseline != null && value != null &&
            baseline - amountDue >= value * 0.8 && abs(baseline - amountDue) <= value * 1.5
        val remaining = when {
            amountDue == null -> null
            value == null || deducted -> amountDue
            else -> amountDue - value
        }
        return Payment(value, offer != null, deducted, remaining)
    }
}
