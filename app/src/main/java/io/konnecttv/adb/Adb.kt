package io.konnecttv.adb

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
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
            val name = X500Name("CN=KonnectTV")
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
    override fun getDeviceName() = "KonnectTV"

    /** Runs a shell command and returns its output, stdout and stderr merged. */
    fun sh(command: String): String =
        openStream("shell:$command; echo $END").use { it.readUntil { out -> out.contains(END) } }
            .substringBefore(END).trim()

    private var keyShell: AdbStream? = null

    /** Key presses go down one long-lived shell, so a tap costs a write, not a new stream. */
    @Synchronized
    fun keyevent(code: Int) = typeRaw("input keyevent $code")

    @Synchronized
    fun type(text: String) = typeRaw("input text ${shellText(text)}")

    private fun typeRaw(line: String) {
        val shell = keyShell?.takeUnless { it.isClosed } ?: openStream("shell:").also { keyShell = it }
        try {
            shell.openOutputStream().apply { write("$line\n".toByteArray()); flush() }
        } catch (e: Exception) {
            keyShell = null
            throw e
        }
    }

    /** Streams an APK into `pm install`, no temp file on the TV. Returns pm's last line. */
    fun install(apk: InputStream, size: Long): String =
        openStream("exec:cmd package install -r -S $size").use { stream ->
            stream.openOutputStream().let { out -> apk.copyTo(out); out.flush() }
            stream.readUntil { "Success" in it || "Failure" in it }.trim().lines().last()
        }

    /** Copies a file onto the TV. `exec:` is a raw pipe, so the bytes arrive untouched. */
    fun push(file: File, remote: String) {
        openStream("exec:head -c ${file.length()} > $remote; echo $END").use { stream ->
            stream.openOutputStream().let { out -> file.inputStream().use { it.copyTo(out) }; out.flush() }
            stream.readUntil { END in it }
        }
    }

    override fun close() {
        keyShell?.runCatching { close() }
        keyShell = null
        super.close()
    }
}

private const val END = "__KONNECTTV_END__"

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
