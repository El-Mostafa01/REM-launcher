package local.cinematictv

import org.junit.Assert.assertEquals
import org.junit.Test

class AppsTest {
    @Test
    fun youTubeMusicIsMusicNotVideo() {
        assertEquals(Kind.MUSIC, kindFromPackage("com.google.android.youtube.tvmusic"))
        assertEquals(Kind.VIDEO, kindFromPackage("com.google.android.youtube.tv"))
        assertEquals(Kind.VIDEO, kindFromPackage("com.netflix.ninja"))
        assertEquals(Kind.OTHER, kindFromPackage("com.android.tv.settings"))
    }

    @Test
    fun recentsSkipMalformedLinesAndKeepOrder() {
        val raw = "com.a/.Main 200\ngarbage\ncom.b/.Main notanumber\ncom.c/.Main 100"
        assertEquals(listOf("com.a/.Main" to 200L, "com.c/.Main" to 100L), parseRecents(raw))
        assertEquals(emptyList<Pair<String, Long>>(), parseRecents(""))
    }

    @Test
    fun reopeningMovesToFrontWithoutDuplicatesAndCaps() {
        val list = listOf("a/.X" to 1L, "b/.X" to 2L)
        assertEquals(listOf("b/.X" to 9L, "a/.X" to 1L), pushRecent(list, "b/.X", 9L))
        assertEquals(2, pushRecent(list, "c/.X", 9L, max = 2).size)
    }
}
