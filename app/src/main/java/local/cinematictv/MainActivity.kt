package local.cinematictv

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private const val COLUMNS = 5
private const val KEY_ONBOARDED = "onboarded"
private const val KEY_SETTINGS = "settings"

class MainActivity : Activity() {

    private class TvApp(val label: String, val component: ComponentName, val banner: Drawable?, val icon: Drawable)

    private val prefs by lazy { getSharedPreferences("cinematictv", MODE_PRIVATE) }
    private lateinit var content: ViewGroup
    private lateinit var scroll: ScrollView
    private lateinit var grid: GridLayout
    private lateinit var backdrop: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroHint: TextView
    private lateinit var overlay: View
    private lateinit var panelTitle: TextView
    private lateinit var panelBody: TextView
    private lateinit var panelButtons: LinearLayout
    private var shown: List<ComponentName>? = null
    private var lastFocus: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.content)
        scroll = findViewById(R.id.scroll)
        grid = findViewById(R.id.grid)
        backdrop = findViewById(R.id.backdrop)
        heroTitle = findViewById(R.id.hero_title)
        heroHint = findViewById(R.id.hero_hint)
        overlay = findViewById(R.id.overlay)
        panelTitle = findViewById(R.id.panel_title)
        panelBody = findViewById(R.id.panel_body)
        panelButtons = findViewById(R.id.panel_buttons)
        if (Build.VERSION.SDK_INT >= 31) {
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(dp(40f), dp(40f), Shader.TileMode.CLAMP))
        }
        if (!prefs.getBoolean(KEY_ONBOARDED, false)) showWelcome()
    }

    override fun onResume() {
        super.onResume()
        // Re-scan on every return so apps installed meanwhile (e.g. from USB) show up.
        Thread {
            val apps = loadApps()
            runOnUiThread { if (!isDestroyed && apps.map { it.component } != shown) render(apps) }
        }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home (or the app icon) pressed while already open: back to the first app, like any launcher.
        if (overlay.visibility == View.VISIBLE && prefs.getBoolean(KEY_ONBOARDED, false)) hidePanel()
        scroll.smoothScrollTo(0, 0)
        firstCard()?.requestFocus()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        when {
            overlay.visibility == View.VISIBLE -> hidePanel()
            isDefaultHome() -> Unit // a Home app has nothing behind it
            else -> super.onBackPressed() // back to Google TV
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU) return super.onKeyDown(keyCode, event)
        if (overlay.visibility != View.VISIBLE) showSettings()
        return true
    }

    private fun loadApps(): List<TvApp> {
        val pm = packageManager
        val tvApps = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(tvApps, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                val banner = runCatching { pm.getActivityBanner(component) }.getOrNull()
                TvApp(it.loadLabel(pm).toString(), component, banner, it.loadIcon(pm))
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun render(apps: List<TvApp>) {
        val focusedKey = currentFocus?.tag
        shown = apps.map { it.component }
        grid.removeAllViews()
        val gap = dp(20)
        val inset = resources.getDimensionPixelSize(R.dimen.safe_horizontal) * 2
        // Width from the real screen so 1080p at any density keeps 5 columns inside the safe area.
        val width = (resources.displayMetrics.widthPixels - inset - gap * (COLUMNS - 1)) / COLUMNS
        apps.forEach { app ->
            addTile(app.component, app.label, app.banner, app.icon, app.banner ?: app.icon, width, gap,
                getString(R.string.hint_open)) { launch(app) }
        }
        addTile(KEY_SETTINGS, getString(R.string.settings), null, getDrawable(R.drawable.ic_settings), null,
            width, gap, getString(R.string.hint_settings)) { showSettings() }
        if (overlay.visibility != View.VISIBLE) (grid.findViewWithTag<View>(focusedKey) ?: firstCard())?.requestFocus()
    }

    private fun addTile(
        key: Any, label: String, banner: Drawable?, icon: Drawable?, art: Drawable?,
        width: Int, gap: Int, hint: String, onClick: () -> Unit,
    ) {
        val height = width * 9 / 16
        val card = FrameLayout(this).apply {
            tag = key
            isFocusable = true
            isClickable = true
            contentDescription = label
            background = getDrawable(R.drawable.tile_bg)
            foreground = getDrawable(R.drawable.tile_shine)?.mutate()?.apply { alpha = 0 }
            clipToOutline = true
            if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            setOnClickListener { onClick() }
        }
        if (banner != null) {
            card.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(banner)
            })
        } else {
            // No TV banner: icon and name on the card, like the stock launcher does.
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ImageView(this@MainActivity).apply { setImageDrawable(icon) },
                    LinearLayout.LayoutParams(height / 2, height / 2))
                addView(label(label, 13f).apply { setPadding(dp(8), dp(6), dp(8), 0) })
            })
        }
        val caption = label(label, 15f).apply {
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(card, LinearLayout.LayoutParams(width, height))
            addView(caption, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
        }
        val lastColumn = grid.childCount % COLUMNS == COLUMNS - 1
        grid.addView(tile, GridLayout.LayoutParams().apply {
            this.width = width
            setMargins(0, 0, if (lastColumn) 0 else gap, gap)
        })
        card.setOnFocusChangeListener { _, focused ->
            val scale = if (focused) 1.1f else 1f
            card.animate().scaleX(scale).scaleY(scale).translationZ(if (focused) dp(16f) else 0f)
                .setDuration(180).start()
            // Icon cards already print their name inside; only banner cards need the caption.
            caption.animate().alpha(if (focused && banner != null) 1f else 0f).setDuration(180).start()
            card.foreground?.alpha = if (focused) 255 else 0
            if (focused) showHero(label, hint, art)
        }
    }

    private fun showHero(title: String, hint: String, art: Drawable?) {
        heroTitle.text = title
        heroHint.text = hint
        val copy = art?.constantState?.newDrawable()?.mutate()
        backdrop.animate().alpha(0f).setDuration(120).withEndAction {
            backdrop.setImageDrawable(copy)
            if (copy != null) backdrop.animate().alpha(0.55f).setDuration(260).start()
        }.start()
    }

    private fun launch(app: TvApp) = open(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    )

    private fun open(intent: Intent?, forResult: Boolean = false) {
        try {
            if (intent != null) {
                // The Home role request only works with a caller, hence startActivityForResult.
                if (forResult) startActivityForResult(intent, 1) else startActivity(intent)
                return
            }
        } catch (e: RuntimeException) { // missing screen or blocked by the device
        }
        Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
    }

    private fun homeStatus() = getString(
        when {
            isDefaultHome() -> R.string.home_is_default
            !canReplaceHome() -> R.string.home_locked
            homeSetupIntent() != null -> R.string.home_available
            else -> R.string.home_press_home
        }
    )

    /** Only offered when this device really lets a sideloaded app become Home. */
    private fun setHomeButton(): Pair<String, () -> Unit>? {
        val setup = homeSetupIntent()?.takeIf { !isDefaultHome() && canReplaceHome() } ?: return null
        return getString(R.string.set_as_home) to { open(setup, forResult = true) }
    }

    private fun showWelcome() = showPanel(
        getString(R.string.welcome_title),
        getString(R.string.welcome_body) + "\n\n" + homeStatus(),
        listOfNotNull(getString(R.string.get_started) to {}, setHomeButton()),
    )

    private fun showSettings() = showPanel(
        getString(R.string.settings),
        homeStatus() + "\n\n" + getString(R.string.version, BuildConfig.VERSION_NAME),
        listOfNotNull(
            setHomeButton(),
            getString(R.string.go_google_tv) to { open(stockHomeIntent()) },
            getString(R.string.tv_settings) to { open(Intent(Settings.ACTION_SETTINGS)) },
            getString(R.string.app_info) to {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            },
        ),
    )

    private fun showPanel(title: String, body: String, buttons: List<Pair<String, () -> Unit>>) {
        if (overlay.visibility != View.VISIBLE) lastFocus = currentFocus
        panelTitle.text = title
        panelBody.text = body
        panelButtons.removeAllViews()
        buttons.forEach { (text, action) ->
            panelButtons.addView(TextView(this).apply {
                this.text = text
                textSize = 18f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(getColorStateList(R.color.button_text))
                background = getDrawable(R.drawable.button_bg)
                setPadding(dp(24), 0, dp(24), 0)
                isFocusable = true
                isClickable = true
                setOnClickListener { hidePanel(); action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10) })
        }
        // Keep the remote's focus inside the panel instead of wandering onto tiles underneath.
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        overlay.visibility = View.VISIBLE
        panelButtons.getChildAt(0)?.requestFocus()
    }

    private fun hidePanel() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        overlay.visibility = View.GONE
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        (lastFocus?.takeIf { it.isAttachedToWindow } ?: firstCard())?.requestFocus()
    }

    private fun firstCard() = (grid.getChildAt(0) as? ViewGroup)?.getChildAt(0)

    private fun label(text: String, sp: Float) = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dp(v: Int) = dp(v.toFloat()).toInt()
}
