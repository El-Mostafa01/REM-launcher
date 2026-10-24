package local.cinematictv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** Colours of the cinematic theme (see the reference mock-up: deep navy, glass, blue focus glow). */
object Palette {
    const val TEXT = Color.WHITE
    const val TEXT_DIM = 0xFFAEB8CC.toInt()
    const val TEXT_ACCENT = 0xFF8CC8FF.toInt()
    const val GLOW = 0xFF5FB0FF.toInt()
    const val RING = 0xF2CFE6FF.toInt()
    const val SURFACE = 0xCC1A2236.toInt()
    const val GLASS = 0x26FFFFFF
    const val GLASS_EDGE = 0x33FFFFFF
    const val GREEN = 0xFF4ADE80.toInt()
    const val AMBER = 0xFFFFC266.toInt()
    const val NAV_TEXT = 0xFFC9D2E3.toInt()
}

fun Context.dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dp(v: Int) = dp(v.toFloat()).toInt()

fun Context.rounded(color: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Float = 0f) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp)
    if (strokeDp > 0) setStroke(dp(strokeDp).toInt(), strokeColor)
}

fun Context.gradient(colors: IntArray, radiusDp: Float) =
    GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = dp(radiusDp) }

/** Background per state: focused wins over selected (current page) over default. */
fun states(focused: Drawable, default: Drawable, selected: Drawable? = null) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), focused)
    if (selected != null) addState(intArrayOf(android.R.attr.state_selected), selected)
    addState(intArrayOf(), default)
}

fun textStates(focused: Int, default: Int, selected: Int = default) = ColorStateList(
    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_selected), intArrayOf()),
    intArrayOf(focused, selected, default),
)

fun Context.text(value: CharSequence, sp: Float, color: Int = Palette.TEXT, medium: Boolean = false) = TextView(this).apply {
    text = value
    textSize = sp
    setTextColor(color)
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END
    if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

/** Puts a 24dp vector icon before the text, tinted like the text in every state. */
fun TextView.leadingIcon(res: Int, sizeDp: Int = 18) {
    val icon = context.getDrawable(res)?.mutate() ?: return
    icon.setBounds(0, 0, context.dp(sizeDp), context.dp(sizeDp))
    setCompoundDrawablesRelative(icon, null, null, null)
    compoundDrawablePadding = context.dp(10)
    compoundDrawableTintList = textColors
}

/** tvOS-style focus: the view grows, lifts with a blue glow and shows a bright rim. */
fun View.liftOnFocus(scale: Float, radiusDp: Float, rim: Boolean = true, onFocused: () -> Unit = {}) {
    isFocusable = true
    isClickable = true
    if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
    if (Build.VERSION.SDK_INT >= 28) {
        outlineSpotShadowColor = Palette.GLOW
        outlineAmbientShadowColor = Palette.GLOW
    }
    val ring = if (rim) context.rounded(Color.TRANSPARENT, radiusDp, Palette.RING, 2.5f).apply { alpha = 0 } else null
    if (ring != null) foreground = ring
    setOnFocusChangeListener { v, focused ->
        val s = if (focused) scale else 1f
        v.animate().scaleX(s).scaleY(s).translationZ(if (focused) context.dp(18f) else 0f).setDuration(170).start()
        ring?.alpha = if (focused) 255 else 0
        if (focused) onFocused()
    }
}

/** Rounded pill button: white when focused, glass (or solid white for the primary action) otherwise. */
fun Context.pill(label: String, icon: Int?, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
    text = label
    textSize = 16f
    gravity = Gravity.CENTER
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    setPadding(dp(22), 0, dp(26), 0)
    background = states(
        focused = rounded(Color.WHITE, 24f),
        default = if (primary) rounded(0xEEFFFFFF.toInt(), 24f) else rounded(Palette.GLASS, 24f, Palette.GLASS_EDGE, 1f),
    )
    setTextColor(textStates(focused = Color.BLACK, default = if (primary) Color.BLACK else Color.WHITE))
    if (icon != null) leadingIcon(icon)
    liftOnFocus(1.06f, 24f, rim = false)
    setOnClickListener { onClick() }
}
