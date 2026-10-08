package app.deliveryday

import org.json.JSONObject

/** Raw view of the last snapshot ("Raw data" menu). */
object Recap {
    /** Every field as "path = value", sorted. */
    fun details(snapshot: String?): List<String> {
        if (snapshot == null) return emptyList()
        val flat = mutableMapOf<String, String>().also { Differ.flatten(JSONObject(snapshot), "", it) }
        return flat.entries.sortedBy { it.key }
            .filter { it.value.isNotBlank() && it.value != "null" }
            .map { "${it.key} = ${it.value}" }
    }
}
