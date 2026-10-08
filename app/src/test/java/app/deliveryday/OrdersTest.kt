package app.deliveryday

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

const val REF = "RN000000001"

/** Minimal snapshot in the format returned by TeslaApi.fetchOrders(). */
fun snapshot(
    window: String? = "10 Décembre - 18 Février",
    vin: String? = null,
    amountDue: Double = 33983.76,
    appointment: Boolean = false,
    financingComplete: Boolean = true,
    lang: String = "fr",
    financingText: String? = null,
): String {
    val order = JSONObject()
        .put("referenceNumber", REF).put("orderStatus", "BOOKED").put("modelCode", "my").put("countryCode", "FR")
        .put("mktOptions", "APBS,IBB6,PPSW,SC04,MDLY,WY18P,MTY85,STY5B,CPF0,TW01")
    vin?.let { order.put("vin", it) }
    val scheduling = JSONObject().put("isValidAppointment", appointment).put("order", 6)
        .put("deliveryAddressTitle", "Tesla Delivery Center Lyon").put("deliveryType", "PICKUP_SERVICE_CENTER")
        .put("card", JSONObject().put("title", "Check back later"))
    window?.let { scheduling.put("deliveryWindowDisplay", it) }
    val tasks = JSONObject()
        .put("scheduling", scheduling)
        .put("financing", JSONObject().put("complete", financingComplete).put("enabled", true).put("order", 4)
            .put("card", JSONObject().put("title", financingText ?: if (financingComplete) "Complete" else "To do").put("messageBody", "Cash")))
        .put("finalPayment", JSONObject().put("amountDue", amountDue).put("order", 10)
            .put("currencyFormat", JSONObject().put("currencyCode", "EUR")))
        .put("tradeIn", JSONObject().put("tradeInIntent", "Yes").put("status", "NO_ESTIMATE_SELF_INSPECTION")
            .put("orderPlacedDate", "2026-08-20T09:30:00.00000").put("order", 3))
    return JSONObject().put(REF, JSONObject().put("order", order).put("tasks", JSONObject().put("tasks", tasks)).put("lang", lang)).toString()
}

private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

class WindowParsingTest {
    private val oct8 = d(2026, 10, 8)
    private val expected = d(2026, 12, 10) to d(2027, 2, 18)

    @Test fun `upcoming window in French`() = assertEquals(expected, Orders.parseWindow("10 Décembre - 18 Février", oct8))

    @Test fun `upcoming window in English`() = assertEquals(expected, Orders.parseWindow("Dec 10 - Feb 18", oct8))

    @Test fun `upcoming window in English, full month names`() = assertEquals(expected, Orders.parseWindow("December 10 – February 18", oct8))

    @Test fun `upcoming window in German`() = assertEquals(expected, Orders.parseWindow("10. Dezember – 18. Februar", oct8))

    // Regression: once the window has started, its start must not jump to the next year.
    @Test fun `window in progress in January`() = assertEquals(expected, Orders.parseWindow("10 Décembre - 18 Février", d(2027, 1, 20)))

    @Test fun `window just passed`() = assertEquals(expected, Orders.parseWindow("10 Décembre - 18 Février", d(2027, 2, 25)))

    // Regression: "juin" and "juillet" share their first 3 letters.
    @Test fun `June and July are not mixed up`() =
        assertEquals(d(2027, 6, 20) to d(2027, 7, 15), Orders.parseWindow("20 juin - 15 juillet", d(2027, 3, 1)))

    @Test fun `explicit year and month without accent`() =
        assertEquals(d(2027, 1, 5) to d(2027, 2, 1), Orders.parseWindow("5 janvier 2027 – 1 fevrier 2027", oct8))

    @Test fun `unreadable text`() {
        assertEquals(null to null, Orders.parseWindow(null, oct8))
        assertNull(Orders.parseWindow("Soon", oct8).first)
    }
}

class OrdersParseTest {
    @Test fun `main information is extracted as codes`() {
        val o = Orders.parse(snapshot(), d(2026, 10, 8)).single()
        assertEquals(REF, o.reference)
        assertEquals("Model Y", o.model)
        assertEquals("FR", o.countryCode)
        assertEquals("MTY85", o.trimCode)
        assertEquals("PPSW", o.paintCode)
        assertEquals("BOOKED", o.statusCode)
        assertEquals("PICKUP_SERVICE_CENTER", o.deliveryTypeCode)
        assertEquals(listOf("APBS", "IBB6", "SC04", "WY18P", "STY5B", "CPF0", "TW01"), o.optionCodes)
        assertEquals(d(2026, 8, 20), o.orderedOn)
        assertEquals(d(2026, 12, 10), o.windowStart)
        assertEquals(d(2027, 2, 18), o.windowEnd)
        assertEquals(33983.76, o.amountDue!!, 0.001)
        assertEquals("Cash", o.paymentMethod)
        assertTrue(o.tradeIn)
        assertFalse(o.delivered)
        assertNull(o.vin)
        assertNull(o.appointmentText)
    }

    @Test fun `empty or missing snapshot`() {
        assertTrue(Orders.parse(null).isEmpty())
        assertTrue(Orders.parse("{}").isEmpty())
        assertTrue(Orders.parse("not json").isEmpty())
    }

