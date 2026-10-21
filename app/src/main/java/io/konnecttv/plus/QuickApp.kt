package io.konnecttv.plus

/** Apps the remote opens with one tap. TV makers ship each under several packages; the first one installed opens. */
enum class QuickApp(val label: String, val packages: List<String>) {
    YouTube("YouTube", listOf("com.google.android.youtube.tv", "com.google.android.youtube.googletv", "com.amazon.firetv.youtube", "com.google.android.youtube")),
    Hotstar("Hotstar", listOf("in.startv.hotstar", "in.startv.hotstar.dplus.tv", "in.startv.hotstar.dplus")),
}

/** The first of this app's packages that `pm list packages` output lists, or null when the TV has none of them. */
fun QuickApp.installedIn(pmList: String): String? {
    val installed = pmList.lineSequence().map { it.trim().removePrefix("package:") }.toSet()
    return packages.firstOrNull { it in installed }
}
