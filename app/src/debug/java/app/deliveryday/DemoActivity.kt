package app.deliveryday

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Debug builds only: fills the app with a fictitious order (no Tesla account, no network) to take
 * screenshots. adb shell am start -n app.deliveryday/.DemoActivity
 */
class DemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fr = Locale.getDefault().language == "fr"
        val today = LocalDate.now()
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        val windowFormat = DateTimeFormatter.ofPattern(if (fr) "d MMMM" else "MMMM d", Locale.getDefault())
        val window = "${today.plusDays(12).format(windowFormat)} - ${today.plusDays(40).format(windowFormat)}"
            .replaceFirstChar(Char::titlecase)

        fun task(order: Int, complete: Boolean, enabled: Boolean, title: String, subtitle: String = "") =
            JSONObject().put("order", order).put("complete", complete).put("enabled", enabled)
                .put("card", JSONObject().put("title", title).put("subtitle", subtitle))
        val done = if (fr) "Terminé" else "Complete"
        val later = if (fr) "Disponible plus tard" else "Available later"

        val tasks = JSONObject()
            .put("deliveryDetails", task(1, true, true, done))
            .put("incentives", task(2, true, true, done))
            .put("tradeIn", task(3, false, false, if (fr) "Offre en préparation" else "Offer in progress")
                .put("tradeInIntent", "Yes").put("status", "NO_ESTIMATE_SELF_INSPECTION")
                .put("orderPlacedDate", "${today.minusDays(41)}T10:00:00.00000"))
            .put("financing", task(4, true, true, done).also {
                it.getJSONObject("card").put("messageBody", if (fr) "Comptant" else "Cash")
            })
            .put("registration", task(5, true, true, done)
                .put("regData", JSONObject().put("owner", JSONObject().put("user", JSONObject().put("firstName", "ALEX").put("lastName", "MARTIN")))))
            .put("scheduling", task(6, false, false, later)
                .put("deliveryWindowDisplay", window).put("deliveryType", "PICKUP_SERVICE_CENTER")
                .put("deliveryAddressTitle", if (fr) "Centre de Livraison Tesla Lyon" else "Tesla Delivery Center Lyon")
                .put("isValidAppointment", false))
            .put("insurance", task(7, false, true, if (fr) "Fournir un justificatif d'assurance" else "Provide proof of insurance",
                if (fr) "À envoyer avant la livraison" else "Upload it before delivery"))
            .put("finalPayment", task(10, false, false, later)
                .put("amountDue", 41990.0).put("currencyFormat", JSONObject().put("currencyCode", "EUR")))
            .put("deliveryAcceptance", task(11, false, false, later))

        val order = JSONObject().put("referenceNumber", "RN-DEMO-0001").put("orderStatus", "BOOKED")
            .put("modelCode", "my").put("countryCode", "FR").put("vin", "DEMO-VIN-12345678")
            .put("mktOptions", "APBS,IBB6,PPSB,SC04,MDLY,WY19P,MTY85,STY5B,CPF0,TW01")
        val snapshot = JSONObject().put("RN-DEMO-0001", JSONObject().put("order", order)
            .put("tasks", JSONObject().put("tasks", tasks)).put("lang", Locale.getDefault().language))

        Store(this).apply {
            clearAll()
            demo = true
            refreshToken = "demo"
            this.snapshot = snapshot.toString()
            lastCheck = now - 12 * 60_000
            tradeInEstimate = 8500.0
            tradeInBaseline = 41990.0
            lastNews = getString(R.string.ch_vin_assigned)
            lastNewsAt = now - 2 * day
            addHistory(listOf(getString(R.string.task_financing) to getString(R.string.ch_task_done)), now - 20 * day)
            addHistory(listOf(getString(R.string.ch_window) to (if (fr) "5 nov. – 2 déc. → " else "Nov 5 – Dec 2 → ") +
                "${Fmt.dayMonth(today.plusDays(12))} – ${Fmt.dayMonth(today.plusDays(40))}"), now - 9 * day)
            addHistory(listOf(getString(R.string.ch_vin_assigned) to "DEMO-VIN-12345678"), now - 2 * day)
        }
        OrderWidget.updateAll(this)

        // With "notify" extra: a real notification, computed from an earlier fictitious state
        // (no VIN yet, previous delivery window) exactly like a background check would.
        if (intent.getBooleanExtra("notify", false)) {
            val before = JSONObject(snapshot.toString())
            val o = before.getJSONObject("RN-DEMO-0001")
            o.getJSONObject("order").remove("vin")
            o.getJSONObject("tasks").getJSONObject("tasks").getJSONObject("scheduling")
                .put("deliveryWindowDisplay", "${today.plusDays(28).format(windowFormat)} - ${today.plusDays(55).format(windowFormat)}"
                    .replaceFirstChar(Char::titlecase))
            val s = Changes.summarize(before.toString(), snapshot.toString())
            CheckWorker.notify(this, ChangeText.notificationTitle(this, s), ChangeText.notificationLines(this, s))
        }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
