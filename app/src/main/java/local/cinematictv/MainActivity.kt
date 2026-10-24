package local.cinematictv

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateUtils
import android.view.FocusFinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

private const val KEY_ONBOARDED = "onboarded"
private const val GRID_COLUMNS = 5
private const val HERO_SLIDES = 4
private const val HERO_ROTATE_MS = 8000L
private val HERO_ART = intArrayOf(R.drawable.hero_dusk, R.drawable.hero_aurora, R.drawable.hero_ocean)

class MainActivity : Activity() {

    private enum class Page(val title: Int, val icon: Int, val inNav: Boolean = true) {
        HOME(R.string.nav_home, R.drawable.ic_home),
        VIDEO(R.string.nav_video, R.drawable.ic_play),
        MUSIC(R.string.nav_music, R.drawable.ic_music),
        GAMES(R.string.cat_games, R.drawable.ic_games, inNav = false),
        APPS(R.string.nav_apps, R.drawable.ic_apps),
        SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
    }

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val navButtons = mutableMapOf<Page, View>()
    private lateinit var page: FrameLayout
    private lateinit var overlay: View
    private lateinit var panelTitle: TextView
    private lateinit var panelBody: TextView
    private lateinit var panelButtons: LinearLayout
    private var apps: List<TvApp> = emptyList()
    private var current = Page.HOME
    private var hero: Hero? = null
    private var heroIndex = 0
    private var lastFocus: View? = null

