package local.cinematictv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTest {
    @Test
    fun stockLauncherWithSystemPriorityCannotBeReplaced() {
        val homes = listOf(HomeEntry("me", 0), HomeEntry("launcherx", 2), HomeEntry("settings", -1000))
        assertFalse(homeReplaceable(homes, "me"))
    }

    @Test
    fun equalPriorityLaunchersLetTheUserChoose() {
        assertTrue(homeReplaceable(listOf(HomeEntry("tvlauncher", 0), HomeEntry("me", 0)), "me"))
    }

    @Test
    fun notRegisteredAsHomeMeansNotReplaceable() {
        assertFalse(homeReplaceable(listOf(HomeEntry("launcherx", 0)), "me"))
    }
}
