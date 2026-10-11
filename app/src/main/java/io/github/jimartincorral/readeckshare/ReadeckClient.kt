package io.github.jimartincorral.readeckshare

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal client for the two Readeck API calls this app needs. */
class ReadeckClient(baseUrl: String, private val token: String) {
    private val base = baseUrl.trimEnd('/')

    /** The server answered, but not with success. */
    class HttpException(val code: Int, message: String) : Exception(message)

    /** POST /api/bookmarks. Readeck answers 202 and fetches the article in the background. */
    fun addBookmark(url: String, title: String?, labels: List<String>) {
        val body = JSONObject().put("url", url)
        if (!title.isNullOrBlank()) body.put("title", title)
        if (labels.isNotEmpty()) body.put("labels", JSONArray(labels))
        request("POST", "/api/bookmarks", body.toString())
    }

    /** GET /api/profile, used to check the server URL and token. Returns the username. */
    fun username(): String {
        val text = request("GET", "/api/profile", null)
        return try {
            JSONObject(text).optJSONObject("user")?.optString("username").orEmpty()
        } catch (e: JSONException) {
            ""
        }
    }

    private fun request(method: String, path: String, body: String?): String {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val ok = code in 200..299
            val stream = if (ok) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) throw HttpException(code, describeError(code, text))
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun describeError(code: Int, text: String): String = when (code) {
        401 -> "Invalid API token"
        403 -> "The API token isn’t allowed to do this"
        404 -> "No Readeck API at this URL"
        in 300..399 -> "Server redirected (HTTP $code) — check the URL, e.g. https:// instead of http://"
        else -> {
            val message = try {
                JSONObject(text).optString("message")
            } catch (e: JSONException) {
                ""
            }
            message.ifBlank { "HTTP $code" }
        }
    }
}
