package app.deliveryday

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** App private storage: Tesla tokens, last known state of the orders, user inputs. */
class Store(private val context: Context) {
    private val prefs = context.getSharedPreferences("tesla", Context.MODE_PRIVATE)

    /**
     * Version announced to Tesla: the one of the Tesla app installed on the phone ("4.61.0"),
     * otherwise the last one seen, otherwise a recent known version. A realistic version
     * rather than a made-up number.
     */
    val teslaAppVersion: String
        get() {
            val installed = runCatching {
                context.packageManager.getPackageInfo("com.teslamotors.tesla", 0).versionName?.substringBefore('-')
            }.getOrNull()?.takeIf { Regex("""\d+\.\d+\.\d+""").matches(it) }
            if (installed != null) { prefs.edit().putString("teslaAppVersion", installed).apply(); return installed }
            return prefs.getString("teslaAppVersion", null) ?: "4.61.0"
        }

    var accessToken: String?
        get() = secret("access")
        set(v) = setSecret("access", v)

    var refreshToken: String?
        get() = secret("refresh")
        set(v) = setSecret("refresh", v)

    /** Tesla tokens are encrypted at rest; a legacy plain value is encrypted on first read. */
    private fun secret(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        if (!raw.startsWith(Crypto.PREFIX)) { setSecret(key, raw); return raw }
        return Crypto.decrypt(raw)
    }

    private fun setSecret(key: String, v: String?) =
        prefs.edit().putString(key, v?.let(Crypto::encrypt)).apply()

    /** Scheduled check interval (minutes), to reschedule only when it changes. */
    var checkInterval: Long
        get() = prefs.getLong("checkInterval", 0)
        set(v) = prefs.edit().putLong("checkInterval", v).apply()

    /** JSON of the last orders snapshot. */
    var snapshot: String?
        get() = prefs.getString("snapshot", null)
        set(v) = prefs.edit().putString("snapshot", v).apply()

    var lastCheck: Long
        get() = prefs.getLong("lastCheck", 0)
        set(v) = prefs.edit().putLong("lastCheck", v).apply()

    /** Technical message of the last failed check (null when the last check succeeded). */
    var lastError: String?
        get() = prefs.getString("lastError", null)
        set(v) = prefs.edit().putString("lastError", v).apply()

    /** Tesla refused the session: the user must sign in again. */
    var needsLogin: Boolean
        get() = prefs.getBoolean("needsLogin", false)
        set(v) = prefs.edit().putBoolean("needsLogin", v).apply()

    /** Estimated trade-in value, typed by the user (Tesla only sends it once the official offer is made). */
    var tradeInEstimate: Double?
        get() = prefs.getString("tradeInEstimate", null)?.toDoubleOrNull()
        set(v) = prefs.edit().putString("tradeInEstimate", v?.toString()).apply()

    /** Amount requested by Tesla when the estimate was typed (trade-in not deducted yet). */
    var tradeInBaseline: Double?
        get() = prefs.getString("tradeInBaseline", null)?.toDoubleOrNull()
        set(v) = prefs.edit().putString("tradeInBaseline", v?.toString()).apply()

    /** Last important news (for the banner and the widget) and when it arrived. */
    var lastNews: String?
        get() = prefs.getString("lastNews", null)
        set(v) = prefs.edit().putString("lastNews", v).apply()

    var lastNewsAt: Long
        get() = prefs.getLong("lastNewsAt", 0)
        set(v) = prefs.edit().putLong("lastNewsAt", v).apply()

    data class Event(val at: Long, val title: String, val detail: String)

    /** History of important changes, most recent first (100 max). */
    val history: List<Event>
        get() = runCatching {
            val a = JSONArray(prefs.getString("history", "[]"))
            (0 until a.length()).map { a.getJSONObject(it) }.map { Event(it.getLong("at"), it.getString("title"), it.optString("detail")) }
        }.getOrDefault(emptyList())

    fun addHistory(events: List<Pair<String, String>>, at: Long = System.currentTimeMillis()) {
        val all = events.map { Event(at, it.first, it.second) } + history
        val a = JSONArray()
        all.take(100).forEach { a.put(JSONObject().put("at", it.at).put("title", it.title).put("detail", it.detail)) }
        prefs.edit().putString("history", a.toString()).apply()
    }

    /** PKCE verifier of the sign-in in progress. */
    var pkceVerifier: String?
        get() = prefs.getString("pkce", null)
        set(v) = prefs.edit().putString("pkce", v).apply()

    /** OAuth "state" of the sign-in in progress. */
    var oauthState: String?
        get() = prefs.getString("oauthState", null)
        set(v) = prefs.edit().putString("oauthState", v).apply()

    /** Last error on delivery details / tasks (null when fine). */
    var tasksError: String?
        get() = prefs.getString("tasksError", null)
        set(v) = prefs.edit().putString("tasksError", v).apply()

    /** Removes everything, history and trade-in estimate included. */
    fun clearAll() = prefs.edit().clear().apply()

    /** Demo data (debug builds only, for screenshots): checks never call Tesla. */
    var demo: Boolean
        get() = prefs.getBoolean("demo", false)
        set(v) = prefs.edit().putBoolean("demo", v).apply()

    /** Clears the session and Tesla data, but keeps the trade-in estimate and the history. */
    fun logout() {
        val keep = tradeInEstimate; val base = tradeInBaseline; val hist = prefs.getString("history", null)
        prefs.edit().clear().apply()
        tradeInEstimate = keep; tradeInBaseline = base
        hist?.let { prefs.edit().putString("history", it).apply() }
    }
}
