package app.deliveryday

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeInTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.classLoader!!.getResource(name)!!.readText())

    // Real Tesla response with no offer yet: nothing must be taken for an amount.
    @Test fun `no offer in a real response`() = assertNull(TradeIn.officialOffer(fixture("tradein_no_offer.json")))

    @Test fun `offer detected`() {
        val t = fixture("tradein_no_offer.json").put("status", "OFFER_READY")
            .put("finalOffer", JSONObject().put("offerAmount", 7250).put("currencyCode", "EUR"))
        assertEquals(7250.0, TradeIn.officialOffer(t)!!, 0.001)
    }

    @Test fun `offer as formatted text`() {
        val t = JSONObject().put("tradeInOffer", JSONObject().put("value", "7 250,00 €"))
        assertEquals(7250.0, TradeIn.officialOffer(t)!!, 0.001)
    }

    @Test fun `trade-in bonus is not an offer`() {
        val t = JSONObject().put("tradeInUpliftDetails", JSONObject().put("amount", 1500))
        assertNull(TradeIn.officialOffer(t))
    }

    // Tesla asks for the full amount without the trade-in: subtract the estimate.
    @Test fun `estimate not deducted yet`() {
        val p = TradeIn.payment(amountDue = 33983.76, estimate = 7400.0, offer = null, baseline = 33983.76)
        assertFalse(p.deductedByTesla); assertFalse(p.official)
        assertEquals(26583.76, p.remaining!!, 0.001)
    }

    // Official offer but unchanged Tesla amount: subtract the offer (not the estimate).
    @Test fun `official offer wins`() {
        val p = TradeIn.payment(33983.76, 7400.0, 7250.0, 33983.76)
        assertTrue(p.official); assertFalse(p.deductedByTesla)
        assertEquals(26733.76, p.remaining!!, 0.001)
    }

    // Tesla lowered its amount by the trade-in value: never subtract it a second time.
    @Test fun `no double deduction`() {
        val p = TradeIn.payment(amountDue = 26733.76, estimate = 7400.0, offer = 7250.0, baseline = 33983.76)
        assertTrue(p.deductedByTesla)
        assertEquals(26733.76, p.remaining!!, 0.001)
    }

    // A small price change (options, fees) is not a trade-in deduction.
    @Test fun `small price drop is not the trade-in`() {
        val p = TradeIn.payment(33483.76, 7400.0, null, 33983.76)
        assertFalse(p.deductedByTesla)
        assertEquals(26083.76, p.remaining!!, 0.001)
    }

    @Test fun `no trade-in`() {
        val p = TradeIn.payment(33983.76, null, null, null)
        assertNull(p.value); assertEquals(33983.76, p.remaining!!, 0.001)
    }

    @Test fun `offer change is reported`() {
        fun withOffer(amount: Int?) = JSONObject(snapshot()).also { s ->
            val t = s.getJSONObject(REF).getJSONObject("tasks").getJSONObject("tasks").getJSONObject("tradeIn")
            amount?.let { t.put("offerAmount", it) }
        }.toString()
        val c = Changes.summarize(withOffer(null), withOffer(7250)).changes.single()
        assertEquals(Changes.Kind.TRADE_IN_OFFER, c.kind)
        assertEquals(7250.0, c.after.tradeInOffer!!, 0.001)
    }
}
