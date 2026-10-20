package io.konnecttv

/** Reject malformed ports before passing them to the ADB client. */
internal fun adbPort(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..65535 }

/** RoleManager arrived in Android 10. Android 9 uses PackageManager's home command. */
internal fun homeSelectionCommand(sdk: Int, packageName: String, activity: String): String {
    val setHome = "cmd package set-home-activity $activity"
    return if (sdk >= 29) {
        "cmd role add-role-holder --user 0 android.app.role.HOME $packageName; $setHome"
    } else setHome
}

/** Android versions/vendors print either an abbreviated or a full activity class name. */
internal fun matchesHomeComponent(component: String, packageName: String, className: String): Boolean {
    val parts = component.trim().split('/', limit = 2)
    if (parts.size != 2 || parts[0] != packageName) return false
    fun expanded(name: String) = if (name.startsWith('.')) packageName + name else name
    return expanded(parts[1]) == expanded(className)
}
