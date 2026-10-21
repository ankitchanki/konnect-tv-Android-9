package io.konnecttv.plus.companion

import org.junit.Assert.*
import org.junit.Test

class CompanionSignaturesTest {
    private val path="package:/data/app/old-build/base.apk\n"
    private val bundled=ApkIdentity(setOf("new-sha256"))
    @Test fun absentAndMalformedOutputNeverAuthorizeRemoval() {
        assertEquals(SignatureMatch.ABSENT,compareCompanionSignatures("",null,bundled))
        assertEquals(SignatureMatch.UNKNOWN,compareCompanionSignatures("signatures=PackageSignatures{123abc [deadbeef]}",null,bundled))
        assertEquals(SignatureMatch.UNKNOWN,compareCompanionSignatures(path,null,bundled))
    }
    @Test fun differentActualApkCertificateIsAMismatch() {
        assertEquals(SignatureMatch.MISMATCH,compareCompanionSignatures(path,ApkIdentity(setOf("foreign-sha256")),bundled))
        assertEquals(SignatureMatch.MATCH,compareCompanionSignatures(path,bundled,bundled))
    }
    @Test fun supportedSigningRotationIsNotAMismatch() {
        assertEquals(SignatureMatch.MATCH,compareCompanionSignatures(path,ApkIdentity(setOf("old")),ApkIdentity(setOf("new"),setOf("old","new"))))
        assertEquals(SignatureMatch.MISMATCH,compareCompanionSignatures(path,ApkIdentity(setOf("old","other")),bundled))
    }
    @Test fun pmPathChoosesBaseAmongSplitApks() {
        assertEquals("/data/app/old-build/base.apk",installedApkPath("package:/data/app/old-build/split_en.apk\n$path"))
        assertNull(installedApkPath("Error: package not found"))
    }
    @Test fun installErrorFallbackRequiresExactMismatchMarker() {
        assertTrue(isSignatureInstallFailure("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package signatures do not match]"))
        assertFalse(isSignatureInstallFailure("INSTALL_FAILED_INSUFFICIENT_STORAGE"))
    }
    @Test fun settingsFallbackHomeIsNotAUsableStockLauncher() {
        assertNull(stockHomeComponent("$COMPANION_PACKAGE/.HomeActivity", "com.android.settings/.FallbackHome", "package:com.android.settings"))
    }
    @Test fun restoreChoosesOnlyAnEnabledSystemHome() {
        val homes="io.konnecttv.glowlauncher/.HomeActivity\norg.launcher.thirdparty/.Home\ncom.google.android.tvlauncher/.MainActivity"
        assertEquals("com.google.android.tvlauncher/.MainActivity",stockHomeComponent("io.konnecttv.glowlauncher/.HomeActivity",homes,"package:com.google.android.tvlauncher"))
        assertNull(stockHomeComponent("",homes,"package:io.konnecttv.glowlauncher"))
    }
}

class StockHomeRestorerTest {
    private fun transport(commands: MutableList<String>, current: String) = object: CompanionTransport {
        override fun shell(command: String): String {
            commands+=command
            return when {
                "resolve-activity" in command -> current
                command=="settings get secure enabled_accessibility_services" -> "$COMPANION_PACKAGE/.HomeButtonService:org.reader/.Accessibility"
                else -> ""
            }
        }
        override fun copyApk(remotePath: String, destination: java.io.File) = error("No APK read during Home restore")
        override fun install(apk: java.io.File) = error("No install during Home restore")
    }
    @Test fun anotherSelectedHomeAndUnrelatedAccessibilityServiceAreKept() {
        val commands=mutableListOf<String>()
        restoreStockHome(transport(commands,"org.other.launcher/.Home"))
        assertTrue(commands.contains("settings put secure enabled_accessibility_services 'org.reader/.Accessibility'"))
        assertFalse(commands.any { "set-home" in it || "am start" in it || "uninstall" in it })
    }
    @Test fun noEnabledStockHomeStopsBeforeChangingSettingsOrRemovingAnything() {
        val commands=mutableListOf<String>()
        try { restoreStockHome(transport(commands,"$COMPANION_PACKAGE/.HomeActivity"));fail("Missing stock Home must stop replacement") }
        catch(expected: IllegalStateException) { assertTrue(expected.message.orEmpty().contains("was not uninstalled")) }
        assertFalse(commands.any { it.startsWith("settings put") || "uninstall" in it || "set-home" in it })
    }
}
