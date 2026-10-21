package io.konnecttv.plus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.plus.companion.ReplacementConsent

/** Independent host keeps the original setup/progress sheet intact behind this confirmation. */
@Composable
fun CompanionReplacementSheet(consent: ReplacementConsent) {
    val conflict by consent.pending.collectAsStateWithLifecycle()
    val host = remember { SheetHost() }
    CompositionLocalProvider(LocalSheetHost provides host) {
        Box {
            GlassSheet(conflict != null, onDismiss = { consent.respond(false) }) {
                Text("Replace older Glowlauncher?", style = H1)
                Text(conflict?.tvName.orEmpty(), style = Small)
                Text("An older Glowlauncher from a different build is on this TV. Replace it? Its wallpaper and home-app list will be removed; your TV’s own home screen is kept.", style = Body)
                Text("Removed Glowlauncher data cannot be restored. Cancel keeps the existing installation.", style = Small)
                PrimaryButton("Replace Glowlauncher") { consent.respond(true) }
                SecondaryButton("Cancel") { consent.respond(false) }
            }
            SheetOverlay(host)
        }
    }
}
