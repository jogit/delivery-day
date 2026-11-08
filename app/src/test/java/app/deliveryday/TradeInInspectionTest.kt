package app.deliveryday

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeInInspectionTest {
    private fun inspected(vararg fields: Pair<String, String>): String {
        val root = JSONObject(snapshot())
        val vehicle = JSONObject()
        fields.forEach { (key, value) -> vehicle.put(key, value) }
        root.getJSONObject(REF).getJSONObject("tasks").getJSONObject("tasks")
            .getJSONObject("tradeIn").put("tradeInVehicle", vehicle)
        return root.toString()
    }

    @Test fun `inspection is reported even when offer status stays unchanged`() {
        val new = inspected("AMPStatus" to "INSPECTION_COMPLETED", "InspectionStatus" to "COMPLETED")
        val parsed = Orders.parse(new).single()
        assertEquals("INSPECTION_COMPLETED", parsed.tradeInInspectionCode)
        assertEquals("NO_ESTIMATE_SELF_INSPECTION", parsed.tradeInStatusCode)
        assertNull(parsed.tradeInOffer)
        assertEquals(listOf(Changes.Kind.TRADE_IN_INSPECTION),
            Changes.summarize(snapshot(), new).changes.map { it.kind })
    }

    @Test fun `inspection status fallback uses the same completed code`() {
        val amp = inspected("AMPStatus" to "INSPECTION_COMPLETED")
        val fallback = inspected("InspectionStatus" to "COMPLETED")
        assertEquals("INSPECTION_COMPLETED", Orders.parse(fallback).single().tradeInInspectionCode)
        assertTrue(Changes.summarize(amp, fallback).changes.isEmpty())
    }

    @Test fun `unchanged inspection and translated milestone do not notify`() {
        val old = inspected("AMPStatus" to "INSPECTION_COMPLETED", "AcquisitionMilestone" to "Inspection Completed")
        val new = inspected("AMPStatus" to "INSPECTION_COMPLETED", "AcquisitionMilestone" to "Inspection terminée")
        assertTrue(Changes.summarize(old, new).changes.isEmpty())
    }

    @Test fun `missing inspection is not a regression or a notification`() {
        assertNull(Orders.parse(snapshot()).single().tradeInInspectionCode)
        assertTrue(Changes.summarize(inspected("AMPStatus" to "INSPECTION_COMPLETED"), snapshot()).changes.isEmpty())
    }
}
