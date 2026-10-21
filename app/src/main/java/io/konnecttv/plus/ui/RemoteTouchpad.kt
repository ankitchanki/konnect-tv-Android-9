package io.konnecttv.plus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.konnecttv.plus.VolumeKeyStrip
import io.konnecttv.plus.RemotePreferences
import io.konnecttv.plus.TvViewModel
import io.konnecttv.plus.adb.Key
import kotlin.math.abs
import kotlin.math.roundToInt

/** Relative directional swipes by default; optional TV cursor uses explicit accessibility access. */
@Composable
fun RemoteTouchpad(vm: TvViewModel, compact: Boolean) {
    val ready by vm.remoteInputAvailable.collectAsState()
    val volume by vm.remoteMedia.collectAsState()
    val brightness by vm.screenBrightness.collectAsState()
    var pointer by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Swipe to navigate · centre OK") }
    BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=if(compact) Alignment.BottomCenter else Alignment.Center) {
        val side = if(compact) 220.dp else minOf(maxHeight,maxWidth)
        Column(Modifier.size(side).testTag("remote-dpad").background(C.Card,RoundedCornerShape(28.dp)).border(1.dp,C.Border,RoundedCornerShape(28.dp))) {
            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Dim",style=Small)
                Text(if(pointer && ready) "TV cursor" else "Touchpad",style=Small,modifier=Modifier.clickable { pointer = !pointer && ready; if(!ready) vm.say("Enable TV touchpad access in Settings for a visible cursor.") })
                Text("Volume",style=Small)
            }
            Box(Modifier.fillMaxWidth().weight(1f).pointerInput(pointer,ready,volume?.volume,brightness) {
                var zone=0; var dx=0f;var dy=0f;var last=0L
                var keyStrip=VolumeKeyStrip()
                detectDragGestures(onDragStart={ start -> zone=if(start.x < size.width*.18f) 1 else if(start.x > size.width*.82f) 2 else 0; dx=0f;dy=0f;keyStrip=VolumeKeyStrip() },
                    onDragEnd={
                        if(zone==0 && pointer && ready && (dx!=0f || dy!=0f)) vm.movePointer((dx*2).roundToInt(),(dy*2).roundToInt())
                        if(zone==1) { if(ready) vm.setScreenBrightness((brightness-dy/size.height*100).roundToInt()) else vm.say("Enable TV touchpad access for screen dimming.") }
                        if(zone==2) { val v=volume?.volume; if(v?.supported==true) vm.setTvVolume((v.index-dy/size.height*(v.max-v.min)).roundToInt()) }
                        hint="Swipe to navigate · centre OK"
                    },onDragCancel={hint="Swipe to navigate · centre OK"}) { change,amount ->
                    change.consume(); dx+=amount.x;dy+=amount.y
                    when(zone) {
                        1 -> hint="Screen brightness ${(brightness-dy/size.height*100).roundToInt().coerceIn(20,100)}%"
                        2 -> {
                            if(volume?.volume?.supported==true) hint="Volume ${((volume?.volume?.percent ?: 0)-dy/size.height*100).roundToInt().coerceIn(0,100)}%"
                            else { hint="TV volume keys";keyStrip.travel(amount.y / density).forEach { vm.key(it);RemotePreferences.feedback() } }
                        }
                        else -> {
                            val now=android.os.SystemClock.elapsedRealtime()
                            if(pointer && ready && now-last>=70) { vm.movePointer((dx*2).roundToInt(),(dy*2).roundToInt());dx=0f;dy=0f;last=now }
                            else if((!pointer || !ready) && maxOf(abs(dx),abs(dy)) >= 34.dp.toPx()) {
                                vm.key(if(abs(dx)>abs(dy)) { if(dx>0) Key.RIGHT else Key.LEFT } else { if(dy>0) Key.DOWN else Key.UP })
                                RemotePreferences.feedback();dx=0f;dy=0f
                            }
                        }
                    }
                }
            },contentAlignment=Alignment.Center) {
                Box(Modifier.size(76.dp).background(C.Raised,RoundedCornerShape(24.dp)).clickable {
                    if(pointer && ready) vm.pointerClick() else vm.key(Key.OK)
                },contentAlignment=Alignment.Center) { Text("OK",style=Body.copy(color=C.Accent)) }
            }
            Text(hint,style=Small,modifier=Modifier.padding(10.dp))
        }
    }
}
