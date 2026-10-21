package io.konnecttv.plus.diagnostics

/** Redact before persistence, including errors returned by third-party ADB and Android services. */
object LogSanitizer {
    fun clean(value: String): String = value
        .replace(Regex("(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----"), "[private key omitted]")
        .replace(Regex("(?i)(password|passwd|pairing[_ -]?code|pair[_ -]?code|secret|token|access[_ -]?token|authorization)([\\s=:]+)[^\\s,;]+"), "$1$2[redacted]")
        .replace(Regex("(?i)(serial(?:no|number)?|android_id|device_id)([\\s=:]+)[^\\s,;]+"), "$1$2[redacted]")
        .replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[address]")
        .replace(Regex("(?i)(?<![a-z0-9:])(?:(?:[a-f0-9]{1,4}:){4,}[a-f0-9:]+|[a-f0-9:]*::[a-f0-9:]+)(?:%[a-z0-9]+)?"), "[address]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email]")
        .replace(Regex("(?i)(?:content|file)://[^\\s]+"), "[file URI]")
        .replace(Regex("(?i)([?&](?:token|key|sig|secret|code)=)[^&\\s]+"), "$1[redacted]")

    fun command(value: String): String = when {
        value.contains("input text") -> "Remote text input (content omitted)"
        value.contains("MediaBridge") -> "TV media controls (metadata omitted)"
        value.contains("IconDump") -> "App icon helper (image payload omitted)"
        else -> clean(value)
    }

    fun output(command: String, value: String): String = when {
        command.contains("MediaBridge") || command.contains("input text") || command.contains("IconDump") || command.contains("ro.serialno") || command.contains("android_id") -> "[sensitive or binary output omitted]"
        else -> clean(value).take(12000).let { if (value.length > 12000) "$it\n[output truncated]" else it }
    }
}
