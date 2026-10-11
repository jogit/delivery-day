package app.deliveryday

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The other kinds of change that [Changes.summarize] reports (see ChangesTest for window, VIN, tasks). */
class ChangesKindsTest {
    private fun kinds(old: String, new: String) = Changes.summarize(old, new).changes.map { it.kind }

    /** [snapshot] with [f] applied to the order, the "scheduling" task and the "tradeIn" task. */
    private fun tweak(
        base: String = snapshot(),
        order: JSONObject.() -> Unit = {},
        scheduling: JSONObject.() -> Unit = {},
        tradeIn: JSONObject.() -> Unit = {},
    ): String {
        val j = JSONObject(base)
        val o = j.getJSONObject(REF)
        o.getJSONObject("order").order()
        val tasks = o.getJSONObject("tasks").getJSONObject("tasks")
        tasks.getJSONObject("scheduling").scheduling()
        tasks.getJSONObject("tradeIn").tradeIn()
        return j.toString()
    }

    @Test fun `order status change`() =
        assertEquals(listOf(Changes.Kind.STATUS), kinds(snapshot(), tweak(order = { put("orderStatus", "DELIVERED") })))

    @Test fun `appointment set`() =
        assertEquals(listOf(Changes.Kind.APPOINTMENT_SET), kinds(snapshot(appointment = false), snapshot(appointment = true)))

    @Test fun `appointment moved`() {
        val old = tweak(snapshot(appointment = true), scheduling = { put("appointmentDateTime", "12 Dec 10:00") })
        val new = tweak(snapshot(appointment = true), scheduling = { put("appointmentDateTime", "13 Dec 14:30") })
        assertEquals(listOf(Changes.Kind.APPOINTMENT_CHANGED), kinds(old, new))
    }

    @Test fun `delivery place change`() =
        assertEquals(listOf(Changes.Kind.PLACE), kinds(snapshot(), tweak(scheduling = { put("deliveryAddressTitle", "Tesla Delivery Center Paris") })))

    // Tesla's place names are translated: a language switch must not look like a move.
    @Test fun `delivery place in another language is not a change`() = assertEquals(
        emptyList<Changes.Kind>(),
        kinds(snapshot(lang = "fr"), tweak(snapshot(lang = "en"), scheduling = { put("deliveryAddressTitle", "Tesla Lyon Delivery Hub") })),
    )

    @Test fun `trade-in status change`() =
        assertEquals(listOf(Changes.Kind.TRADE_IN_STATUS), kinds(snapshot(), tweak(tradeIn = { put("status", "ESTIMATE_READY") })))

    @Test fun `amount due change`() =
        assertEquals(listOf(Changes.Kind.AMOUNT), kinds(snapshot(amountDue = 33983.76), snapshot(amountDue = 12000.0)))

    @Test fun `new order`() {
        val c = Changes.summarize("{}", snapshot()).changes.single()
        assertEquals(Changes.Kind.NEW_ORDER, c.kind)
        assertEquals(REF, c.after.reference)
    }

    @Test fun `unchanged order next to a changed one only reports the changed one`() {
        fun two(vin: String?): String {
            val a = JSONObject(snapshot(vin = vin)).getJSONObject(REF)
            val b = JSONObject(snapshot()).getJSONObject(REF)
            b.getJSONObject("order").put("referenceNumber", "RN000000002")
            return JSONObject().put(REF, a).put("RN000000002", b).toString()
        }
        val s = Changes.summarize(two(null), two("LRWYGCEK1TC000001"))
        assertEquals(1, s.important)
        assertEquals(REF, s.changes.single().after.reference)
    }

    @Test fun `an order that disappears is not reported`() =
        assertTrue(kinds(snapshot(), "{}").isEmpty())
}
