package local.cinematictv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Opt-in Google TV workaround: whenever the stock launcher's window comes to the front (Home button,
 * leaving an app, start-up), open REM instead. Nothing is disabled; turning this service off restores
 * stock behaviour. It only receives window-change events from the stock launcher, nothing else.
 */
class HomeRedirectService : AccessibilityService() {

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val openRem = Runnable {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private var stock = emptySet<String>()

    override fun onServiceConnected() {
        instance = this
        stock = stockHomePackages().toSet()
        if (stock.isNotEmpty()) serviceInfo = serviceInfo.apply { packageNames = stock.toTypedArray() }
        // At start-up Google TV's home is already on screen before this service is bound (it waits for the
        // user unlock), so no window event arrives. shortcut: "recently booted" stands in for "stock home is
        // in front", since reading the foreground window would need screen-content access; later reconnects
        // (e.g. after a memory-pressure restart mid-film) never open REM.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) handler.postDelayed(openRem, 600)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() !in stock) return
        // "Go to Google TV home" in REM: leave Google TV on screen until REM is reopened or the visit expires.
        if (System.currentTimeMillis() < prefs.getLong(KEY_STOCK_VISIT, 0L)) return
        handler.post(openRem)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacks(openRem)
        return super.onUnbind(intent)
    }

    companion object {
        private const val BOOT_WINDOW_MS = 3 * 60_000L

        /** Set while the service is running, so REM's switch can turn it off with disableSelf(). */
        var instance: HomeRedirectService? = null
    }
}
