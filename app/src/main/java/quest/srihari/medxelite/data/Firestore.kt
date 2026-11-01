package quest.srihari.medxelite.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import quest.srihari.medxelite.Config
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class HttpException(val code: Int, val body: String) : IOException("HTTP $code")

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun call(
        method: String,
        url: String,
        token: String? = null,
        body: JsonElement? = null,
        formBody: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        if (token != null) builder.header("Authorization", "Bearer $token")
        val reqBody = when {
            formBody != null -> formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            body != null -> body.toString().toRequestBody(JSON)
            method == "POST" || method == "PATCH" -> "{}".toRequestBody(JSON)
            else -> null
        }
        builder.method(method, reqBody)
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw HttpException(resp.code, text)
            text
        }
    }
}

/** Disk cache for stale-while-revalidate reads (CacheManager.swift). */
class DiskCache(context: Context) {
    private val dir = File(context.filesDir, "medx-cache").apply { mkdirs() }
    private val launchedAt = System.currentTimeMillis()

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    fun get(key: String): JsonElement? = try {
        val f = file(key)
        if (f.exists()) MedxJson.parseToJsonElement(f.readText()) else null
    } catch (_: Exception) {
        null
    }

    fun isFresh(key: String, maxAgeMs: Long, sinceLaunch: Boolean = true): Boolean {
        val f = file(key)
        if (!f.exists()) return false
        val modified = f.lastModified()
        if (sinceLaunch && modified < launchedAt) return false
        return System.currentTimeMillis() - modified < maxAgeMs
    }

    fun set(key: String, value: JsonElement) {
        try {
            file(key).writeText(value.toString())
        } catch (_: Exception) {
        }
    }

    fun remove(key: String) {
        file(key).delete()
    }

    fun markAllStale() {
        dir.listFiles()?.forEach { it.setLastModified(0) }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0
}

/**
 * Plain Firestore REST, ported from FirestoreService.swift. Every model is read as normalised
 * JSON (Firestore's typed values flattened) and decoded leniently by the model's `from`.
 */
class Firestore(private val cache: DiskCache, private val tokenProvider: suspend () -> String) {

    companion object {
        private const val FRESHNESS = 10 * 60 * 1000L
        private const val MODULE_FRESHNESS = 24 * 60 * 60 * 1000L

        fun normalizeMap(fields: JsonObject): JsonObject =
            JsonObject(fields.mapValues { (_, v) -> if (v is JsonObject) normalizeValue(v) else v })

        fun normalizeValue(v: JsonObject): JsonElement {
            v["stringValue"]?.let { return it }
            v["integerValue"]?.let { p -> return JsonPrimitive(p.asStringOrNull()?.toLongOrNull() ?: 0L) }
            v["doubleValue"]?.let { p -> return JsonPrimitive((p as? JsonPrimitive)?.doubleOrNull ?: 0.0) }
            v["booleanValue"]?.let { return it }
            v["arrayValue"]?.let { a ->
                val values = (a as? JsonObject)?.get("values") as? JsonArray ?: JsonArray(emptyList())
                return JsonArray(values.map { if (it is JsonObject) normalizeValue(it) else it })
            }
            v["mapValue"]?.let { m ->
                val f = (m as? JsonObject)?.get("fields") as? JsonObject ?: JsonObject(emptyMap())
                return normalizeMap(f)
            }
            v["timestampValue"]?.let { return it }
            v["bytesValue"]?.let { return it }
            v["referenceValue"]?.let { return it }
            return JsonNull
        }

        fun toValue(v: JsonElement): JsonObject? = when (v) {
            is JsonNull -> JsonObject(mapOf("nullValue" to JsonNull))
            is JsonPrimitive -> when {
                v.isString -> JsonObject(mapOf("stringValue" to v))
                v.booleanOrNull != null -> JsonObject(mapOf("booleanValue" to v))
                v.longOrNull != null -> JsonObject(mapOf("integerValue" to JsonPrimitive(v.longOrNull.toString())))
                v.doubleOrNull != null -> JsonObject(mapOf("doubleValue" to v))
                else -> JsonObject(mapOf("stringValue" to JsonPrimitive(v.contentOrNull ?: "")))
            }
            is JsonArray -> JsonObject(mapOf("arrayValue" to JsonObject(mapOf("values" to JsonArray(v.mapNotNull { toValue(it) })))))
            is JsonObject -> JsonObject(mapOf("mapValue" to JsonObject(mapOf("fields" to toFields(v)))))
        }

        fun toFields(obj: JsonObject): JsonObject =
            JsonObject(obj.filterValues { it !is JsonNull }.mapNotNull { (k, v) -> toValue(v)?.let { k to it } }.toMap())

        /** `updateMask.fieldPaths` segment quoting: anything not a plain identifier is backticked. */
        fun fieldPath(segments: List<String>): String = segments.joinToString(".") { s ->
            if (Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(s)) s else "`" + s.replace("\\", "\\\\").replace("`", "\\`") + "`"
        }

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        private fun docIdOf(raw: JsonObject): String? = raw.str("name")?.substringAfterLast("/")

        fun decodeDocument(raw: JsonObject): JsonObject? {
            val fields = raw["fields"] as? JsonObject ?: return null
            val normalized = normalizeMap(fields).toMutableMap()
            if (normalized["id"] == null || normalized["id"] is JsonNull) {
                docIdOf(raw)?.let { normalized["id"] = JsonPrimitive(it) }
            }
            return JsonObject(normalized)
        }
    }

