package local.cinematictv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.os.Build

const val PREFS = "cinematictv"
/** Epoch millis until which the Home Redirect leaves Google TV's home on screen. */
const val KEY_STOCK_VISIT = "stock_visit_until"
const val STOCK_VISIT_MS = 10 * 60_000L
private const val KEY_RECENTS = "recents"

enum class Kind { VIDEO, MUSIC, GAME, OTHER }

class TvApp(
    val label: String,
    val component: ComponentName,
    val banner: Drawable?,
    val icon: Drawable,
    val kind: Kind,
)

// shortcut: many TV apps leave android:appCategory unset, so well-known package names fill the gap;
// extend these lists when an installed app lands in the wrong row.
private val MUSIC_HINTS = listOf("music", "spotify", "deezer", "tidal", "soundcloud", "pandora", "audible", "podcast", "radio")
private val VIDEO_HINTS = listOf(
    "netflix", "youtube", "primevideo", "amazon.avod", "disney", "hulu", "hbo", ".max", "plex", "kodi", "vlc",
    "mxtech", "twitch", "crunchyroll", "paramount", "peacock", "tubi", "pluto", "apple.atve", "mubi", "dazn",
    "molotov", "canal", "francetv", "arte", "shahid", "osn", "starz", "jellyfin", "emby", "stremio",
)

fun kindOf(info: ApplicationInfo): Kind {
    if (Build.VERSION.SDK_INT >= 26) when (info.category) {
        ApplicationInfo.CATEGORY_VIDEO -> return Kind.VIDEO
        ApplicationInfo.CATEGORY_AUDIO -> return Kind.MUSIC
        ApplicationInfo.CATEGORY_GAME -> return Kind.GAME
    }
    @Suppress("DEPRECATION")
    if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) return Kind.GAME
    return kindFromPackage(info.packageName)
}

fun kindFromPackage(packageName: String): Kind {
    val pkg = packageName.lowercase()
    return when {
        MUSIC_HINTS.any { it in pkg } -> Kind.MUSIC // first, so YouTube Music isn't filed as video
        VIDEO_HINTS.any { it in pkg } -> Kind.VIDEO
        else -> Kind.OTHER
    }
}

/** Every TV-compatible (Leanback) app except REM itself, alphabetically. */
fun Context.loadApps(): List<TvApp> {
    val pm = packageManager
    val tvApps = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    return pm.queryIntentActivities(tvApps, 0)
        .filter { it.activityInfo.packageName != packageName }
        .map {
            val component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
            val banner = runCatching { pm.getActivityBanner(component) }.getOrNull()
            TvApp(it.loadLabel(pm).toString(), component, banner, it.loadIcon(pm), kindOf(it.activityInfo.applicationInfo))
        }
        .sortedBy { it.label.lowercase() }
}

/** The app that plays live channels (Live TV / inputs), if the device has one among the TV apps. */
fun Context.liveTvApp(apps: List<TvApp>): TvApp? {
    val live = Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)
    val pkgs = packageManager.queryIntentActivities(live, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
    return apps.firstOrNull { it.component.packageName in pkgs }
}

/** Stored as "pkg/.Activity epochMillis" lines, newest first; malformed lines are skipped. */
fun parseRecents(raw: String): List<Pair<String, Long>> = raw.lines().mapNotNull { line ->
    val parts = line.split(' ')
    val time = parts.getOrNull(1)?.toLongOrNull()
    if (parts.size == 2 && '/' in parts[0] && time != null) parts[0] to time else null
}

fun pushRecent(list: List<Pair<String, Long>>, key: String, now: Long, max: Int = 12) =
    (listOf(key to now) + list.filter { it.first != key }).take(max)

/** Apps opened from REM, newest first, with when. Kept by REM itself, no permissions needed. */
fun SharedPreferences.recents(): List<Pair<ComponentName, Long>> =
    parseRecents(getString(KEY_RECENTS, "").orEmpty())
        .mapNotNull { (key, time) -> ComponentName.unflattenFromString(key)?.let { it to time } }

fun SharedPreferences.recordOpened(component: ComponentName, now: Long) {
    val list = pushRecent(parseRecents(getString(KEY_RECENTS, "").orEmpty()), component.flattenToString(), now)
    edit().putString(KEY_RECENTS, list.joinToString("\n") { "${it.first} ${it.second}" }).apply()
}
