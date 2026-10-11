package io.github.jimartincorral.readeckshare

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager

/**
 * Receives shared links. Saving starts immediately; this small sheet at the bottom of
 * the screen only reports progress and then closes itself, leaving the user in the app
 * they shared from.
 */
class ShareActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progress: ProgressBar
    private lateinit var icon: ImageView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var action: Button

    private var workName: String? = null
    private var workInfos: LiveData<List<WorkInfo>>? = null
    private val observer = Observer<List<WorkInfo>> { infos -> infos.lastOrNull()?.let(::onWorkUpdate) }
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        // Let touches outside the sheet reach the app underneath.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val sheet = findViewById<View>(R.id.sheet)
        progress = findViewById(R.id.progress)
        icon = findViewById(R.id.icon)
        status = findViewById(R.id.status)
        detail = findViewById(R.id.detail)
        action = findViewById(R.id.action)
        sheet.setOnClickListener { finish() }
        keepClearOfNavigationBar(sheet)

        val prefs = Prefs(this)
        if (!prefs.isConfigured) {
            showResult(R.drawable.ic_error, getString(R.string.not_configured))
            action.visibility = View.VISIBLE
            action.setOnClickListener {
                startActivity(
                    Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            }
            return
        }

        val link = UrlExtractor.fromShare(
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
        )
        if (link == null) {
            showResult(R.drawable.ic_error, getString(R.string.no_link))
            closeAfter(ERROR_DELAY_MS)
            return
        }

        detail.text = link.title ?: link.url
        detail.visibility = View.VISIBLE

        val name = SaveWorker.enqueue(this, link, prefs.labels)
        workName = name
        visibleWorkName = name

        if (!isOnline()) {
            showQueued()
            return
        }
        workInfos = WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(name)
            .also { it.observeForever(observer) }
        handler.postDelayed({ if (!finished) showQueued() }, WAIT_FOR_RESULT_MS)
    }

    private fun onWorkUpdate(info: WorkInfo) {
        when (info.state) {
            WorkInfo.State.SUCCEEDED -> {
                showResult(R.drawable.ic_check, getString(R.string.saved))
                closeAfter(SUCCESS_DELAY_MS)
            }
            WorkInfo.State.FAILED -> {
                val error = info.outputData.getString(SaveWorker.KEY_ERROR)
                showResult(R.drawable.ic_error, getString(R.string.save_failed))
                if (error != null) detail.text = error
                closeAfter(ERROR_DELAY_MS)
            }
            WorkInfo.State.CANCELLED -> finish()
            // Already failed once and is waiting to retry: the server isn't reachable right now.
            WorkInfo.State.ENQUEUED -> if (info.runAttemptCount > 0) showQueued()
            else -> Unit
        }
    }

    private fun showQueued() {
        if (finished) return
        showResult(R.drawable.ic_bookmark, getString(R.string.queued))
        closeAfter(QUEUED_DELAY_MS)
    }

    private fun showResult(iconRes: Int, message: String) {
        progress.visibility = View.GONE
        icon.setImageResource(iconRes)
        icon.visibility = View.VISIBLE
        status.text = message
    }

    private fun closeAfter(delayMs: Long) {
        finished = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, delayMs)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun keepClearOfNavigationBar(sheet: View) {
        val basePadding = sheet.paddingBottom
        sheet.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePadding + bottom)
            insets
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        workInfos?.removeObserver(observer)
        if (visibleWorkName == workName) visibleWorkName = null
        super.onDestroy()
    }

    companion object {
        /** Unique work name of the save the sheet is currently showing, if any. */
        @Volatile
        var visibleWorkName: String? = null

        private const val WAIT_FOR_RESULT_MS = 4_000L
        private const val SUCCESS_DELAY_MS = 900L
        private const val QUEUED_DELAY_MS = 1_800L
        private const val ERROR_DELAY_MS = 3_000L
    }
}
