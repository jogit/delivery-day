package app.deliveryday

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorKindTest {
    private fun kind(e: Throwable) = ErrorKind.of(e)

    @Test fun `network problems are retried, not blamed on Tesla`() {
        assertEquals(ErrorKind.NETWORK, kind(UnknownHostException("owner-api.teslamotors.com")))
        assertEquals(ErrorKind.NETWORK, kind(SocketTimeoutException("timeout")))
    }

    @Test fun `server errors and throttling are temporary`() {
        assertEquals(ErrorKind.TESLA_DOWN, kind(ApiHttpError(503, "u")))
        assertEquals(ErrorKind.TESLA_DOWN, kind(ApiHttpError(500, "u")))
        assertEquals(ErrorKind.TESLA_DOWN, kind(ApiHttpError(429, "u")))
        assertEquals(ErrorKind.TESLA_DOWN, kind(AuthHttpError(502, "")))
    }

    @Test fun `refused requests suggest that the API changed`() {
        assertEquals(ErrorKind.API_CHANGED, kind(ApiHttpError(403, "u")))
        assertEquals(ErrorKind.API_CHANGED, kind(ApiHttpError(404, "u")))
        assertEquals(ErrorKind.API_CHANGED, kind(AuthHttpError(400, "")))
    }

    @Test fun `unreadable answers suggest that the API changed`() =
        assertEquals(ErrorKind.API_CHANGED, kind(JSONException("Value <html> of type java.lang.String cannot be converted")))

    @Test fun `the message keeps the HTTP code and URL for bug reports`() =
        assertEquals("HTTP 403 on https://x/tasks", ApiHttpError(403, "https://x/tasks").message)
}
