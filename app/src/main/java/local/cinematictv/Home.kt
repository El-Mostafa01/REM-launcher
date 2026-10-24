package local.cinematictv

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** A Home activity as Android resolves it: its package and intent-filter priority. */
data class HomeEntry(val pkg: String, val priority: Int)

/**
 * Android picks the highest-priority Home activity before it even looks at the user's default
 * (ComputerEngine.chooseBestActivity). Sideloaded apps are capped at priority 0, so a stock launcher
 * with a system priority above that can only be bypassed by disabling it, which CinematicTV never does.
 */
fun homeReplaceable(homes: List<HomeEntry>, me: String): Boolean {
    val mine = homes.firstOrNull { it.pkg == me } ?: return false
    return homes.none { it.pkg != me && it.priority > mine.priority }
}

private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

private fun Context.homes() =
    packageManager.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)

fun Context.isDefaultHome() =
    packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == packageName

fun Context.canReplaceHome() =
    homeReplaceable(homes().map { HomeEntry(it.activityInfo.packageName, it.priority) }, packageName)

/** The system screen that lets the user pick a Home app, if this device has one. */
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
    return candidates.firstOrNull { it.resolveActivity(packageManager) != null }
}

/** Explicit intent for the device's own launcher (Google TV home), bypassing any default. */
fun Context.stockHomeIntent(): Intent? =
    homes().filter { it.activityInfo.packageName != packageName }
        .maxByOrNull { it.priority }
        ?.let {
            homeIntent().setClassName(it.activityInfo.packageName, it.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
