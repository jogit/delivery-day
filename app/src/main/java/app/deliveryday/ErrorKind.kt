package app.deliveryday

import java.io.IOException

/** HTTP error response from Tesla's API (as opposed to a network problem). */
class ApiHttpError(val code: Int, url: String) : Exception("HTTP $code on $url")

/** What a failed check means for the user, to tell "try later" apart from "Tesla changed something". */
enum class ErrorKind {
    /** No connection, timeout… The app retries by itself. */
    NETWORK,
    /** Tesla's servers are down or throttling (5xx, 429). The app retries by itself. */
    TESLA_DOWN,
    /** Refused request or unreadable answer: Tesla may have changed its unofficial API. */
    API_CHANGED;

    companion object {
        fun of(e: Throwable): ErrorKind {
            val code = when (e) { is ApiHttpError -> e.code; is AuthHttpError -> e.code; else -> null }
            return when {
                code != null -> if (code >= 500 || code == 429) TESLA_DOWN else API_CHANGED
                e is IOException -> NETWORK
                else -> API_CHANGED
            }
        }
    }
}
