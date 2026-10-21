package io.konnecttv.plus.adb

data class ShellResult(val output: String, val exitCode: Int)

internal fun parseShellResult(raw: String, marker: String): ShellResult {
    val match = Regex("(?:^|\\r?\\n)${Regex.escape(marker)}:(-?\\d+)(?:\\r?\\n|$)").find(raw)
        ?: throw java.io.IOException("The TV shell closed before reporting completion.")
    return ShellResult(raw.substring(0, match.range.first).trim(), match.groupValues[1].toInt())
}

fun installationHelp(output: String): String = when {
    "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in output -> "A different signing key is already installed. Use an APK signed by the same developer; uninstalling the old app removes its data."
    "INSTALL_FAILED_NO_MATCHING_ABIS" in output -> "This APK doesn't include the TV's CPU architecture. Choose a compatible or universal APK."
    "INSTALL_FAILED_OLDER_SDK" in output -> "This APK needs a newer Android version than the TV has."
    "INSTALL_FAILED_INSUFFICIENT_STORAGE" in output -> "The TV doesn't have enough free storage."
    "INSTALL_FAILED_USER_RESTRICTED" in output -> "The TV blocked installation. Check its installation restrictions and any on-screen approval prompt."
    "INSTALL_FAILED_VERSION_DOWNGRADE" in output -> "A newer version is installed on the TV. Choose the same or a newer version."
    "INSTALL_PARSE_FAILED" in output || "INSTALL_FAILED_INVALID_APK" in output -> "The APK is invalid, incomplete or unsupported. Download a fresh copy."
    else -> "See Diagnostic logs for the TV's complete installation response."
}

/** Exact byte-count pipe closes stdin for package installation without closing the ADB stream. */
internal fun installationCommand(size: Long, marker: String): String =
    "head -c $size | cmd package install -r -S $size; __plus_rc=\$?; echo; echo $marker:\$__plus_rc; echo ${marker}_DONE"
