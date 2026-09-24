package org.foxgirls.audioranobe.core

import android.util.Base64
import org.foxgirls.audioranobe.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiError(
    val status: Int,
    message: String,
    val code: String? = null,
    val field: String? = null,
) : Exception(message) {
    val isForbiddenWord get() = code == "forbidden_word"
    val isUnauthorized get() = status == 401
    val isNotFound get() = status == 404
}

/** Human-readable error text, like errMsg() on the web. */
fun Throwable.msg(): String = (message?.takeIf { it.isNotBlank() }) ?: "Что-то пошло не так"

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Thin HTTP client mirroring lib/api.ts: cookie session, query params, JSON bodies,
 * and the X-Me header that carries the signed-in viewer on every response.
 */
object Api {
    val baseUrl: String = BuildConfig.API_URL.trimEnd('/')
    val siteUrl: String = BuildConfig.SITE_URL.trimEnd('/')

    lateinit var cookies: CookieStore
        private set

    private val meHeader = MutableStateFlow<String?>(null)
    /** Raw X-Me header value (base64 JSON, "" when signed out, null before any response). */
    val viewerHeader: StateFlow<String?> = meHeader

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "AudioRanobe-Android/${BuildConfig.VERSION_NAME}")
                        // The backend's CORS / CSRF checks are configured for the site origin; present it like the browser does.
                        .header("Origin", siteUrl)
                        .header("Referer", "$siteUrl/")
                        .build()
                )
            }
            .build()
    }

    fun init(store: CookieStore) {
        cookies = store
    }

    fun url(path: String, params: Map<String, Any?>? = null): HttpUrl {
        val b = (baseUrl + path).toHttpUrl().newBuilder()
        params?.forEach { (k, v) ->
            when (v) {
                null, "" -> {}
                is Iterable<*> -> v.forEach { if (it != null) b.addQueryParameter(k, it.toString()) }
                is Boolean -> b.addQueryParameter(k, if (v) "1" else "0")
                else -> b.addQueryParameter(k, v.toString())
            }
        }
        return b.build()
    }

    suspend fun raw(
        method: String,
        path: String,
        params: Map<String, Any?>? = null,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): String? {
        val req = Request.Builder().url(url(path, params))
        headers.forEach { (k, v) -> req.header(k, v) }
        val rb = body ?: if (method == "GET" || method == "HEAD") null else ByteArray(0).toRequestBody(null)
        req.method(method, rb)
        val res = try {
            client.newCall(req.build()).await()
        } catch (e: IOException) {
            throw ApiError(0, "Нет соединения с сервером")
        }
        res.use { r ->
            if (!path.startsWith("/auth/")) {
                r.header("X-Me")?.let { meHeader.value = it }
            }
            val text = withContext(Dispatchers.IO) { r.body?.string() }
            if (!r.isSuccessful) {
                var message = "Ошибка запроса (${r.code})"
                var code: String? = null
                var field: String? = null
                try {
                    val obj = text?.takeIf { it.isNotBlank() }?.let { AppJson.parseToJsonElement(it).jsonObject }
                    obj?.get("error")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { message = it }
                    code = obj?.get("code")?.jsonPrimitive?.content
                    field = obj?.get("field")?.jsonPrimitive?.content
                } catch (_: Exception) {}
                throw ApiError(r.code, message, code, field)
            }
            if (r.code == 204) return null
            return text
        }
    }

    suspend inline fun <reified T> request(
        method: String,
        path: String,
        params: Map<String, Any?>? = null,
        body: JsonElement? = null,
    ): T {
        val rb = body?.let { AppJson.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON_MEDIA) }
        val text = raw(method, path, params, rb)
        return decode(text)
    }

    inline fun <reified T> decode(text: String?): T {
        if (T::class == Unit::class) return Unit as T
        if (text.isNullOrBlank()) {
            if (null is T) return null as T
            throw ApiError(0, "Пустой ответ сервера")
        }
        return AppJson.decodeFromString(text)
    }

    suspend inline fun <reified T> get(path: String, params: Map<String, Any?>? = null): T = request("GET", path, params)
    suspend inline fun <reified T> post(path: String, body: JsonElement? = null, params: Map<String, Any?>? = null): T = request("POST", path, params, body ?: JsonObject(emptyMap()))
    suspend inline fun <reified T> put(path: String, body: JsonElement? = null, params: Map<String, Any?>? = null): T = request("PUT", path, params, body ?: JsonObject(emptyMap()))
    suspend inline fun <reified T> patch(path: String, body: JsonElement? = null, params: Map<String, Any?>? = null): T = request("PATCH", path, params, body ?: JsonObject(emptyMap()))
    suspend inline fun <reified T> delete(path: String, body: JsonElement? = null, params: Map<String, Any?>? = null): T = request("DELETE", path, params, body)

    /** multipart/form-data upload (images etc.). */
    suspend inline fun <reified T> upload(path: String, method: String = "POST", build: MultipartBody.Builder.() -> Unit): T {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        mb.build()
        return decode(raw(method, path, null, mb.build()))
    }

    /** PUT raw bytes (chunked audio upload). */
    suspend inline fun <reified T> putBytes(path: String, params: Map<String, Any?>, bytes: ByteArray): T =
        decode(raw("PUT", path, params, bytes.toRequestBody("application/octet-stream".toMediaType())))

    fun decodeViewerHeader(header: String): JsonObject? {
        if (header.isEmpty()) return null
        return try {
            val bytes = Base64.decode(header, Base64.DEFAULT)
            AppJson.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
        } catch (_: Exception) {
            null
        }
    }

    val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
