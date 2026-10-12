package io.github.jimartincorral.readeckshare

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A shared link that hasn't reached Readeck yet. */
data class PendingLink(
    val url: String,
    val title: String?,
    val labels: List<String>,
    val sharedAt: Long,
    val state: State,
    /** Why the last attempt failed, if it did. */
    val error: String?,
) {
    enum class State { PENDING, FAILED }
}

/**
 * The app's own list of unsent links, shown on the main screen. WorkManager does the
 * sending, but it doesn't expose a job's URL and forgets failed jobs, so we keep this
 * alongside it. Entries are keyed by URL, like the unique work names in [SaveWorker].
 */
object PendingStore {
    private const val PREFS_NAME = "queue"
    private const val KEY_ITEMS = "items"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<PendingLink> = decode(prefs(context).getString(KEY_ITEMS, null))

    @Synchronized
    fun get(context: Context, url: String): PendingLink? = all(context).firstOrNull { it.url == url }

    /** Adds a link, or resets an existing entry for the same URL to pending. */
    @Synchronized
    fun add(context: Context, link: SharedLink, labels: List<String>, now: Long = System.currentTimeMillis()) =
        update(context) { upsert(it, link, labels, now) }

    @Synchronized
    fun markRetrying(context: Context, url: String, error: String) =
        update(context) { setState(it, url, PendingLink.State.PENDING, error) }

    @Synchronized
    fun markFailed(context: Context, url: String, error: String) =
        update(context) { setState(it, url, PendingLink.State.FAILED, error) }

    @Synchronized
    fun remove(context: Context, url: String) = update(context) { list -> list.filter { it.url != url } }

    private fun update(context: Context, transform: (List<PendingLink>) -> List<PendingLink>) {
        val prefs = prefs(context)
        val updated = transform(decode(prefs.getString(KEY_ITEMS, null)))
        prefs.edit().putString(KEY_ITEMS, encode(updated)).apply()
    }

    // Pure helpers below, kept separate so they can be unit-tested.

    fun upsert(list: List<PendingLink>, link: SharedLink, labels: List<String>, now: Long): List<PendingLink> {
        val existing = list.firstOrNull { it.url == link.url }
        val entry = PendingLink(
            url = link.url,
            title = link.title ?: existing?.title,
            labels = labels,
            sharedAt = existing?.sharedAt ?: now,
            state = PendingLink.State.PENDING,
            error = null,
        )
        return if (existing == null) list + entry else list.map { if (it.url == link.url) entry else it }
    }

    // Only touches entries that still exist, so a link the user removed doesn't come back.
    fun setState(list: List<PendingLink>, url: String, state: PendingLink.State, error: String?) =
        list.map { if (it.url == url) it.copy(state = state, error = error) else it }

    fun encode(list: List<PendingLink>): String = JSONArray().apply {
        list.forEach { item ->
            put(
                JSONObject()
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("labels", JSONArray(item.labels))
                    .put("sharedAt", item.sharedAt)
                    .put("state", item.state.name)
                    .put("error", item.error)
            )
        }
    }.toString()

    fun decode(text: String?): List<PendingLink> {
        if (text.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val labels = o.optJSONArray("labels")
                PendingLink(
                    url = o.getString("url"),
                    title = o.optStringOrNull("title"),
                    labels = labels?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                    sharedAt = o.optLong("sharedAt"),
                    state = runCatching { PendingLink.State.valueOf(o.getString("state")) }
                        .getOrDefault(PendingLink.State.PENDING),
                    error = o.optStringOrNull("error"),
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    // optString() turns a missing value into "" (or "null"), so check explicitly.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
