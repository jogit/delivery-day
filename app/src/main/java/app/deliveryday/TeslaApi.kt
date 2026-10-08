package app.deliveryday

import android.net.Uri
import android.util.Base64
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

class AuthExpired : Exception("Tesla session expired")

/** HTTP error response from the auth server (as opposed to a network problem). */
class AuthHttpError(val code: Int, val body: String) : Exception("Auth HTTP $code")

/** Unofficial API used by the Tesla mobile app (OAuth PKCE, "ownerapi" client). */
class TeslaApi(private val store: Store, private val http: OkHttpClient = OkHttpClient()) {

    companion object {
        private const val AUTH = "https://auth.tesla.com/oauth2/v3"
        private const val REDIRECT = "tesla://auth/callback"
        private const val CLIENT_ID = "ownerapi"
        private const val SCOPE = "openid email offline_access"
        /** Shared by all instances (worker, widget, buttons) so token refreshes never overlap. */
        private val REFRESH_LOCK = Any()

        private fun rand(n: Int): String {
            val b = ByteArray(n); SecureRandom().nextBytes(b)
            return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
        private fun sha256(s: String) = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    /** URL to open in the browser to sign in. */
    fun loginUrl(): String {
        // Kept on disk: Android may kill the app while the user signs in in the browser.
        val verifier = rand(64).also { store.pkceVerifier = it }
        // Checked on return, so that only the answer to this sign-in is accepted.
        val state = rand(16).also { store.oauthState = it }
        return "$AUTH/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("code_challenge", sha256(verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("redirect_uri", REDIRECT)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("state", state)
            .build().toString()
    }

    /** [callbackUrl]: the "tesla://auth/callback?code=..." URL received after signing in. */
    fun exchangeCode(callbackUrl: String) {
        val uri = Uri.parse(callbackUrl.trim())
        val expectedState = store.oauthState ?: error("Sign-in expired, please start again")
        if (uri.getQueryParameter("state") != expectedState) error("This sign-in response does not match, please start again")
        val code = uri.getQueryParameter("code") ?: error("No code parameter in the URL")
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("code_verifier", store.pkceVerifier ?: error("Sign-in expired, please start again"))
            .add("redirect_uri", REDIRECT)
            .build()
        saveTokens(post(body))
        store.pkceVerifier = null
        store.oauthState = null
    }

    /** [staleToken]: the access token that was refused; if another caller already replaced it, nothing to do. */
    private fun refresh(staleToken: String? = null): Unit = synchronized(REFRESH_LOCK) {
        if (staleToken != null && store.accessToken.let { it != null && it != staleToken }) return
        val rt = store.refreshToken ?: throw AuthExpired()
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", CLIENT_ID)
            .add("refresh_token", rt)
            .add("scope", SCOPE)
            .build()
        // Only an explicit refusal from Tesla invalidates the session; a network error or another 400 is just retried.
        try { saveTokens(post(body)) } catch (e: AuthHttpError) {
            if (e.code == 401 || (e.code == 400 && e.body.contains("invalid_grant"))) throw AuthExpired() else throw e
        }
    }

    private fun post(body: FormBody): JSONObject {
        val req = Request.Builder().url("$AUTH/token").post(body).build()
        http.newCall(req).execute().use {
            val s = it.body.string()
            if (!it.isSuccessful) throw AuthHttpError(it.code, s)
            return JSONObject(s)
        }
    }

    private fun saveTokens(j: JSONObject) {
        store.accessToken = j.getString("access_token")
        j.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { store.refreshToken = it }
    }

    private fun get(url: String, retry: Boolean = true): JSONObject {
        if (store.accessToken == null) refresh()
        val token = store.accessToken
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "TeslaApp/${store.teslaAppVersion}")
            .build()
        http.newCall(req).execute().use {
            if (it.code == 401 && retry) { refresh(token); return get(url, false) }
            if (it.code == 401) throw AuthExpired()
            val s = it.body.string()
            if (!it.isSuccessful) error("HTTP ${it.code} on $url")
            return JSONObject(s)
        }
    }

    /** Returns { referenceNumber: { order: {...}, tasks: {...} } } for all orders. */
    fun fetchOrders(): JSONObject {
        val list = get("https://owner-api.teslamotors.com/api/1/users/orders").optJSONArray("response")
        val out = JSONObject()
        if (list == null) return out
        val previous = store.snapshot?.let { runCatching { JSONObject(it) }.getOrNull() }
        store.tasksError = null
        val lang = Locale.getDefault().language
        for (i in 0 until list.length()) {
            val order = list.getJSONObject(i)
            val rn = order.optString("referenceNumber")
            if (rn.isEmpty()) continue
            val tasks = try {
                get(
                    "https://akamai-apigateway-vfx.tesla.com/tasks".toHttpUrl().newBuilder()
                        // Tesla texts in the phone's language, for the order's country.
                        .addQueryParameter("deviceLanguage", lang)
                        .addQueryParameter("deviceCountry", order.optString("countryCode").ifEmpty { Locale.getDefault().country })
                        .addQueryParameter("referenceNumber", rn)
                        // Versions that are too old are refused by /tasks ("Update App", HTTP 403).
                        .addQueryParameter("appVersion", store.teslaAppVersion)
                        .build().toString()
                ).also(::dropStrings)
            } catch (e: AuthExpired) { throw e } catch (e: Exception) {
                // Keep the last known information (otherwise everything would look "gone" and trigger alerts).
                store.tasksError = e.message ?: e.javaClass.simpleName
                previous?.optJSONObject(rn)?.optJSONObject("tasks") ?: JSONObject()
            }
            // The language is recorded so that a language switch is not mistaken for changes.
            out.put(rn, JSONObject().put("order", order).put("tasks", tasks).put("lang", lang))
        }
        return out
    }

    /** Removes translated texts ("strings"): bulky and useless to detect changes. */
    private fun dropStrings(o: Any?) {
        when (o) {
            is JSONObject -> { o.remove("strings"); o.keys().forEach { dropStrings(o.opt(it)) } }
            is JSONArray -> for (i in 0 until o.length()) dropStrings(o.opt(i))
        }
    }
}