    @Test fun `simple steps`() {
        assertEquals(listOf(true, false, false, false), simpleSteps(Orders.parse(snapshot()).single()))
        assertEquals(listOf(true, true, true, false), simpleSteps(Orders.parse(snapshot(vin = "V1", appointment = true)).single()))
    }
}

class ChangesTest {
    private fun kinds(old: String, new: String) = Changes.summarize(old, new).changes.map { it.kind }

    @Test fun `no change`() {
        val s = Changes.summarize(snapshot(), snapshot())
        assertEquals(0, s.important); assertEquals(0, s.minor)
    }

    @Test fun `window change`() =
        assertEquals(listOf(Changes.Kind.WINDOW), kinds(snapshot(window = "1 Décembre - 10 Février"), snapshot()))

    // Same dates written in another language (phone language changed): not a change.
    @Test fun `same window in another language is not a change`() =
        assertEquals(emptyList<Changes.Kind>(), kinds(snapshot(window = "10 Décembre - 18 Février"), snapshot(window = "Dec 10 - Feb 18")))

    // Regression: switching the phone language changes all of Tesla's texts, but nothing really changed.
    @Test fun `language switch is not a change`() = assertEquals(emptyList<Changes.Kind>(),
        kinds(snapshot(lang = "fr", financingText = "Terminé"), snapshot(lang = "en", financingText = "Complete")))

    @Test fun `Tesla text change in the same language is reported`() = assertEquals(listOf(Changes.Kind.TASK_INFO),
        kinds(snapshot(financingText = "En attente"), snapshot(financingText = "Contrat prêt")))

    @Test fun `VIN assigned`() {
        val c = Changes.summarize(snapshot(), snapshot(vin = "LRWYGCEK1TC000001")).changes.single()
        assertEquals(Changes.Kind.VIN_ASSIGNED, c.kind)
        assertEquals("LRWYGCEK1TC000001", c.after.vin)
    }

    @Test fun `several updates`() = assertEquals(
        setOf(Changes.Kind.VIN_ASSIGNED, Changes.Kind.AMOUNT, Changes.Kind.TASK_DONE),
        kinds(snapshot(financingComplete = false), snapshot(vin = "V1", amountDue = 30000.0)).toSet(),
    )

    private fun withoutFinancingTask(vararg keep: String): String {
        val j = JSONObject(snapshot(financingComplete = false))
        val tasks = j.getJSONObject(REF).getJSONObject("tasks").getJSONObject("tasks")
        tasks.remove("financing")
        keep.forEach { tasks.remove(it) }
        return j.toString()
    }

    @Test fun `new actionable task is reported`() =
        assertEquals(listOf(Changes.Kind.TASK_TODO), kinds(withoutFinancingTask(), snapshot(financingComplete = false)))

    @Test fun `new task already done is not reported`() =
        assertEquals(emptyList<Changes.Kind>(), kinds(withoutFinancingTask(), snapshot(financingComplete = true)))

    // After a /tasks failure the stored tasks are empty: everything would look new.
    @Test fun `no previous tasks, nothing is reported as new`() {
        val j = JSONObject(snapshot(financingComplete = false))
        j.getJSONObject(REF).put("tasks", JSONObject())
        assertFalse(Changes.Kind.TASK_TODO in kinds(j.toString(), snapshot(financingComplete = false)))
    }

    @Test fun `technical detail alone is not important`() {
        val old = JSONObject(snapshot())
        old.getJSONObject(REF).getJSONObject("order").put("vehicleMapId", 1)
        val s = Changes.summarize(old.toString(), snapshot())
        assertEquals(0, s.important)
        assertEquals(1, s.minor)
    }
}

class IntervalTest {
    private val today = d(2026, 10, 8)

    @Test fun `hourly while nothing moves`() = assertEquals(60L, CheckWorker.intervalFor(snapshot(), today))

    @Test fun `15 min once the VIN is assigned`() = assertEquals(15L, CheckWorker.intervalFor(snapshot(vin = "V1"), today))

    @Test fun `15 min when the window gets close`() =
        assertEquals(15L, CheckWorker.intervalFor(snapshot(window = "20 Octobre - 30 Novembre"), today))

    @Test fun `15 min once an appointment is set`() =
        assertEquals(15L, CheckWorker.intervalFor(snapshot(appointment = true), today))
}

class NightTest {
    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val night = LocalDateTime.of(2026, 10, 9, 2, 0)

    @Test fun `never skipped during the day`() {
        val noon = LocalDateTime.of(2026, 10, 9, 12, 0)
        assertFalse(CheckWorker.skipAtNight(noon, ms(noon.minusMinutes(10))))
    }

    @Test fun `spaced out at night`() = assertTrue(CheckWorker.skipAtNight(night, ms(night.minusHours(1))))

    @Test fun `checked at night after 3 hours`() = assertFalse(CheckWorker.skipAtNight(night, ms(night.minusHours(3).minusMinutes(1))))

    @Test fun `never checked`() = assertFalse(CheckWorker.skipAtNight(night, 0))
}