    /**
     * Inside `withContext(CacheOnly)` (a screen's first paint) any cached copy is served however
     * old, and a miss throws instead of going to the network — the Kotlin twin of iOS
     * `MedxReadPolicy.cachedFirst`, scoped to the coroutine rather than global.
     */
    object CacheOnly : CoroutineContext.Element {
        // The key is a separate object read through a getter. Passing the object itself to
        // AbstractCoroutineContextElement(CacheOnly) read it while it was still being created,
        // so the key was null and the first Firestore read after sign-in crashed the app with
        // ExceptionInInitializerError (an Error, so no catch (e: Exception) stopped it).
        override val key: CoroutineContext.Key<*> get() = CacheOnlyKey
    }

    internal object CacheOnlyKey : CoroutineContext.Key<CacheOnly>

    private suspend fun cacheOnly(): Boolean = coroutineContext[CacheOnlyKey] != null

    private suspend fun mayServeCached(key: String, maxAge: Long, sinceLaunch: Boolean = true) =
        cacheOnly() || cache.isFresh(key, maxAge, sinceLaunch)

    private suspend fun missIfCacheOnly() {
        if (cacheOnly()) throw IOException("not cached")
    }

    suspend fun token() = tokenProvider()

    // ---------------------------------------------------------------- generic

