package io.konnecttv

import io.konnecttv.adb.shellText
import io.konnecttv.data.DebloatList
import io.konnecttv.data.Group
import io.konnecttv.data.SafeEntry
import io.konnecttv.data.TvApp
import io.konnecttv.data.prettyName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val list = DebloatList(
        safe = mapOf(
            "com.sony.dtv.sonyselect" to SafeEntry("com.sony.dtv.sonyselect", "Sony Select", "ads", false),
            "com.google.android.leanback.ime" to SafeEntry("com.google.android.leanback.ime", "Keyboard", "bad entry", false),
        ),
        protectedPkgs = setOf("com.android.vending"),
        protectedParts = listOf(".ime", "bluetooth", "tvinput"),
        labels = mapOf("com.netflix.ninja" to "Netflix"),
    )

    @Test fun groups() {
        assertEquals(Group.SAFE, list.group(TvApp("com.sony.dtv.sonyselect", "", system = true, disabled = false)))
        assertEquals(Group.NEEDED, list.group(TvApp("com.sony.dtv.unknown", "", system = true, disabled = false)))
        assertEquals(Group.YOURS, list.group(TvApp("com.netflix.ninja", "", system = false, disabled = false)))
        // Protection wins even if the safe list is wrong.
        assertEquals(Group.NEEDED, list.group(TvApp("com.google.android.leanback.ime", "", system = true, disabled = false)))
    }

    @Test fun protection() {
        assertTrue(list.isProtected("com.android.vending"))
        assertTrue(list.isProtected("com.android.bluetooth"))
        assertTrue(list.isProtected("com.mediatek.tvinput"))
        assertFalse(list.isProtected("com.netflix.ninja"))
    }

    @Test fun labels() {
        assertEquals("Netflix", list.label("com.netflix.ninja"))
        assertEquals("Sony Select", list.label("com.sony.dtv.sonyselect"))
        assertEquals("Sonyselect", prettyName("com.sony.dtv.sonyselect"))
        assertEquals("Hotstar", prettyName("in.startv.hotstar.tv"))
    }

    @Test fun launcherSwapNeverTouchesSettingsOrSetup() {
        val protected = { p: String -> p in setOf("com.google.android.tvlauncher", "com.android.settings", "com.google.android.tungsten.setupwraith") }
        assertTrue(isRivalLauncher("com.google.android.tvlauncher", protected))
        assertTrue(isRivalLauncher("com.mitv.tvhome", protected))
        assertFalse(isRivalLauncher("com.android.settings", protected))
        assertFalse(isRivalLauncher("com.android.tv.settings", protected))
        assertFalse(isRivalLauncher("com.google.android.tungsten.setupwraith", protected))
        assertFalse(isRivalLauncher("com.ansub.tvhome", protected))
    }

    @Test fun shellQuoting() {
        assertEquals("'hello%sworld'", shellText("hello world"))
        assertEquals("'it'\\''s'", shellText("it's"))
    }
}
