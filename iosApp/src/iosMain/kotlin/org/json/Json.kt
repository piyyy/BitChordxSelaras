package org.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Android's org.json, for the phone's files compiled for iOS: read-mostly views over
 * kotlinx.serialization's JSON tree, with org.json's lenient opt* conversions.
 */
class JSONException(message: String) : RuntimeException(message)

private fun wrap(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> JSONObject(e)
    is JsonArray -> JSONArray(e)
    is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull ?: e.content
}

private fun unwrap(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JSONObject -> JsonObject(v.map.mapValues { unwrap(it.value) })
    is JSONArray -> JsonArray(v.list.map(::unwrap))
    is JsonElement -> v
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to unwrap(it.value) })
    is Collection<*> -> JsonArray(v.map(::unwrap))
    else -> JsonPrimitive(v.toString())
}

private fun Any?.asLong(): Long? = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull() ?: toDoubleOrNull()?.toLong()
    else -> null
}

private fun Any?.asDouble(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
}

private fun Any?.asBoolean(): Boolean? = when (this) {
    is Boolean -> this
    is String -> when (lowercase()) { "true" -> true; "false" -> false; else -> null }
    else -> null
}

class JSONObject internal constructor(internal val map: MutableMap<String, Any?>) {
    constructor() : this(LinkedHashMap())
    constructor(source: String) : this(
        (runCatching { Json.parseToJsonElement(source) }.getOrNull() as? JsonObject)?.let { obj ->
            LinkedHashMap<String, Any?>().also { m -> obj.forEach { (k, v) -> m[k] = wrap(v) } }
        } ?: throw JSONException("Not a JSON object"),
    )
    internal constructor(obj: JsonObject) : this(LinkedHashMap<String, Any?>().also { m -> obj.forEach { (k, v) -> m[k] = wrap(v) } })
    constructor(from: Map<String, Any?>) : this(LinkedHashMap(from))

    fun has(name: String): Boolean = map.containsKey(name)
    fun isNull(name: String): Boolean = map[name] == null
    fun length(): Int = map.size
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun names(): JSONArray? = if (map.isEmpty()) null else JSONArray(map.keys.toMutableList<Any?>())

    fun opt(name: String): Any? = map[name]
    fun get(name: String): Any = map[name] ?: throw JSONException("No value for $name")

    fun optString(name: String): String = optString(name, "")
    fun optString(name: String, fallback: String): String = when (val v = map[name]) {
        null -> fallback
        is String -> v
        else -> v.toString()
    }
    fun getString(name: String): String = get(name).let { it as? String ?: it.toString() }

    fun optInt(name: String): Int = optInt(name, 0)
    fun optInt(name: String, fallback: Int): Int = map[name].asLong()?.toInt() ?: fallback
    fun getInt(name: String): Int = get(name).asLong()?.toInt() ?: throw JSONException("$name is not an int")
    fun optLong(name: String): Long = optLong(name, 0L)
    fun optLong(name: String, fallback: Long): Long = map[name].asLong() ?: fallback
    fun getLong(name: String): Long = get(name).asLong() ?: throw JSONException("$name is not a long")
    fun optDouble(name: String): Double = optDouble(name, Double.NaN)
    fun optDouble(name: String, fallback: Double): Double = map[name].asDouble() ?: fallback
    fun getDouble(name: String): Double = get(name).asDouble() ?: throw JSONException("$name is not a number")
    fun optBoolean(name: String): Boolean = optBoolean(name, false)
    fun optBoolean(name: String, fallback: Boolean): Boolean = map[name].asBoolean() ?: fallback
    fun getBoolean(name: String): Boolean = get(name).asBoolean() ?: throw JSONException("$name is not a boolean")

    fun optJSONObject(name: String): JSONObject? = map[name] as? JSONObject
    fun getJSONObject(name: String): JSONObject = map[name] as? JSONObject ?: throw JSONException("$name is not an object")
    fun optJSONArray(name: String): JSONArray? = map[name] as? JSONArray
    fun getJSONArray(name: String): JSONArray = map[name] as? JSONArray ?: throw JSONException("$name is not an array")

    fun put(name: String, value: Any?): JSONObject = apply { if (value == null) map.remove(name) else map[name] = value }
    fun remove(name: String): Any? = map.remove(name)

    override fun toString(): String = unwrap(this).toString()

    companion object {
        val NULL: Any = Any()
    }
}

class JSONArray internal constructor(internal val list: MutableList<Any?>) {
    constructor() : this(ArrayList())
    constructor(source: String) : this(
        (runCatching { Json.parseToJsonElement(source) }.getOrNull() as? JsonArray)?.mapTo(ArrayList()) { wrap(it) }
            ?: throw JSONException("Not a JSON array"),
    )
    internal constructor(arr: JsonArray) : this(arr.mapTo(ArrayList()) { wrap(it) })
    constructor(from: Collection<*>) : this(from.toMutableList<Any?>())

    fun length(): Int = list.size
    fun opt(index: Int): Any? = list.getOrNull(index)
    fun get(index: Int): Any = list.getOrNull(index) ?: throw JSONException("No value at $index")
    fun isNull(index: Int): Boolean = list.getOrNull(index) == null

    fun optString(index: Int): String = optString(index, "")
    fun optString(index: Int, fallback: String): String = list.getOrNull(index)?.let { it as? String ?: it.toString() } ?: fallback
    fun getString(index: Int): String = get(index).let { it as? String ?: it.toString() }
    fun optInt(index: Int, fallback: Int = 0): Int = list.getOrNull(index).asLong()?.toInt() ?: fallback
    fun getInt(index: Int): Int = get(index).asLong()?.toInt() ?: throw JSONException("$index is not an int")
    fun optLong(index: Int, fallback: Long = 0L): Long = list.getOrNull(index).asLong() ?: fallback
    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = list.getOrNull(index).asDouble() ?: fallback
    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = list.getOrNull(index).asBoolean() ?: fallback

    fun optJSONObject(index: Int): JSONObject? = list.getOrNull(index) as? JSONObject
    fun getJSONObject(index: Int): JSONObject = list.getOrNull(index) as? JSONObject ?: throw JSONException("$index is not an object")
    fun optJSONArray(index: Int): JSONArray? = list.getOrNull(index) as? JSONArray
    fun getJSONArray(index: Int): JSONArray = list.getOrNull(index) as? JSONArray ?: throw JSONException("$index is not an array")

    fun put(value: Any?): JSONArray = apply { list.add(value) }
    fun put(index: Int, value: Any?): JSONArray = apply {
        while (list.size <= index) list.add(null)
        list[index] = value
    }
    fun remove(index: Int): Any? = list.removeAt(index)

    override fun toString(): String = unwrap(this).toString()
}
