package com.example.chat_ui.debug

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Backend Chat multi-model tester for your Cloud Run backend.
 *
 * - Fetch models: GET /v1/models
 * - Chat completion: POST /v1/chat
 *
 * Requires Firebase ID Token:
 * Authorization: Bearer <Firebase ID Token>
 */
class BackendChatMultiModelTester(
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ModelResult(
        val modelId: String,
        val ok: Boolean,
        val output: String
    )

    suspend fun fetchModelIds(): List<String> {
        val token = tokenProvider()
        val url = endpoint("/models")

        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .build()

        val body = httpText(req)
        val json = JSONObject(body)

        val data = json.optJSONArray("data") ?: JSONArray()
        val out = ArrayList<String>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            if (id.isNotBlank()) out.add(id)
        }
        return out
    }

    suspend fun chatOnce(modelId: String, userText: String): String {
        val token = tokenProvider()
        val url = endpoint("/chat")

        val payload = JSONObject().apply {
            put("model", modelId)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "You are a helpful assistant. Keep responses short."))
                    put(JSONObject().put("role", "user").put("content", userText))
                }
            )
            put("temperature", 0)
            put("max_tokens", 96)
        }

        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .build()

        val body = httpText(req)
        val json = JSONObject(body)

        // OpenAI-compatible: choices[0].message.content
        val choices = json.optJSONArray("choices")
        val first = choices?.optJSONObject(0)
        val msg = first?.optJSONObject("message")
        val content = msg?.optString("content")

        return content ?: body
    }

    suspend fun runMultiModelTest(
        prompt: String = "Reply with exactly one sentence: What is 7 + 5?"
    ): List<ModelResult> {
        val modelIds = fetchModelIds()
        val results = ArrayList<ModelResult>(modelIds.size)

        for (id in modelIds) {
            try {
                val out = chatOnce(id, prompt)
                results.add(ModelResult(id, true, out))
            } catch (e: Exception) {
                results.add(ModelResult(id, false, e.message ?: "Unknown error"))
            }
        }

        return results
    }

    private suspend fun httpText(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw RuntimeException("HTTP ${res.code}: $text")
            }
            text
        }
    }

    private fun endpoint(path: String): String {
        val root = baseUrl.trimEnd('/')
        return if (root.endsWith("/v1")) "$root$path" else "$root/v1$path"
    }
}

/**
 * Token provider for Android app using FirebaseAuth current user.
 * Call this AFTER the user is logged in.
 */
object FirebaseIdTokenProvider {
    suspend fun getIdToken(forceRefresh: Boolean = true): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("No Firebase user. Please sign in first.")

        val tokenResult = user.getIdToken(forceRefresh).await()
        val token = tokenResult.token
        if (token.isNullOrBlank()) throw IllegalStateException("Firebase ID token is null/blank.")
        return token
    }
}

/**
 * Minimal Task<T>.await() without extra dependencies.
 */
private suspend fun <T> Task<T>.await(): T = suspendCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
    addOnCanceledListener { cont.resumeWithException(CancellationException("Task was cancelled.")) }
}
