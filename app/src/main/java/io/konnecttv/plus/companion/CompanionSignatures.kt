package io.konnecttv.plus.companion

const val COMPANION_PACKAGE = "io.konnecttv.glowlauncher"
data class ApkIdentity(val current: Set<String>, val lineage: Set<String> = current)
enum class SignatureMatch { ABSENT, MATCH, MISMATCH, UNKNOWN }
fun installedApkPath(output: String): String? = output.lineSequence().map { it.trim() }
    .filter { it.startsWith("package:/") }.map { it.removePrefix("package:") }
    .filter { it.endsWith(".apk") && it.none { c -> c=='\n' || c=='\r' || c=='\u0000' } }
    .toList().let { paths -> paths.firstOrNull { it.endsWith("/base.apk") } ?: paths.firstOrNull() }
fun compareCompanionSignatures(pmOutput: String, installed: ApkIdentity?, bundled: ApkIdentity): SignatureMatch {
    if(pmOutput.isBlank()) return SignatureMatch.ABSENT
    if(installedApkPath(pmOutput)==null || installed==null || installed.current.isEmpty() || bundled.current.isEmpty()) return SignatureMatch.UNKNOWN
    return if(installed.current==bundled.current || (installed.current.size==1 && bundled.current.size==1 && installed.current.single() in bundled.lineage)) SignatureMatch.MATCH else SignatureMatch.MISMATCH
}
fun isSignatureInstallFailure(message: String) = "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in message
fun homeComponents(output: String): List<String> = output.lineSequence().map { it.trim() }
    .filter { it.matches(Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")) }.toList()
fun stockHomeComponent(current: String, candidates: String, systemPackages: String): String? {
    val system=systemPackages.lineSequence().map { it.trim().removePrefix("package:") }.toSet()
    val homes=homeComponents(candidates).filter { it.substringBefore('/') in system && it.substringBefore('/') !in setOf(COMPANION_PACKAGE,"com.ansub.tvhome","android") }
    return homes.firstOrNull { it in homeComponents(current) } ?: homes.firstOrNull()
}
