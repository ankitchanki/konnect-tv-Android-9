package io.konnecttv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.konnecttv.ui.AppsScreen
import io.konnecttv.ui.Body
import io.konnecttv.ui.BoostScreen
import io.konnecttv.ui.C
import io.konnecttv.ui.ConnectScreen
import io.konnecttv.ui.DetoxScreen
import io.konnecttv.ui.HapticIndication
import io.konnecttv.ui.LocalHaze
import io.konnecttv.ui.LocalSheetHost
import io.konnecttv.ui.SheetHost
import io.konnecttv.ui.SheetOverlay
import io.konnecttv.ui.RemoteScreen
import io.konnecttv.ui.Tab
import io.konnecttv.ui.TabIcon
import io.konnecttv.ui.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: TvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        C.light = getSharedPreferences("konnecttv", MODE_PRIVATE).getBoolean("light", false)
        setContent {
            // Status and navigation bar icons follow the theme.
            LaunchedEffect(C.light) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (C.light) SystemBarStyle.light(t, t) else SystemBarStyle.dark(t)
                enableEdgeToEdge(style, style)
            }
            val haze = remember { HazeState() }
            val sheets = remember { SheetHost() }
            val press = LocalIndication.current as IndicationNodeFactory
            val indication = remember(press) { HapticIndication(press) }
            CompositionLocalProvider(LocalHaze provides haze, LocalSheetHost provides sheets, LocalIndication provides indication) { Root(vm) }
        }
    }
}

@Composable
private fun Root(vm: TvViewModel) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    // Tabs sit side by side: swipe or tap the bar.
    val pager = rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val message by vm.message.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Box(Modifier.fillMaxSize().hazeSource(LocalHaze.current).statusBarsPadding()) {
            when (val c = conn) {
                is Conn.On -> HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    when (Tab.entries[page]) {
                        Tab.Remote -> RemoteScreen(vm, c.tv)
                        Tab.Apps -> AppsScreen(vm)
                        Tab.Detox -> DetoxScreen(vm)
                        Tab.Boost -> BoostScreen(vm, c.tv)
                    }
                }
                else -> ConnectScreen(vm, (c as? Conn.Busy)?.text)
            }
        }
        if (conn is Conn.On) TabBar(Tab.entries[pager.currentPage], Modifier.align(Alignment.BottomCenter)) {
            scope.launch { pager.animateScrollToPage(it.ordinal) }
        }
        SheetOverlay(LocalSheetHost.current)

        // Short status line, floating above everything.
        LaunchedEffect(message) { if (message != null) { delay(3_500); vm.message.value = null } }
        AnimatedVisibility(
            message != null,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Box(
                Modifier.glass(RoundedCornerShape(18.dp)).clickable { vm.message.value = null }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text(message.orEmpty(), style = Body) }
        }
    }
}

@Composable
private fun TabBar(current: Tab, modifier: Modifier, onPick: (Tab) -> Unit) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(64.dp)
            .glass(RoundedCornerShape(32.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val haptics = LocalHapticFeedback.current
        Tab.entries.forEach { t ->
            val color = if (t == current) C.Text else C.Muted
            Column(
                Modifier
                    .weight(1f)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onPick(t)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TabIcon(t, if (t == current) C.Accent else color)
                Spacer(Modifier.height(4.dp))
                Text(t.title, style = io.konnecttv.ui.Small.copy(color = color))
            }
        }
    }
}

/** Space the floating tab bar covers, so the last list item can scroll clear of it. */
val TabBarClearance = 110.dp
