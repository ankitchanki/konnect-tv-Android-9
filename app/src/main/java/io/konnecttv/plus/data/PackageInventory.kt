package io.konnecttv.plus.data

/** Owner-profile inventory includes disabled packages and system APKs uninstalled only for that user. */
internal fun parsePackageInventory(output: String, label: (String) -> String): List<TvApp> {
    val sections = output.split("__PLUS_SECTION__")
    fun packages(index: Int) = sections.getOrNull(index).orEmpty().lineSequence()
        .map { it.trim() }.filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").trim() }.filter { it.matches(Regex("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)*")) }.toSet()
    val installed = packages(0)
    val system = packages(1)
    val disabled = packages(2)
    val availableSystem = packages(3)
    return (installed + availableSystem).map { pkg ->
        TvApp(pkg, label(pkg), pkg in system || pkg in availableSystem, pkg in disabled || pkg !in installed, pkg in installed)
    }.sortedBy { it.label.lowercase() }
}
