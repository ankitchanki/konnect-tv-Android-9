package io.konnecttv.plus.companion

import android.content.Context
import com.android.apksig.ApkVerifier
import io.konnecttv.plus.diagnostics.DiagnosticLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

interface CompanionTransport {
    fun shell(command: String): String
    fun copyApk(remotePath: String, destination: File)
    fun install(apk: File)
}
data class CompanionConflict(val tvName: String, val reason: String)
class ReplacementCancelled: IllegalStateException("Replacement cancelled. The previous Glowlauncher was kept.")
class ReplacementConsent {
    val pending = MutableStateFlow<CompanionConflict?>(null)
    private var answer: CompletableDeferred<Boolean>? = null
    suspend fun request(conflict: CompanionConflict): Boolean {
        val result=CompletableDeferred<Boolean>();check(answer==null)
        answer=result;pending.value=conflict
        return try { result.await() } finally { if(answer===result) { answer=null;pending.value=null } }
    }
    fun respond(replace: Boolean) { answer?.complete(replace) }
}

/** One resource gate is shared with IconDump and every APK install. No uninstall without consent. */
class CompanionInstaller(
    private val context: Context,
    private val transport: CompanionTransport,
    private val resourceGate: Mutex,
    private val confirm: suspend (CompanionConflict)->Boolean,
    private val beforeRemoval: suspend ()->Unit,
    private val removed: suspend ()->Unit = {},
    private val replaced: suspend ()->Unit = {},
) {
    fun identity(apk: File): ApkIdentity? {
        // Read/verify APK signing blocks directly: Android archive parsing can return no signingInfo.
        // Files come only from our bundled asset or `pm path` for the fixed companion package.
        val result=ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(28).build().verify()
        if(!result.isVerified) {
            DiagnosticLog.record("WARN","Companion","APK signature verification failed: ${result.errors}")
            return null
        }
        fun digest(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val current=result.signerCertificates.map { digest(it.encoded) }.toSet()
        val lineage=result.signingCertificateLineage?.certificatesInLineage.orEmpty().map { digest(it.encoded) }.toSet()+current
        return ApkIdentity(current,lineage)
    }

    suspend fun install(tvName: String) = resourceGate.withLock {
        val bundled=File.createTempFile("glow-bundled-",".apk",context.cacheDir)
        val installed=File.createTempFile("glow-installed-",".apk",context.cacheDir)
        try {
            context.assets.open("glowlauncher.apk").use { input -> bundled.outputStream().use { input.copyTo(it) } }
            val bundledIdentity=checkNotNull(identity(bundled)) { "Bundled TV companion certificate could not be verified." }
            val pm=transport.shell("pm path $COMPANION_PACKAGE")
            val installedIdentity=installedApkPath(pm)?.let { path -> runCatching { transport.copyApk(path,installed);identity(installed) }.onFailure {
                DiagnosticLog.record("WARN","Companion","Could not inspect installed APK certificate; a normal non-destructive update will be tried",it)
            }.getOrNull() }
            val match=compareCompanionSignatures(pm,installedIdentity,bundledIdentity)
            DiagnosticLog.record("INFO","Companion","Signing preflight: $match; installed=${installedIdentity?.current}; bundled=${bundledIdentity.current}")
            var conflict=match==SignatureMatch.MISMATCH
            if(!conflict) {
                try { transport.install(bundled);return@withLock }
                catch(e: Exception) { if(!isSignatureInstallFailure(e.message.orEmpty())) throw e;conflict=true }
            }
            if(conflict) {
                DiagnosticLog.record("WARN","Companion","Different signing build detected; waiting for explicit replacement confirmation")
                if(!confirm(CompanionConflict(tvName,"An older Glowlauncher from a different build is on this TV."))) {
                    DiagnosticLog.record("INFO","Companion","Replacement cancelled; no uninstall performed")
                    throw ReplacementCancelled()
                }
                DiagnosticLog.record("INFO","Companion","Replacement confirmed; restoring stock Home before uninstall")
                beforeRemoval()
                check(transport.shell("pm uninstall $COMPANION_PACKAGE").trim()=="Success") { "The TV refused to uninstall the old Glowlauncher. Replacement stopped." }
                removed()
                try { transport.install(bundled) } catch(e: Exception) {
                    throw IllegalStateException("Old Glowlauncher was removed after confirmation, but the new installation failed. The stock TV Home was kept. Retry the original action. ${e.message}",e)
                }
                replaced()
                DiagnosticLog.record("INFO","Companion","Confirmed replacement installed; continuing original action")
            }
        } finally { bundled.delete();installed.delete() }
    }
}
