package io.konnecttv.plus.companion

import io.konnecttv.plus.homeSelectionCommand

/** Preserve another selected Home; otherwise verify a stock Home before removing our package. */
fun restoreStockHome(transport: CompanionTransport) {
    val resolve = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    val current = transport.shell(resolve)
    val currentHome = homeComponents(current).firstOrNull { it.substringBefore('/') !in setOf(COMPANION_PACKAGE, "android") }
    val stock = if (currentHome != null) null else {
        val candidates = transport.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        checkNotNull(stockHomeComponent(current, candidates, transport.shell("pm list packages -s"))) {
            "No enabled stock TV Home was found. Enable the TV's own launcher in TV Settings, then try again. Glowlauncher was not uninstalled."
        }
    }
    // Remove only our interception, preserving services added by other apps or phones.
    val existing = transport.shell("settings get secure enabled_accessibility_services").trim()
    val retained = existing.split(':').filter { it.isNotBlank() && it != "null" && it.substringBefore('/') != COMPANION_PACKAGE }
    val value = retained.joinToString(":").replace("'", "'\\''")
    transport.shell("settings put secure enabled_accessibility_services '$value'")
    if (stock == null) return
    val sdk = transport.shell("getprop ro.build.version.sdk").trim().toIntOrNull() ?: 28
    transport.shell(homeSelectionCommand(sdk, stock.substringBefore('/'), stock))
    val selected = transport.shell(resolve)
    check(homeComponents(selected).any { it.substringBefore('/') == stock.substringBefore('/') }) {
        "The TV did not confirm its stock Home. Glowlauncher was not uninstalled; select the TV's own Home in TV Settings."
    }
    transport.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME -n '$stock'")
}