    suspend fun fetchCollection(collection: String, useCache: Boolean = true): List<JsonObject> {
        val key = "col_$collection"
        if (useCache && mayServeCached(key, FRESHNESS)) {
            (cache.get(key) as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { arr -> return arr.map { it.jsonObject } }
        }
        missIfCacheOnly()
        val out = mutableListOf<JsonObject>()
        try {
            val token = token()
            var pageToken: String? = null
            var pages = 0
            do {
                var url = "${Config.FIRESTORE_BASE}/$collection?pageSize=300"
                if (!pageToken.isNullOrEmpty()) url += "&pageToken=${enc(pageToken)}"
                val json = MedxJson.parseToJsonElement(Http.call("GET", url, token)).jsonObject
                json.objects("documents").mapNotNullTo(out) { decodeDocument(it) }
                pageToken = json.str("nextPageToken")
                pages++
            } while (!pageToken.isNullOrEmpty() && pages < 40)
        } catch (e: Exception) {
            if (useCache) (cache.get(key) as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { arr -> return arr.map { it.jsonObject } }
            throw e
        }
        if (out.isNotEmpty()) cache.set(key, JsonArray(out))
        return out
    }

    suspend fun fetchDocument(collection: String, docId: String, useCache: Boolean = true, maxAge: Long = FRESHNESS): JsonObject? {
        val key = "doc_${collection}_$docId"
        if (useCache && mayServeCached(key, maxAge)) (cache.get(key) as? JsonObject)?.let { return it }
        missIfCacheOnly()
        try {
            val token = token()
            val text = Http.call("GET", "${Config.FIRESTORE_BASE}/$collection/${enc(docId)}", token)
            val doc = decodeDocument(MedxJson.parseToJsonElement(text).jsonObject) ?: return null
            cache.set(key, doc)
            return doc
        } catch (e: HttpException) {
            if (e.code == 404) return null
            if (useCache) (cache.get(key) as? JsonObject)?.let { return it }
            throw e
        } catch (e: Exception) {
            if (useCache) (cache.get(key) as? JsonObject)?.let { return it }
            throw e
        }
    }

    /** A single equality filter. Returns normalised documents (with `id` injected). */
    suspend fun runQuery(collection: String, field: String, equals: String, useCache: Boolean = true): List<JsonObject> {
        val key = "query_${collection}_${field}_$equals"
        if (useCache && mayServeCached(key, FRESHNESS)) {
            (cache.get(key) as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { arr -> return arr.map { it.jsonObject } }
        }
        missIfCacheOnly()
        val body = jsonOf(
            "structuredQuery" to jsonOf(
                "from" to listOf(mapOf("collectionId" to collection)),
                "where" to mapOf(
                    "fieldFilter" to mapOf(
                        "field" to mapOf("fieldPath" to field),
                        "op" to "EQUAL",
                        "value" to mapOf("stringValue" to equals),
                    ),
                ),
            ),
        )
        val docs = try {
            val text = Http.call("POST", "${Config.FIRESTORE_BASE}:runQuery", token(), body)
            MedxJson.parseToJsonElement(text).jsonArray.mapNotNull { (it as? JsonObject)?.obj("document") }
                .mapNotNull { decodeDocument(it) }
        } catch (e: Exception) {
            if (useCache) (cache.get(key) as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { arr -> return arr.map { it.jsonObject } }
            throw e
        }
        if (useCache && docs.isNotEmpty()) cache.set(key, JsonArray(docs))
        return docs
    }

    suspend fun runOrderedQuery(
        collection: String,
        orderBy: String,
        limit: Int,
        startAfterTimestamp: String? = null,
        startAfterDocument: String? = null,
    ): List<JsonObject> {
        val structured = mutableMapOf<String, Any?>(
            "from" to listOf(mapOf("collectionId" to collection)),
            "orderBy" to listOf(
                mapOf("field" to mapOf("fieldPath" to orderBy), "direction" to "DESCENDING"),
                mapOf("field" to mapOf("fieldPath" to "__name__"), "direction" to "DESCENDING"),
            ),
            "limit" to limit,
        )
        if (startAfterTimestamp != null) {
            val values = mutableListOf<Map<String, String>>(mapOf("timestampValue" to startAfterTimestamp))
            if (startAfterDocument != null) values.add(mapOf("referenceValue" to startAfterDocument))
            structured["startAt"] = mapOf("values" to values, "before" to false)
        }
        val text = Http.call("POST", "${Config.FIRESTORE_BASE}:runQuery", token(), jsonOf("structuredQuery" to structured))
        return MedxJson.parseToJsonElement(text).jsonArray.mapNotNull { (it as? JsonObject)?.obj("document") }
    }

    suspend fun writeDocument(collection: String, docId: String, fields: JsonObject, merge: Boolean = false) {
        var url = "${Config.FIRESTORE_BASE}/$collection/${enc(docId)}"
        if (merge) {
            val mask = fields.keys.joinToString("&") { "updateMask.fieldPaths=${enc(fieldPath(listOf(it)))}" }
            if (mask.isNotEmpty()) url += "?$mask"
        }
        Http.call("PATCH", url, token(), JsonObject(mapOf("fields" to toFields(fields))))
    }

    suspend fun deleteDocument(collection: String, docId: String) {
        try {
            Http.call("DELETE", "${Config.FIRESTORE_BASE}/$collection/${enc(docId)}", token())
        } catch (e: HttpException) {
            if (e.code != 404) throw e
        }
    }

    /** Several known documents in one round trip; missing ones are absent from the map. */
    suspend fun batchGet(paths: List<Pair<String, String>>): Map<String, JsonObject> {
        if (paths.isEmpty()) return emptyMap()
        val names = paths.map { (c, d) -> "${Config.FIRESTORE_DOC_PREFIX}/$c/$d" }
        val text = Http.call("POST", "${Config.FIRESTORE_BASE}:batchGet", token(), jsonOf("documents" to names))
        val out = mutableMapOf<String, JsonObject>()
        MedxJson.parseToJsonElement(text).jsonArray.forEach { r ->
            val found = (r as? JsonObject)?.obj("found") ?: return@forEach
            val name = found.str("name") ?: return@forEach
            val fields = found.obj("fields") ?: return@forEach
            out[name.substringAfterLast("/")] = normalizeMap(fields)
        }
        return out
    }

    // ---------------------------------------------------------------- domain

    suspend fun fetchAriseSubjects(): List<BankSubject> =
        fetchCollection("medx_qbank_subjects").map { BankSubject.fromArise(it) }.sortedBy { it.id.toIntOrNull() ?: 0 }

    suspend fun fetchMarrowSubjects(): List<BankSubject> =
        fetchDocument("medx_meta", "qbank_fmge")?.objects("subjects")?.map { BankSubject.from(it) } ?: emptyList()

    /** Both banks; a missing Marrow index leaves ARISE working. */
    suspend fun fetchBanks(): List<BankSubject> = coroutineScope {
        val arise = async { fetchAriseSubjects() }
        val marrow = async { runCatching { fetchMarrowSubjects() }.getOrDefault(emptyList()) }
        arise.await() + marrow.await()
    }

    suspend fun fetchModule(moduleId: String): ModuleDetail {
        val key = "qb_mod_$moduleId"
        if (mayServeCached(key, MODULE_FRESHNESS, sinceLaunch = false)) {
            (cache.get(key) as? JsonObject)?.let { return ModuleDetail.from(it) }
        }
        missIfCacheOnly()
        val raw = try {
            fetchDocument("medx_qbank_modules", moduleId, useCache = false)
        } catch (e: Exception) {
            (cache.get(key) as? JsonObject)?.let { return ModuleDetail.from(it) }
            throw e
        } ?: throw IOException("Module not found")
        var detail = ModuleDetail.from(raw)
        if (detail.questions.isEmpty() && (detail.partCount ?: 0) > 0) {
            val parts = runQuery("medx_qbank_module_parts", "moduleId", moduleId)
            val qs = parts.sortedBy { it.int("part") ?: 0 }.flatMap { p -> p.objects("questions").map { Question.from(it) } }
            detail = detail.copy(questions = qs)
        }
        cache.set(key, detail.toJson())
        return detail
    }

    suspend fun fetchTests(): List<BatchTest> =
        fetchCollection("medx_tests").map { BatchTest.from(it) }
            .sortedWith(compareByDescending<BatchTest> { it.gradable }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    suspend fun fetchTestQuestions(testId: String): List<Question> {
        val parts = runQuery("medx_test_questions", "testId", testId)
        val qs = parts.sortedBy { it.int("part") ?: 0 }.flatMap { p -> p.objects("questions").map { Question.from(it) } }
        return Question.uniqued(qs)
    }

    suspend fun fetchSeries(): List<SeriesPaper> =
        fetchDocument("medx_meta", "series_fmge")?.objects("tests")?.map { SeriesPaper.from(it) }?.filter { it.hasQuestions }
            ?: emptyList()

    suspend fun fetchFlashcardSubjects(): List<FlashcardSubject> =
        fetchCollection("medx_flashcard_subjects").map { FlashcardSubject.from(it) }.sortedBy { it.name.lowercase() }

    suspend fun fetchVideos(): List<RecordedVideo> =
        fetchCollection("medx_videos").map { RecordedVideo.from(it) }

    suspend fun fetchVodPage(pageSize: Int = 48, cursor: String? = null): Pair<List<VodItem>, String?> {
        val stamp = cursor?.substringBefore("|")
        val name = cursor?.substringAfter("|", "")?.ifEmpty { null }
        val docs = runOrderedQuery("medx_vod", "uploadedAt", pageSize, stamp, name)
        val items = docs.mapNotNull { d ->
            val f = d.obj("fields") ?: return@mapNotNull null
            VodItem.from(normalizeMap(f), d.str("name")?.substringAfterLast("/") ?: "")
        }
        val next = if (docs.size < pageSize) null else {
            val last = docs.last()
            val ts = last.obj("fields")?.obj("uploadedAt")?.str("timestampValue")
                ?: items.lastOrNull()?.uploadedAtRaw
            ts?.let { "$it|${last.str("name") ?: ""}" }
        }
        return items to next
    }

    // ---- per-user rows

    suspend fun fetchAttempts(uid: String): List<SittingAttempt> =
        runQuery("medx_attempts", "uid", uid, useCache = false).map { SittingAttempt.from(it) }

    suspend fun saveAttempt(attempt: SittingAttempt) {
        val id = attempt.id ?: SittingAttempt.newId()
        writeDocument("medx_attempts", id, attempt.copy(id = id).toJson())
    }

    suspend fun deleteAttempt(id: String) = deleteDocument("medx_attempts", id)

    suspend fun fetchBookmarks(uid: String): List<BookmarkedQuestion> =
        runQuery("medx_bookmarks", "ownerId", uid, useCache = false).mapNotNull { BookmarkedQuestion.from(it) }

    suspend fun saveBookmark(b: BookmarkedQuestion) = writeDocument("medx_bookmarks", b.docId, b.toJson())

    suspend fun fetchWatchHistory(uid: String): List<WatchHistoryEntry> =
        runQuery("medx_watch_history", "ownerId", uid, useCache = false).mapNotNull { WatchHistoryEntry.from(it) }

    suspend fun saveWatchHistory(e: WatchHistoryEntry) = writeDocument("medx_watch_history", e.docId, e.toJson())

    suspend fun fetchTracker(uid: String): JsonObject? =
        runCatching { fetchDocument("user_tracker", uid, useCache = false) }.getOrNull()

    suspend fun updateTrackerCell(uid: String, subject: String, field: String, value: Boolean) {
        val path = fieldPath(listOf("subjects", subject, field))
        val url = "${Config.FIRESTORE_BASE}/user_tracker/${enc(uid)}?updateMask.fieldPaths=${enc(path)}"
        val body = JsonObject(
            mapOf(
                "fields" to toFields(jsonOf("subjects" to mapOf(subject to mapOf(field to value)))),
            ),
        )
        Http.call("PATCH", url, token(), body)
    }

    // ---- custom modules: open sign-in means the collection is no longer two people's shared
    // ---- shelf, so each user sees and edits only the papers they built (filtered by `uid`).

    suspend fun fetchCustomModules(uid: String): List<CustomModule> =
        runQuery("medx_custom_modules", "uid", uid, useCache = false).map { CustomModule.from(it) }

    suspend fun saveCustomModule(m: CustomModule) = writeDocument("medx_custom_modules", m.id, m.toJson())

    suspend fun deleteCustomModule(id: String) = deleteDocument("medx_custom_modules", id)
}
