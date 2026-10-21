package io.konnecttv.plus

import android.app.Application
import io.konnecttv.plus.diagnostics.DiagnosticLog

class PlusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.init(this)
        RemotePreferences.init(this)
    }
}
