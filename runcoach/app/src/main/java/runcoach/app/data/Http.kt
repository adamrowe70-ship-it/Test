package runcoach.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal val http = OkHttpClient()
internal val json = Json { ignoreUnknownKeys = true }

class HttpException(val code: Int, message: String) : Exception("HTTP $code: $message")

internal suspend fun execute(request: Request): JsonElement = withContext(Dispatchers.IO) {
    http.newCall(request).execute().use { r: Response ->
        val body = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw HttpException(r.code, body.take(300))
        if (body.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(body)
    }
}

internal operator fun JsonElement.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
internal val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
internal val JsonElement?.dbl: Double? get() = (this as? JsonPrimitive)?.doubleOrNull
internal val JsonElement?.lng: Long? get() = (this as? JsonPrimitive)?.longOrNull
