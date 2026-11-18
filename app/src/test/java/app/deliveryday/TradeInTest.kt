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

    @Test fun `selected Tesla valuation replaces manual estimate`() {
        val t = JSONObject("""{"status":"OFFER_ACCEPTED_COMPLETE","tradeInVehicle":{
            "SelectedValuation":{"Value":12000,"CurrencyType":"EUR"},
            "Valuations":[{"Value":15000}]}}""")
        val offer = TradeIn.officialOffer(t)
        assertEquals(12000.0, offer!!, 0.001)
        val p = TradeIn.payment(28000.0, 15000.0, offer, 40000.0)
        assertTrue(p.official)
        assertTrue(p.deductedByTesla)
        assertEquals(12000.0, p.value!!, 0.001)
        assertEquals(28000.0, p.remaining!!, 0.001)
    }

    @Test fun `selected valuation takes priority over an older offer`() {
        val t = JSONObject("""{"offerAmount":15000,"tradeInVehicle":{
            "SelectedValuation":{"Value":"12000"}}}""")
        assertEquals(12000.0, TradeIn.officialOffer(t)!!, 0.001)
    }

    @Test fun `unselected valuations are not an official offer`() {
        val t = JSONObject("""{"tradeInVehicle":{"SelectedValuation":{"Value":null},
            "Valuations":[{"Value":15000}]}}""")
        assertNull(TradeIn.officialOffer(t))
    }

    @Test fun `trade-in bonus is not an offer`() {
        val t = JSONObject().put("tradeInUpliftDetails", JSONObject().put("amount", 1500))
        assertNull(TradeIn.officialOffer(t))
    }

    // Tesla asks for the full amount without the trade-in: subtract the estimate.
    @Test fun `estimate not deducted yet`() {
        val p = TradeIn.payment(amountDue = 38125.42, estimate = 7400.0, offer = null, baseline = 38125.42)
        assertFalse(p.deductedByTesla); assertFalse(p.official)
        assertEquals(30725.42, p.remaining!!, 0.001)
    }

    // Official offer but unchanged Tesla amount: subtract the offer (not the estimate).
    @Test fun `official offer wins`() {
        val p = TradeIn.payment(38125.42, 7400.0, 7250.0, 38125.42)
        assertTrue(p.official); assertFalse(p.deductedByTesla)
        assertEquals(30875.42, p.remaining!!, 0.001)
    }

    // Tesla lowered its amount by the trade-in value: never subtract it a second time.
    @Test fun `no double deduction`() {
        val p = TradeIn.payment(amountDue = 30875.42, estimate = 7400.0, offer = 7250.0, baseline = 38125.42)
        assertTrue(p.deductedByTesla)
        assertEquals(30875.42, p.remaining!!, 0.001)
    }

    @Test fun `accepted offer uses Tesla balance without a local baseline`() {
        val p = TradeIn.payment(31275.42, null, 6850.0, null, offerAccepted = true)
        assertFalse(p.deductedByTesla)
        assertFalse(p.subtractsTradeIn)
        assertEquals(31275.42, p.remaining!!, 0.001)
    }

    // A small price change (options, fees) is not a trade-in deduction.
    @Test fun `small price drop is not the trade-in`() {
        val p = TradeIn.payment(37625.42, 7400.0, null, 38125.42)
        assertFalse(p.deductedByTesla)
        assertEquals(30225.42, p.remaining!!, 0.001)
    }

    @Test fun `no trade-in`() {
        val p = TradeIn.payment(38125.42, null, null, null)
        assertNull(p.value); assertEquals(38125.42, p.remaining!!, 0.001)
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