    private val rotate = object : Runnable {
        override fun run() {
            hero?.takeIf { !it.view.hasFocus() }?.let { it.show(it.index + 1) }
            handler.postDelayed(this, HERO_ROTATE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        page = findViewById(R.id.page)
        overlay = findViewById(R.id.overlay)
        panelTitle = findViewById(R.id.panel_title)
        panelBody = findViewById(R.id.panel_body)
        panelButtons = findViewById(R.id.panel_buttons)
        buildTopBar()
        if (!prefs.getBoolean(KEY_ONBOARDED, false)) showWelcome()
    }

    override fun onResume() {
        super.onResume()
        prefs.edit().remove(KEY_STOCK_VISIT).apply() // back in REM: a Google TV visit is over

        // Re-scan on every return: new installs, the Recently opened row and Settings switches stay current.
        Thread {
            val loaded = loadApps()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                apps = loaded
                showPage(current, keepFocus = true)
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(rotate)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed (or the redirect fired) while open: back to the top of Home, like any launcher.
        if (overlay.visibility == View.VISIBLE && prefs.getBoolean(KEY_ONBOARDED, false)) hidePanel()
        showPage(Page.HOME)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        when {
            overlay.visibility == View.VISIBLE -> hidePanel()
            current != Page.HOME -> showPage(Page.HOME)
            actsAsHome() -> Unit // a home screen has nothing behind it
            else -> super.onBackPressed() // back to Google TV
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU) return super.onKeyDown(keyCode, event)
        if (overlay.visibility != View.VISIBLE) showPage(Page.SETTINGS)
        return true
    }

    // ---- Top bar -------------------------------------------------------------------------------

    private fun buildTopBar() {
        val nav = findViewById<LinearLayout>(R.id.nav)
        Page.entries.filter { it.inNav }.forEach { p ->
            val button = TextView(this).apply {
                text = getString(p.title)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(20), 0)
                background = states(
                    focused = rounded(Color.WHITE, 22f),
                    default = rounded(Color.TRANSPARENT, 22f),
                    selected = gradient(intArrayOf(0xFF4E8DF5.toInt(), 0xFF2F57C8.toInt()), 22f),
                )
                setTextColor(textStates(focused = Color.BLACK, default = Palette.NAV_TEXT, selected = Color.WHITE))
                leadingIcon(p.icon)
                liftOnFocus(1.04f, 22f, rim = false)
                setOnClickListener { showPage(p) }
            }
            nav.addView(button, LinearLayout.LayoutParams(WRAP_CONTENT, dp(42)).apply { marginEnd = dp(2) })
            navButtons[p] = button
        }
        findViewById<ImageView>(R.id.search).apply {
            background = states(focused = rounded(Color.WHITE, 22f), default = rounded(Palette.GLASS, 22f))
            imageTintList = textStates(focused = Color.BLACK, default = Color.WHITE)
            liftOnFocus(1.08f, 22f, rim = false)
            setOnClickListener {
                open(Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH), Intent(Intent.ACTION_ASSIST))
            }
        }
    }

    // ---- Pages ---------------------------------------------------------------------------------

    private fun showPage(p: Page, keepFocus: Boolean = false) {
        val focusTag = if (keepFocus && page.hasFocus()) currentFocus?.tag else null
        val keepNavFocus = keepFocus && navButtons.values.any { it.hasFocus() }
        current = p
        navButtons.forEach { (navPage, view) -> view.isSelected = navPage == p }
        handler.removeCallbacks(rotate)
        hero = null
        page.removeAllViews()
        page.addView(
            when (p) {
                Page.HOME -> homePage()
                Page.VIDEO -> gridPage(R.string.page_video, apps.filter { it.kind == Kind.VIDEO }, R.string.empty_video)
                Page.MUSIC -> gridPage(R.string.page_music, apps.filter { it.kind == Kind.MUSIC }, R.string.empty_music)
                Page.GAMES -> gridPage(R.string.page_games, apps.filter { it.kind == Kind.GAME }, R.string.empty_games)
                Page.APPS -> gridPage(R.string.page_apps, apps, R.string.empty_apps)
                Page.SETTINGS -> settingsPage()
            }
        )
        if (hero != null) handler.postDelayed(rotate, HERO_ROTATE_MS)
        if (overlay.visibility == View.VISIBLE || keepNavFocus) return
        val target = focusTag?.let { page.findViewWithTag<View>(it) }
            ?: page.getFocusables(View.FOCUS_FORWARD).firstOrNull()
        target?.requestFocus()
    }

    private fun recentApps(): List<Pair<TvApp, Long>> =
        prefs.recents().mapNotNull { (component, time) -> apps.firstOrNull { it.component == component }?.let { it to time } }

    private fun homePage(): View {
        val column = column()
        val recent = recentApps()
        val video = apps.filter { it.kind == Kind.VIDEO }
        val featured = (recent.map { it.first } + video + apps).distinctBy { it.component }.take(HERO_SLIDES)
        if (featured.isNotEmpty()) {
            val h = Hero(featured, recent.toMap())
            hero = h
            column.addView(h.view, LinearLayout.LayoutParams(MATCH_PARENT, dp(196)).apply { inset() })
        }
        column.addView(categoryRow(recent.map { it.first } + video))
        if (recent.isNotEmpty()) {
            column.addView(row(R.string.row_recent, recent.map { it.first }, wide = true) { app ->
                recent.firstOrNull { it.first == app }?.second?.let { getString(R.string.opened_ago, ago(it)) }
            })
        }
        if (video.isNotEmpty()) column.addView(row(R.string.row_video, video, wide = true))
        apps.filter { it.kind == Kind.MUSIC || it.kind == Kind.GAME }.takeIf { it.isNotEmpty() }
            ?.let { column.addView(row(R.string.row_music_games, it, wide = false)) }
        column.addView(row(R.string.row_all, apps, wide = false))
        return scroll(column)
    }

    private fun categoryRow(favorites: List<TvApp>): View {
        val row = LinearLayout(this).apply { clipChildren = false }
        val live = liveTvApp(apps)
        val tiles = mutableListOf<View>()
        tiles += categoryTile("cat:video", getString(R.string.cat_video), R.drawable.ic_play, 0xFFFF4F64.toInt(), 0xFF3A1A26.toInt()) { showPage(Page.VIDEO) }
        tiles += categoryTile("cat:music", getString(R.string.cat_music), R.drawable.ic_music, 0xFFB48CFF.toInt(), 0xFF2A2048.toInt()) { showPage(Page.MUSIC) }
        if (live != null) {
            tiles += categoryTile("cat:live", getString(R.string.cat_live), R.drawable.ic_live_tv, 0xFF5DB8FF.toInt(), 0xFF142B45.toInt()) { launch(live) }
        }
        tiles += categoryTile("cat:games", getString(R.string.cat_games), R.drawable.ic_games, 0xFF4ADE80.toInt(), 0xFF153227.toInt()) { showPage(Page.GAMES) }
        // Favourite apps fill the row up to 8 tiles, with their own icons.
        favorites.distinctBy { it.component }.filter { it != live }.take(7 - tiles.size).forEach { app ->
            tiles += categoryTile("fav:${app.component.flattenToString()}", app.label, null, 0, 0xFF1E2537.toInt(), app) { launch(app) }
        }
        tiles += categoryTile("cat:apps", getString(R.string.cat_apps), R.drawable.ic_apps, Color.WHITE, 0xFF22283A.toInt()) { showPage(Page.APPS) }
        tiles.forEachIndexed { i, tile ->
            row.addView(tile, LinearLayout.LayoutParams(0, dp(66), 1f).apply { if (i < tiles.size - 1) marginEnd = dp(12) })
        }
        return row.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { inset(); topMargin = dp(18) } }
    }

