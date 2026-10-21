package io.konnecttv.plus

/** Apps the remote opens with one tap. TV makers ship each under several packages; the first one installed opens. */
enum class QuickApp(val label: String, val packages: List<String>) {
    YouTube("YouTube", listOf("com.google.android.youtube.tv", "com.google.android.youtube.googletv", "com.amazon.firetv.youtube", "com.google.android.youtube")),
    Cloudstream("Cloudstream", listOf("com.lagradost.cloudstream3", "com.lagradost.cloudstream3.prerelease", "com.lagradost.cloudstream3.debug")),
    Hotstar("JioHotstar", listOf("in.startv.hotstar", "in.startv.hotstar.tv", "in.startv.hotstar.androidtv", "in.startv.hotstar.dplus.tv", "in.startv.hotstar.dplus")),
}

/** The first of this app's packages that `pm list packages` output lists, or null when the TV has none of them. */
fun QuickApp.installedIn(pmList: String): String? {
    val installed = pmList.lineSequence().map { it.trim().removePrefix("package:") }.toSet()
    return packages.firstOrNull { it in installed }
}
