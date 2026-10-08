package app.deliveryday

import org.json.JSONArray
import org.json.JSONObject

/** Compares two JSON snapshots field by field (used to count technical changes). */
object Differ {
    fun flatten(v: Any?, path: String, out: MutableMap<String, String>) {
        when (v) {
            is JSONObject -> v.keys().forEach { flatten(v.get(it), if (path.isEmpty()) it else "$path.$it", out) }
            is JSONArray -> for (i in 0 until v.length()) flatten(v.get(i), "$path[$i]", out)
            else -> out[path] = v.toString()
        }
    }

    fun diff(old: String, new: String): List<String> {
        val a = mutableMapOf<String, String>(); flatten(JSONObject(old), "", a)
        val b = mutableMapOf<String, String>(); flatten(JSONObject(new), "", b)
        val res = mutableListOf<String>()
        for ((k, v) in b) {
            val o = a[k]
            if (o == null) res += "+ ${short(k)} : $v"
            else if (o != v) res += "${short(k)} : $o → $v"
        }
        for (k in a.keys - b.keys) res += "− ${short(k)}"
        return res
    }

    private fun short(path: String) = path.replace(".order.", " · ").replace(".tasks.", " · ")
}
