package io.konnecttv.plus.adb

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import io.konnecttv.plus.diagnostics.DiagnosticLog
import io.konnecttv.plus.diagnostics.LogSanitizer
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.UUID

/**
 * One ADB connection to one TV. The RSA key is made once and kept in app storage, so the TV's
 * "Always allow" and wireless-debugging pairing survive app restarts.
 *
 * Every call blocks: run it on Dispatchers.IO.
 */
class Adb(context: Context) : AbsAdbConnectionManager() {
    private val keyFile = File(context.filesDir, "adb.key")
    private val certFile = File(context.filesDir, "adb.crt")
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        // adbd on Android 11+ speaks the newest protocol; older TVs negotiate down.
        setApi(30)
        setTimeout(30, TimeUnit.SECONDS)
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val name = X500Name("CN=Konnect TV Plus")
            val now = Date()
            val holder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now.time), now, Date(now.time + 20L * 365 * 86_400_000), name, pair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
            privateKey = pair.private
            certificate = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(privateKey.encoded)
            certFile.writeBytes(certificate.encoded)
        }
    }

    override fun getPrivateKey() = privateKey
    override fun getCertificate() = certificate
    override fun getDeviceName() = "Konnect TV Plus"

    /** Runs a shell command and returns its output, stdout and stderr merged. */
    fun sh(command: String): String = shellResult(command).output

    fun checked(command: String, seconds: Long = 90): String {
        val result = shellResult(command, seconds)
        check(result.exitCode == 0) { "TV command failed (exit ${result.exitCode}): ${result.output}" }
        return result.output
    }

    private fun shellResult(command: String, seconds: Long = 90): ShellResult {
        val marker = "__PLUS_${UUID.randomUUID().toString().replace("-", "")}__"
        val started = android.os.SystemClock.elapsedRealtime()
        val label = LogSanitizer.command(command)
        if (DiagnosticLog.verbose.value) DiagnosticLog.record("DEBUG", "ADB", "RUN $label")
        try {
            val result = timedStream("shell:$command; __plus_rc=\$?; echo; echo $marker:\$__plus_rc; echo ${marker}_DONE", seconds) {
                parseShellResult(it.readUntil { raw -> "${marker}_DONE" in raw }, marker)
            }
            if (result.exitCode != 0 || DiagnosticLog.verbose.value) DiagnosticLog.record(
                if (result.exitCode == 0) "DEBUG" else "WARN", "ADB",
                "$label\nExit ${result.exitCode}; ${android.os.SystemClock.elapsedRealtime() - started} ms\n${LogSanitizer.output(command, result.output)}",
            )
            return result
        } catch (e: Exception) {
            DiagnosticLog.record("ERROR", "ADB", "$label failed after ${android.os.SystemClock.elapsedRealtime() - started} ms", e)
            throw e
        }
    }

    private fun <T> timedStream(service: String, seconds: Long, action: (AdbStream) -> T): T = openStream(service).use { stream ->
        withStreamDeadline(TimeUnit.SECONDS.toMillis(seconds), { stream.close() }) { action(stream) }
    }

    private var keyShell: AdbStream? = null

    /** Key presses go down one long-lived shell, so a tap costs a write, not a new stream. */
    @Synchronized
    fun keyevent(code: Int) = typeRaw("input keyevent $code")

    @Synchronized
    fun type(text: String) = typeRaw("input text ${shellText(text)}")

    private fun typeRaw(line: String) {
        if (DiagnosticLog.verbose.value) DiagnosticLog.record("DEBUG", "Remote", LogSanitizer.command(line))
        val shell = keyShell?.takeUnless { it.isClosed } ?: openStream("shell:").also { keyShell = it }
        try {
            shell.openOutputStream().apply { write("$line\n".toByteArray()); flush() }
        } catch (e: Exception) {
            keyShell = null
            throw e
        }
    }

    /** Public APK bytes only. A byte bound/deadline prevents a failed cat from hanging inspection. */
    fun copyRemoteApk(path: String, destination: File) {
        require(path.startsWith("/") && path.endsWith(".apk") && !path.contains('\n') && !path.contains('\u0000'))
        val quoted="'"+path.replace("'","'\\''")+"'"
        val size=checked("stat -c %s $quoted",seconds=15).trim().toLongOrNull() ?: error("Could not read installed APK size.")
        require(size in 1..(32L*1024*1024)) { "Installed APK exceeds the inspection size limit." }
        timedStream("exec:cat $quoted",30) { stream ->
            val input=stream.openInputStream()
            destination.outputStream().use { out ->
                val buffer=ByteArray(8192);var remaining=size
                while(remaining>0) { val count=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());check(count>0) { "Installed APK read was incomplete." };out.write(buffer,0,count);remaining-=count }
            }
        }
        check(destination.length()==size)
    }

    /** Streams an APK into `pm install`, no temp file on the TV. Returns pm's last line. */
    fun install(apk: InputStream, size: Long): String {
        require(size > 0) { "The APK is empty." }
        val marker = "__INSTALL_${UUID.randomUUID().toString().replace("-", "")}__"
        DiagnosticLog.record("INFO", "Install", "APK install started ($size bytes). Awaiting TV package-manager response.")
        try {
            val result = timedStream("exec:${installationCommand(size, marker)}", 180) { stream ->
                val sent = runCatching { stream.openOutputStream().let { out -> apk.copyTo(out).also { out.flush() } } }
                val response = parseShellResult(stream.readUntil { "${marker}_DONE" in it }, marker)
                check(sent.getOrDefault(-1L) == size) { "APK transfer was incomplete. TV response: ${response.output}" }
                response
            }
            val success = result.exitCode == 0 && result.output.lines().any { it.trim() == "Success" || it.trim().startsWith("Success:") }
            DiagnosticLog.record(if (success) "INFO" else "ERROR", "Install", "Package-manager exit ${result.exitCode}\n${result.output}")
            check(success) { "Install failed: ${result.output}\n${installationHelp(result.output)}" }
            return "Success"
        } catch (e: Exception) {
            DiagnosticLog.record("ERROR", "Install", "APK installation did not complete", e)
            throw e
        }
    }

    /** Bounded, checked raw uploads: an interrupted stream must never look like success. */
    fun push(file: File, remote: String) = upload(file, "head -c ${file.length()} > '${remote.replace("'", "'\\''")}'", "Helper file")

    fun pushWallpaper(file: File) = upload(file,
        "head -c ${file.length()} | content write --uri content://io.konnecttv.glowlauncher.wallpaper/wallpaper", "Wallpaper")

    private fun upload(file: File, command: String, label: String) {
        check(file.length() > 0) { "The file to send is empty." }
        val marker = "__UPLOAD_${UUID.randomUUID().toString().replace("-", "")}__"
        DiagnosticLog.record("INFO", "Transfer", "$label upload started (${file.length()} bytes; payload omitted)")
        try {
            val response = timedStream("exec:$command; __plus_rc=\$?; echo; echo $marker:\$__plus_rc; echo ${marker}_DONE", 180) { stream ->
                val sent = runCatching { stream.openOutputStream().let { out -> file.inputStream().use { it.copyTo(out) }.also { out.flush() } } }
                val result = parseShellResult(stream.readUntil { "${marker}_DONE" in it }, marker)
                check(sent.getOrDefault(-1L) == file.length()) { "$label transfer was incomplete. TV response: ${result.output}" }
                result
            }
            check(response.exitCode == 0) { "$label upload failed (exit ${response.exitCode}): ${response.output}" }
            DiagnosticLog.record("INFO", "Transfer", "$label upload completed (${file.length()} bytes)")
        } catch (e: Exception) { DiagnosticLog.record("ERROR", "Transfer", "$label upload failed", e); throw e }
    }

    override fun close() {
        keyShell?.runCatching { close() }
        keyShell = null
        super.close()
    }
}


/**
 * Reads until [done] says the output is complete. libadb's reader can block forever when the
 * TV closes a stream right after its last bytes, so callers never rely on end-of-stream.
 */
private fun AdbStream.readUntil(done: (String) -> Boolean): String {
    val input = openInputStream()
    val out = StringBuilder()
    val buf = ByteArray(8192)
    while (!done(out.toString())) {
        val n = try { input.read(buf) } catch (e: java.io.IOException) { -1 }
        if (n < 0) break
        out.append(String(buf, 0, n))
    }
    return out.toString()
}

/** `input text` needs spaces as %s and shell metacharacters quoted. */
internal fun shellText(text: String): String =
    "'" + text.replace(" ", "%s").replace("'", "'\\''") + "'"

/** Android key codes the remote sends. */
object Key {
    const val HOME = 3
    const val BACK = 4
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val OK = 23
    const val VOL_UP = 24
    const val VOL_DOWN = 25
    const val POWER = 26
    const val MENU = 82
    const val PLAY_PAUSE = 85
    const val MUTE = 164
    const val SETTINGS = 176
    const val DEL = 67
}
