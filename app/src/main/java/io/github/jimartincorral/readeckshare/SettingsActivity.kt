package io.github.jimartincorral.readeckshare

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.IOException

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

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
}
