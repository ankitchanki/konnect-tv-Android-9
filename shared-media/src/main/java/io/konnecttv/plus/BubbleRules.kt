package io.konnecttv.plus

const val TV_HOME_PACKAGE = "io.konnecttv.glowlauncher"
enum class BubbleScope { ANY, HOME, SELECTED }
data class BubbleConfig(val enabled: Boolean = true, val scope: BubbleScope = BubbleScope.ANY, val packages: Set<String> = emptySet()) {
    fun blockedReason(foreground: String?, appLabel: String? = null, overlaysAllowed: Boolean = true): String? {
        if (!overlaysAllowed) return "Not shown: TV owner blocked overlays in Glowlauncher privacy settings."
        if (!enabled) return "Not shown: Show messages on TV is off."
        if (scope == BubbleScope.ANY) return null
        val allowed = when(scope) { BubbleScope.ANY -> true; BubbleScope.HOME -> foreground == TV_HOME_PACKAGE; BubbleScope.SELECTED -> foreground != null && foreground in packages }
        if (allowed) return null
        val label = appLabel?.takeIf { it.isNotBlank() } ?: foreground ?: "an unknown app"
        return "Not shown: TV is in $label (blocked in TV message settings)."
    }
}
