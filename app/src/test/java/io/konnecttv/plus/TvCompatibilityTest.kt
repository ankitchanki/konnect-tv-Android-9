package io.konnecttv.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvCompatibilityTest {
    private val pkg = "io.konnecttv.glowlauncher"
    private val activity = "$pkg/.HomeActivity"

    @Test fun android9DoesNotUseRoleManager() {
        assertEquals("cmd package set-home-activity $activity", homeSelectionCommand(28, pkg, activity))
    }

    @Test fun android10AndLaterUseRoleManagerAndPackageManager() {
        for (sdk in listOf(29, 30, 36, 37)) {
            val command = homeSelectionCommand(sdk, pkg, activity)
            assertTrue(command.startsWith("cmd role add-role-holder --user 0 android.app.role.HOME $pkg; "))
            assertTrue(command.endsWith("cmd package set-home-activity $activity"))
        }
    }

    @Test fun acceptsBothHomeComponentFormats() {
        assertTrue(matchesHomeComponent(activity, pkg, ".HomeActivity"))
        assertTrue(matchesHomeComponent("$pkg/$pkg.HomeActivity\n", pkg, ".HomeActivity"))
        assertFalse(matchesHomeComponent("android/com.android.internal.app.ResolverActivity", pkg, ".HomeActivity"))
        assertFalse(matchesHomeComponent("$pkg/.OtherActivity", pkg, ".HomeActivity"))
        assertFalse(matchesHomeComponent("No activity found", pkg, ".HomeActivity"))
    }

    @Test fun validatesDebuggingPortsWithoutOverflow() {
        assertEquals(5555, adbPort("5555"))
        assertEquals(1, adbPort("1"))
        assertEquals(65535, adbPort("65535"))
        for (bad in listOf("", "0", "-1", "65536", "99999999999999999999999", "5555abc")) {
            assertNull(adbPort(bad))
        }
    }
}
