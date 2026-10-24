package local.cinematictv

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** A Home activity as Android resolves it: its package and intent-filter priority. */
data class HomeEntry(val pkg: String, val priority: Int)

/**
 * Android picks the highest-priority Home activity before it even looks at the role holder or the user's
 * default (ComputerEngine.chooseBestActivity). Sideloaded apps are capped at priority 0, so a stock launcher
 * with a system priority above that keeps the Home button, which the Google TV emulator confirms even when
 * REM holds the Home role. Only disabling the stock launcher would change that, and REM never does.
 */
fun homeReplaceable(homes: List<HomeEntry>, me: String): Boolean {
    val mine = homes.firstOrNull { it.pkg == me } ?: return false
    return homes.none { it.pkg != me && it.priority > mine.priority }
}

private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

private fun Context.homes() =
    packageManager.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)

/** True only when the Home button really opens REM (what Android resolves, not just who holds the role). */
fun Context.isDefaultHome() =
    packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == packageName

fun Context.holdsHomeRole() =
    Build.VERSION.SDK_INT >= 29 && getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

fun Context.canReplaceHome() =
    homeReplaceable(homes().map { HomeEntry(it.activityInfo.packageName, it.priority) }, packageName)

/** The system screen that lets the user pick a Home app, if this device has a working one. */
fun Context.homeSetupIntent(): Intent? {
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= 29) {
            getSystemService(RoleManager::class.java)
                ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }
                ?.let { add(it.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
        }
        add(Intent(Settings.ACTION_HOME_SETTINGS))
        add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }
    // shortcut: Google TV answers these Settings actions with an empty stub screen; matched by name.
    return candidates.firstOrNull {
        val target = it.resolveActivity(packageManager)
        target != null && !target.className.endsWith("StubActivity")
    }
}

/** Packages of the device's own launchers (Google TV home), excluding REM and the Settings fallback. */
fun Context.stockHomePackages() =
    homes().filter { it.activityInfo.packageName != packageName && it.priority >= 0 }
        .map { it.activityInfo.packageName }.distinct()

/** Explicit intent for the device's own launcher (Google TV home), bypassing any default. */
fun Context.stockHomeIntent(): Intent? =
    homes().filter { it.activityInfo.packageName != packageName }
        .maxByOrNull { it.priority }
        ?.let {
            homeIntent().setClassName(it.activityInfo.packageName, it.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

/** Whether the user switched on REM's Home Redirect accessibility service. */
fun Context.redirectEnabled(): Boolean {
    val me = ComponentName(this, HomeRedirectService::class.java)
    return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        .split(':').any { ComponentName.unflattenFromString(it) == me }
}

/** REM behaves as the home screen: Back must not leave it. */
fun Context.actsAsHome() = isDefaultHome() || redirectEnabled()