    private fun categoryTile(key: String, label: String, icon: Int?, tint: Int, fill: Int, app: TvApp? = null, onClick: () -> Unit) =
        LinearLayout(this).apply {
            tag = key
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(fill, 18f, 0x1FFFFFFF, 1f)
            contentDescription = label
            addView(ImageView(this@MainActivity).apply {
                if (app != null) setImageDrawable(app.icon) else {
                    setImageResource(icon!!)
                    setColorFilter(tint)
                }
            }, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(text(label, 13f).apply { gravity = Gravity.CENTER; setPadding(dp(6), dp(6), dp(6), 0) })
            liftOnFocus(1.08f, 18f)
            setOnClickListener { onClick() }
        }

    /** One titled, horizontally scrolling row of app cards. */
    private fun row(title: Int, items: List<TvApp>, wide: Boolean, caption: (TvApp) -> String? = { null }): View {
        val box = column()
        box.addView(LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            addView(text(getString(title), 19f, medium = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(text(resources.getQuantityString(R.plurals.app_count, items.size, items.size), 14f, Palette.TEXT_DIM))
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { inset(); topMargin = dp(22) })
        val (w, h) = if (wide) dp(216) to dp(122) else dp(168) to dp(95)
        val strip = LinearLayout(this).apply { clipChildren = false }
        items.forEach { app ->
            strip.addView(card(app, "${getString(title)}:${app.component.flattenToString()}", caption(app)),
                LinearLayout.LayoutParams(w, h).apply { marginEnd = dp(16) })
        }
        box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
            val side = resources.getDimensionPixelSize(R.dimen.safe_horizontal)
            setPadding(side, dp(12), side, dp(14))
            addView(strip)
        })
        return box
    }

    /** An app card: its TV banner, or its icon and name when the app has no banner. */
    private fun card(app: TvApp, key: String, caption: String?) = FrameLayout(this).apply {
        tag = key
        contentDescription = app.label
        background = rounded(Palette.SURFACE, 14f)
        clipToOutline = true
        if (app.banner != null) {
            addView(ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(app.banner)
            })
        } else {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(ImageView(this@MainActivity).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(text(app.label, 13f).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), 0) })
            })
        }
        if (caption != null) {
            addView(View(this@MainActivity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE6000000.toInt(), 0x00000000))
            }, FrameLayout.LayoutParams(MATCH_PARENT, dp(44), Gravity.BOTTOM))
            addView(text(caption, 12f).apply { setPadding(dp(12), 0, dp(12), dp(8)) },
                FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        }
        liftOnFocus(1.08f, 14f)
        setOnClickListener { launch(app) }
    }

    private fun gridPage(title: Int, list: List<TvApp>, empty: Int): View {
        val column = column()
        column.addView(text(getString(title), 30f).apply { typeface = android.graphics.Typeface.create("sans-serif-light", 0) },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { inset(); topMargin = dp(8) })
        column.addView(text(if (list.isEmpty()) getString(empty) else resources.getQuantityString(R.plurals.app_count, list.size, list.size), 15f, Palette.TEXT_DIM),
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { inset(); topMargin = dp(2) })
        val gap = dp(20)
        val side = resources.getDimensionPixelSize(R.dimen.safe_horizontal)
        // Width from the real screen so 1080p at any density keeps 5 columns inside the safe area.
        val w = (resources.displayMetrics.widthPixels - 2 * side - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val grid = GridLayout(this).apply {
            columnCount = GRID_COLUMNS
            clipChildren = false
            clipToPadding = false
            setPadding(side, dp(20), side, dp(40))
        }
        list.forEachIndexed { i, app ->
            grid.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                addView(card(app, "grid:${app.component.flattenToString()}", null), LinearLayout.LayoutParams(w, w * 9 / 16))
                addView(text(app.label, 14f, Palette.TEXT_DIM).apply { setPadding(dp(4), dp(10), dp(4), 0) })
            }, GridLayout.LayoutParams().apply {
                width = w
                setMargins(0, 0, if (i % GRID_COLUMNS == GRID_COLUMNS - 1) 0 else gap, gap)
            })
        }
        column.addView(grid)
        return scroll(column)
    }

    private fun settingsPage(): View {
        val inner = column()
        inner.addView(text(getString(R.string.settings_overline), 12f, Palette.TEXT_ACCENT).apply { letterSpacing = 0.3f })
        inner.addView(text(getString(R.string.nav_settings), 30f).apply { typeface = android.graphics.Typeface.create("sans-serif-light", 0) })

        inner.addView(sectionTitle(R.string.section_home))
        val (homeStatus, homeColor) = homeStatus()
        val (redirectStatus, redirectColor) = redirectStatus()
        inner.addView(group(
            settingRow(R.string.use_default_home, R.string.use_default_home_desc, homeStatus, homeColor, checked = isDefaultHome()) { onDefaultHomeRow() },
            settingRow(R.string.redirect_title, R.string.redirect_desc, redirectStatus, redirectColor, checked = redirectEnabled()) { onRedirectRow() },
            settingRow(R.string.guided_setup, R.string.guided_setup_desc) { showGuide() },
        ))

        inner.addView(sectionTitle(R.string.section_general))
        inner.addView(group(
            settingRow(R.string.go_google_tv, R.string.go_google_tv_desc) { goToGoogleTv() },
            settingRow(R.string.tv_settings, R.string.tv_settings_desc) { open(Intent(Settings.ACTION_SETTINGS)) },
            settingRow(R.string.app_info, R.string.app_info_desc) { open(appInfoIntent()) },
            settingRow(R.string.show_welcome, R.string.show_welcome_desc) { showWelcome() },
        ))
        inner.addView(text(getString(R.string.version, BuildConfig.VERSION_NAME), 13f, Palette.TEXT_DIM).apply {
            setPadding(dp(8), dp(18), 0, dp(40))
        })
        return scroll(LinearLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(inner, LinearLayout.LayoutParams(dp(760), WRAP_CONTENT))
        })
    }

    private fun sectionTitle(res: Int) = text(getString(res), 19f, medium = true).apply { setPadding(dp(8), dp(26), 0, dp(10)) }

    private fun group(vararg rows: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Palette.SURFACE, 22f, 0x1AFFFFFF, 1f)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        clipChildren = false
        clipToPadding = false
        rows.forEach { addView(it) }
    }

    /** A focusable settings row; a switch on the right when [checked] is given, a chevron otherwise. */
    private fun settingRow(
        title: Int, description: Int, status: String? = null, statusColor: Int = Palette.TEXT_DIM,
        checked: Boolean? = null, onClick: () -> Unit,
    ) = LinearLayout(this).apply {
        tag = "setting:$title"
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        background = states(focused = rounded(0x29FFFFFF, 16f), default = rounded(Color.TRANSPARENT, 16f))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(getString(title), 17f, medium = true))
            addView(text(getString(description), 14f, Palette.TEXT_DIM).apply { maxLines = 3 })
            if (status != null) addView(text(status, 13f, statusColor).apply { maxLines = 2; setPadding(0, dp(6), 0, 0) })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (checked != null) {
            addView(Switch(this@MainActivity).apply {
                isChecked = checked
                isClickable = false
                isFocusable = false
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                trackTintList = android.content.res.ColorStateList.valueOf(if (checked) 0xFF34C759.toInt() else 0xFF4A5368.toInt())
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(16) })
        } else {
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron)
                setColorFilter(Palette.TEXT_DIM)
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(16) })
        }
        liftOnFocus(1.015f, 16f, rim = false)
        setOnClickListener { onClick() }
    }

    // ---- Default Home Experience ---------------------------------------------------------------

    private fun homeStatus(): Pair<String, Int> = when {
        isDefaultHome() -> getString(R.string.home_status_active) to Palette.GREEN
        holdsHomeRole() -> getString(R.string.home_status_role_ignored) to Palette.AMBER
        !canReplaceHome() -> getString(R.string.home_status_blocked) to Palette.AMBER
        homeSetupIntent() != null -> getString(R.string.home_status_available) to Palette.TEXT_DIM
        else -> getString(R.string.home_status_press_home) to Palette.TEXT_DIM
    }

    private fun redirectStatus(): Pair<String, Int> =
        if (redirectEnabled()) getString(R.string.redirect_on) to Palette.GREEN
        else getString(R.string.redirect_off) to Palette.TEXT_DIM

    private fun onDefaultHomeRow() {
        val setup = homeSetupIntent()
        when {
            isDefaultHome() -> open(setup ?: appInfoIntent()) // change or clear the choice
            canReplaceHome() && setup != null -> open(setup, forResult = true)
            else -> showGuide()
        }
    }

    private fun onRedirectRow() {
        if (!redirectEnabled()) return showGuide()
        val running = HomeRedirectService.instance
        if (running != null) running.disableSelf() else open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        handler.postDelayed({ if (current == Page.SETTINGS) showPage(Page.SETTINGS, keepFocus = true) }, 400)
    }

    private fun showGuide() {
        if (canReplaceHome()) {
            val setup = homeSetupIntent()
            showPanel(getString(R.string.guide_title), getString(R.string.guide_body_open), listOfNotNull(
                setup?.let { getString(R.string.choose_home) to { open(it, forResult = true) } },
                getString(R.string.close) to {},
            ))
        } else {
            showPanel(getString(R.string.guide_title), getString(R.string.guide_body_google_tv), listOf(
                getString(R.string.open_accessibility) to { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                getString(R.string.app_info_short) to { open(appInfoIntent()) },
                getString(R.string.close) to {},
            ))
        }
    }

    private fun goToGoogleTv() {
        // Tell the Home Redirect to leave Google TV on screen for a while (ends early when REM reopens).
        prefs.edit().putLong(KEY_STOCK_VISIT, System.currentTimeMillis() + STOCK_VISIT_MS).apply()
        open(stockHomeIntent())
    }

    private fun appInfoIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    // ---- Panels (welcome, guided setup) ---------------------------------------------------------

    private fun showWelcome() = showPanel(
        getString(R.string.welcome_title),
        getString(R.string.welcome_body),
        listOf(
            getString(R.string.set_up_home) to { showPage(Page.SETTINGS); onDefaultHomeRow() },
            getString(R.string.get_started) to {},
        ),
    )

    private fun showPanel(title: String, body: String, buttons: List<Pair<String, () -> Unit>>) {
        if (overlay.visibility != View.VISIBLE) lastFocus = currentFocus
        panelTitle.text = title
        panelBody.text = body
        panelButtons.removeAllViews()
        buttons.forEachIndexed { i, (label, action) ->
            panelButtons.addView(pill(label, null, primary = i == 0) { hidePanel(); action() },
                LinearLayout.LayoutParams(WRAP_CONTENT, dp(46)).apply { marginEnd = dp(14) })
        }
        // Keep the remote's focus inside the panel instead of wandering onto tiles underneath.
        findViewById<ViewGroup>(R.id.content).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        overlay.visibility = View.VISIBLE
        panelButtons.getChildAt(0)?.requestFocus()
    }

    private fun hidePanel() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        overlay.visibility = View.GONE
        findViewById<ViewGroup>(R.id.content).descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        (lastFocus?.takeIf { it.isAttachedToWindow } ?: page.getFocusables(View.FOCUS_FORWARD).firstOrNull())?.requestFocus()
    }

    // ---- Launching -------------------------------------------------------------------------------

    private fun launch(app: TvApp) {
        prefs.recordOpened(app.component, System.currentTimeMillis())
        open(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }

    /** Starts the first intent the device can handle; the Home role request needs a caller (forResult). */
    private fun open(vararg intents: Intent?, forResult: Boolean = false) {
        for (intent in intents.filterNotNull()) {
            try {
                if (forResult) startActivityForResult(intent, 1) else startActivity(intent)
                return
            } catch (e: RuntimeException) { // missing screen or blocked by the device: try the next one
            }
        }
        Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
    }

    // ---- Small helpers ---------------------------------------------------------------------------

    private fun ago(time: Long): String {
        val now = System.currentTimeMillis()
        if (now - time < DateUtils.MINUTE_IN_MILLIS) return getString(R.string.just_now)
        return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
    }

    private fun scroll(child: View) = object : ScrollView(this) {
        override fun executeKeyEvent(event: KeyEvent): Boolean {
            // Up with nothing focusable above goes straight to the top bar instead of nudging the scroll.
            val focused = findFocus()
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && focused != null &&
                FocusFinder.getInstance().findNextFocus(this, focused, View.FOCUS_UP) == null
            ) {
                if (event.action == KeyEvent.ACTION_DOWN) smoothScrollTo(0, 0)
                return false
            }
            return super.executeKeyEvent(event)
        }
    }.apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipChildren = false // focused cards may grow past their row; the page frame still clips
        clipToPadding = false
        setPadding(0, dp(6), 0, dp(36))
        addView(child)
    }

    private fun ViewGroup.MarginLayoutParams.inset() {
        val side = resources.getDimensionPixelSize(R.dimen.safe_horizontal)
        marginStart = side
        marginEnd = side
    }

    // ---- Hero carousel -----------------------------------------------------------------------------

    /** The big featured card at the top of Home, cycling through recent and video apps. */
    private inner class Hero(private val items: List<TvApp>, private val opened: Map<TvApp, Long>) {
        val view = FrameLayout(this@MainActivity)
        var index = 0
        private val art = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        private val overline = text("", 12f, 0xFFB8C4DA.toInt()).apply { letterSpacing = 0.35f }
        private val title = text("", 34f).apply {
            letterSpacing = 0.18f
            typeface = android.graphics.Typeface.create("sans-serif-light", 0)
        }
        private val subtitle = text("", 16f, Palette.TEXT_ACCENT)
        private val detail = text("", 14f, Palette.TEXT_DIM)
        private val poster = FrameLayout(this@MainActivity).apply {
            background = rounded(Palette.SURFACE, 16f, Palette.GLASS_EDGE, 1f)
            clipToOutline = true
            elevation = dp(14f)
        }
        private val dots = LinearLayout(this@MainActivity)

        init {
            view.background = rounded(0xFF0B1020.toInt(), 26f, 0x1FFFFFFF, 1f)
            view.clipToOutline = true
            view.addView(art)
            view.addView(View(this@MainActivity).apply { setBackgroundResource(R.drawable.hero_scrim) })
            val open = pill(getString(R.string.open), R.drawable.ic_play, primary = true) { launch(items[index]) }
            val info = pill(getString(R.string.app_info_short), R.drawable.ic_info, primary = false) { openInfo(items[index]) }
            open.tag = "hero:open"
            info.tag = "hero:info"
            // Left of Open / right of App info flips the carousel, like tvOS.
            open.setOnKeyListener { _, code, e -> flip(code == KeyEvent.KEYCODE_DPAD_LEFT && e.action == KeyEvent.ACTION_DOWN, -1) }
            info.setOnKeyListener { _, code, e -> flip(code == KeyEvent.KEYCODE_DPAD_RIGHT && e.action == KeyEvent.ACTION_DOWN, 1) }
            view.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false // focused pills grow past the text inset
                setPadding(dp(40), 0, 0, 0)
                addView(overline)
                addView(title, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) })
                addView(subtitle, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(2) })
                addView(detail, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(2) })
                addView(LinearLayout(this@MainActivity).apply {
                    clipChildren = false
                    addView(open, LinearLayout.LayoutParams(WRAP_CONTENT, dp(44)).apply { marginEnd = dp(14) })
                    addView(info, LinearLayout.LayoutParams(WRAP_CONTENT, dp(44)))
                }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(16) })
            }, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.55f).toInt(), MATCH_PARENT))
            view.addView(poster, FrameLayout.LayoutParams(dp(232), dp(131), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(48) })
            view.addView(dots, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = dp(26)
                bottomMargin = dp(16)
            })
            show(heroIndex)
        }

        private fun flip(matches: Boolean, step: Int): Boolean {
            if (matches && items.size > 1) show(index + step)
            return matches
        }

        fun show(i: Int) {
            index = Math.floorMod(i, items.size)
            heroIndex = index
            val app = items[index]
            overline.text = getString(when (app.kind) {
                Kind.VIDEO -> R.string.kind_video
                Kind.MUSIC -> R.string.kind_music
                Kind.GAME -> R.string.kind_game
                Kind.OTHER -> R.string.kind_other
            })
            title.text = app.label.uppercase()
            subtitle.text = getString(when (app.kind) {
                Kind.VIDEO -> R.string.tagline_video
                Kind.MUSIC -> R.string.tagline_music
                Kind.GAME -> R.string.tagline_game
                Kind.OTHER -> R.string.tagline_other
            })
            detail.text = opened[app]?.let { getString(R.string.opened_ago_sentence, ago(it)) } ?: getString(R.string.installed_here)
            art.animate().alpha(0.35f).setDuration(140).withEndAction {
                art.setImageResource(HERO_ART[index % HERO_ART.size])
                art.animate().alpha(1f).setDuration(320).start()
            }.start()
            poster.removeAllViews()
            if (app.banner != null) {
                poster.addView(ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(app.banner.constantState?.newDrawable()?.mutate() ?: app.banner)
                })
            } else {
                poster.addView(ImageView(this@MainActivity).apply { setImageDrawable(app.icon.constantState?.newDrawable()?.mutate() ?: app.icon) },
                    FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))
            }
            dots.removeAllViews()
            if (items.size > 1) items.indices.forEach { d ->
                dots.addView(View(this@MainActivity).apply {
                    background = rounded(if (d == index) Color.WHITE else 0x66FFFFFF, 4f)
                }, LinearLayout.LayoutParams(if (d == index) dp(18) else dp(7), dp(7)).apply { marginStart = dp(6) })
            }
        }

        private fun openInfo(app: TvApp) =
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.component.packageName, null)))
    }
}
