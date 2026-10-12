package io.github.jimartincorral.readeckshare

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.IOException

class SettingsActivity : Activity() {

    private lateinit var queueSection: View
    private lateinit var queueHeader: TextView
    private lateinit var queueList: ViewGroup

    // Kept in a field: SharedPreferences only holds listeners weakly.
    private val queueListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> renderQueue() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        queueSection = findViewById(R.id.queue_section)
        queueHeader = findViewById(R.id.queue_header)
        queueList = findViewById(R.id.queue_list)

        val prefs = Prefs(this)
        val serverUrl = findViewById<EditText>(R.id.server_url)
        val token = findViewById<EditText>(R.id.api_token)
        val labels = findViewById<EditText>(R.id.labels)
        val save = findViewById<Button>(R.id.save)
        val result = findViewById<TextView>(R.id.result)

        if (savedInstanceState == null) {
            serverUrl.setText(prefs.serverUrl)
            token.setText(prefs.token)
            labels.setText(prefs.labelsText)
        }

        save.setOnClickListener {
            val url = Prefs.normalizeServerUrl(serverUrl.text.toString())
            val tokenValue = token.text.toString().trim()
            if (url.isEmpty() || tokenValue.isEmpty()) {
                result.text = getString(R.string.missing_fields)
                return@setOnClickListener
            }
            serverUrl.setText(url)
            prefs.serverUrl = url
            prefs.token = tokenValue
            prefs.labelsText = Prefs.parseLabels(labels.text.toString()).joinToString(", ")
            labels.setText(prefs.labelsText)

            save.isEnabled = false
            result.text = getString(R.string.testing)
            Thread {
                val message = try {
                    val username = ReadeckClient(url, tokenValue).username()
                    if (username.isEmpty()) getString(R.string.connected)
                    else getString(R.string.connected_as, username)
                } catch (e: ReadeckClient.HttpException) {
                    e.message
                } catch (e: IOException) {
                    "${getString(R.string.unreachable)}: ${e.message ?: e.javaClass.simpleName}"
                } catch (e: IllegalArgumentException) {
                    e.message
                }
                runOnUiThread {
                    if (!isDestroyed) {
                        result.text = message
                        save.isEnabled = true
                    }
                }
            }.start()
        }
    }

    override fun onStart() {
        super.onStart()
        PendingStore.prefs(this).registerOnSharedPreferenceChangeListener(queueListener)
        renderQueue()
    }

    override fun onStop() {
        PendingStore.prefs(this).unregisterOnSharedPreferenceChangeListener(queueListener)
        super.onStop()
    }

    /** Shows links that haven't reached Readeck yet, newest first. */
    private fun renderQueue() {
        val items = PendingStore.all(this).sortedByDescending { it.sharedAt }
        queueSection.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        queueHeader.text = getString(R.string.queue_header, items.size)
        queueList.removeAllViews()
        for (item in items) {
            val row = layoutInflater.inflate(R.layout.item_pending, queueList, false)
            row.findViewById<TextView>(R.id.title).text = item.title ?: item.url
            row.findViewById<TextView>(R.id.subtitle).text =
                listOfNotNull(Uri.parse(item.url).host, describe(item)).joinToString(" · ")
            row.findViewById<View>(R.id.link).setOnClickListener { openInBrowser(item.url) }
            row.findViewById<View>(R.id.retry).setOnClickListener { SaveWorker.retry(this, item.url) }
            row.findViewById<View>(R.id.remove).setOnClickListener { SaveWorker.remove(this, item.url) }
            queueList.addView(row)
        }
    }

    private fun describe(item: PendingLink): String = when {
        item.state == PendingLink.State.FAILED -> getString(R.string.queue_failed, item.error.orEmpty())
        item.error != null -> getString(R.string.queue_retrying, item.error)
        else -> getString(R.string.queue_waiting)
    }

    private fun openInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // No browser installed; nothing useful to do.
        }
    }
}
