package org.fossify.phone.extensions

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts JSONObject to a Map<String, Any> for easier consumption elsewhere.
 */
fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        val value = this[key]
        map[key] = when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> ""
            else -> value
        }
    }
    return map
}

private fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until length()) {
        val value = get(i)
        list += when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> ""
            else -> value
        }
    }
    return list
}
