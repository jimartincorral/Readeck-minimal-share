package io.github.jimartincorral.readeckshare

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) = sp.edit().putString(KEY_SERVER_URL, value).apply()

    var token: String
        get() = sp.getString(KEY_TOKEN, "").orEmpty()
        set(value) = sp.edit().putString(KEY_TOKEN, value).apply()

    var labelsText: String
        get() = sp.getString(KEY_LABELS, "").orEmpty()
        set(value) = sp.edit().putString(KEY_LABELS, value).apply()

    val labels: List<String> get() = parseLabels(labelsText)

    val isConfigured: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_LABELS = "labels"

        /** "readeck.example.com/" -> "https://readeck.example.com" */
        fun normalizeServerUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            return if (hasScheme) trimmed else "https://$trimmed"
        }

        fun parseLabels(text: String): List<String> =
            text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}
