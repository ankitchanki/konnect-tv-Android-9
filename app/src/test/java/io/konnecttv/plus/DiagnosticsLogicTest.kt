package io.konnecttv.plus

import io.konnecttv.plus.adb.parseShellResult
import io.konnecttv.plus.adb.installationHelp
import io.konnecttv.plus.data.parsePackageInventory
import io.konnecttv.plus.data.TvIdentity
import io.konnecttv.plus.diagnostics.LogSanitizer
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsLogicTest {
    @Test fun shellKeepsCompleteResponseAndExitCode() {
        val r = parseShellResult("warning\r\nFailure [INSTALL_FAILED_OLDER_SDK]\r\nMARK:1\r\nMARK_DONE\r\n", "MARK")
        assertEquals(1, r.exitCode)
        assertTrue(r.output.contains("warning"))
        assertTrue(r.output.contains("INSTALL_FAILED_OLDER_SDK"))
        assertTrue(installationHelp(r.output).contains("newer Android"))
    }

    @Test(expected = java.io.IOException::class) fun incompleteShellResponseIsNeverSuccess() {
        parseShellResult("Success\n", "MARK")
    }

    @Test fun shellSuccessDoesNotDependOnTheFirstOutputLine() {
        val r = parseShellResult("warning\nSuccess\nMARK:0\nMARK_DONE\n", "MARK")
        assertEquals(0, r.exitCode)
        assertEquals("warning\nSuccess", r.output)
    }

    @Test fun diagnosticsOmitSecretsAddressesAndTypedText() {
        val clean = LogSanitizer.clean("connect 192.168.1.25 pairingCode=123456 token=abc123 serialno=ABC123 test@example.com content://photos/private")
        for (secret in listOf("192.168.1.25", "123456", "abc123", "ABC123", "test@example.com", "content://photos/private")) assertFalse(clean, clean.contains(secret))
        assertFalse(LogSanitizer.command("input text 'private search'").contains("private search"))
        assertFalse(LogSanitizer.output("getprop ro.serialno", "PRIVATE123").contains("PRIVATE123"))
        assertFalse(LogSanitizer.output("app_process /system/bin io.konnecttv.plus.adb.IconDump", "binary-payload").contains("binary-payload"))
    }

    @Test fun redactionRetainsDiagnosticTimingAndInstallCodes() {
        val s = LogSanitizer.clean("12:34:56.789 INSTALL_FAILED_NO_MATCHING_ABIS endpoint fe80::1234")
        assertTrue(s, s.contains("12:34:56.789"))
        assertTrue(s.contains("INSTALL_FAILED_NO_MATCHING_ABIS"))
        assertFalse(s.contains("fe80::1234"))
    }

    @Test fun inventoryKeepsExternallyDisabledAndRemovedSystemApps() {
        val r = parsePackageInventory("package:com.tcl.bi\npackage:com.tcl.tv\n__PLUS_SECTION__\npackage:com.tcl.bi\npackage:com.tcl.tv\n__PLUS_SECTION__\npackage:com.tcl.bi\n__PLUS_SECTION__\npackage:com.tcl.bi\npackage:com.tcl.tv\npackage:com.tcl.gallery\n") { it }
        assertEquals(3, r.size)
        assertTrue(r.single { it.pkg == "com.tcl.bi" }.disabled)
        assertTrue(r.single { it.pkg == "com.tcl.bi" }.installed)
        assertFalse(r.single { it.pkg == "com.tcl.gallery" }.installed)
        assertTrue(r.single { it.pkg == "com.tcl.gallery" }.system)
        assertFalse(r.single { it.pkg == "com.tcl.tv" }.disabled)
    }

    @Test fun manufacturerAndPackageEvidenceDetectOemWithoutGuessingSafety() {
        assertEquals("TCL", TvIdentity("TCL", "BeyondTV", "9", 28, "TCL").detectedBrand(emptyList()))
        assertEquals("TCL / TCL-built", TvIdentity("Unknown", "TV", "9", 28, "Unknown").detectedBrand(listOf("com.tcl.tv")))
        assertEquals("Unknown", TvIdentity("Unknown", "TV", "9", 28, "Unknown").detectedBrand(listOf("com.mediatek.tv")))
    }
}
