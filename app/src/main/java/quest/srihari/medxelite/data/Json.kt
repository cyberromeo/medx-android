package quest.srihari.medxelite.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull

val MedxJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

// ---- Lenient readers: the backend is written by two clients and a seeder, so every field
// ---- may arrive as a string, a number or not at all (same policy as the iOS decoders).

fun JsonObject.el(key: String): JsonElement? = this[key]?.takeUnless { it is JsonNull }

fun JsonObject.str(key: String): String? {
    val p = el(key) as? JsonPrimitive ?: return null
    return p.contentOrNull
}

fun JsonObject.int(key: String): Int? {
    val p = el(key) as? JsonPrimitive ?: return null
    p.longOrNull?.let { return it.toInt() }
    p.doubleOrNull?.let { return it.toInt() }
    return p.contentOrNull?.trim()?.toIntOrNull()
}

fun JsonObject.long(key: String): Long? {
    val p = el(key) as? JsonPrimitive ?: return null
    p.longOrNull?.let { return it }
    p.doubleOrNull?.let { return it.toLong() }
    return p.contentOrNull?.trim()?.toLongOrNull()
}

fun JsonObject.double(key: String): Double? {
    val p = el(key) as? JsonPrimitive ?: return null
    p.doubleOrNull?.let { return it }
    return p.contentOrNull?.trim()?.toDoubleOrNull()
}

fun JsonObject.bool(key: String): Boolean? {
    val p = el(key) as? JsonPrimitive ?: return null
    p.booleanOrNull?.let { return it }
    p.longOrNull?.let { return it != 0L }
    val s = p.contentOrNull?.lowercase() ?: return null
    return when (s) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }
}

fun JsonObject.obj(key: String): JsonObject? = el(key) as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = el(key) as? JsonArray
fun JsonObject.objects(key: String): List<JsonObject> = arr(key)?.mapNotNull { it as? JsonObject } ?: emptyList()

fun JsonElement.asIntLenient(): Int? {
    val p = this as? JsonPrimitive ?: return null
    p.longOrNull?.let { return it.toInt() }
    p.doubleOrNull?.let { return it.toInt() }
    return p.contentOrNull?.let { OptionIds.int(it) }
}

fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

// ---- Builders

fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.filter { it.second != null }.associate { it.first to anyToJson(it.second) })

fun anyToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value.toDouble())
    is Map<*, *> -> JsonObject(value.entries.filter { it.value != null }.associate { it.key.toString() to anyToJson(it.value) })
    is Iterable<*> -> JsonArray(value.map { anyToJson(it) })
    else -> JsonPrimitive(value.toString())
}

/** Option and question ids: "123", "q_123" and opaque strings all become a stable number. */
object OptionIds {
    fun int(raw: String): Int {
        raw.toIntOrNull()?.let { return it }
        raw.split("_").lastOrNull()?.toIntOrNull()?.let { return it }
        var hash = 5381L
        for (b in raw.toByteArray(Charsets.UTF_8)) {
            // 64-bit wrapping arithmetic, identical to Swift's `&*` / `&+`, so ids match iOS.
            hash = hash * 33 + (b.toInt() and 0xff)
        }
        return (Math.abs(hash % 1_000_003L) + 1).toInt()
    }
}
