package io.konnecttv.plus

import io.konnecttv.plus.adb.Key
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test

class RemoteFixesTest {
    @Test fun bubbleOffAndTvMasterAlwaysBlock() {
        assertTrue(BubbleConfig(enabled=false).blockedReason("com.netflix.ninja","Netflix")!!.contains("is off"))
        assertTrue(BubbleConfig().blockedReason(TV_HOME_PACKAGE,overlaysAllowed=false)!!.contains("TV owner"))
    }
    @Test fun anyAppAllowsKnownOrUnknownForeground() {
        assertNull(BubbleConfig().blockedReason("com.netflix.ninja","Netflix"))
        assertNull(BubbleConfig().blockedReason(null))
    }
    @Test fun homeOnlyAllowsOnlyGlowlauncher() {
        val config=BubbleConfig(scope=BubbleScope.HOME)
        assertNull(config.blockedReason(TV_HOME_PACKAGE))
        assertEquals("Not shown: TV is in Netflix (blocked in TV message settings).",config.blockedReason("com.netflix.ninja","Netflix"))
        assertNotNull(config.blockedReason(null))
    }
    @Test fun selectedAppsCanIncludeTvHomeButNeverUnknownForeground() {
        val config=BubbleConfig(scope=BubbleScope.SELECTED,packages=setOf("com.google.android.youtube.tv",TV_HOME_PACKAGE))
        assertNull(config.blockedReason("com.google.android.youtube.tv"))
        assertNull(config.blockedReason(TV_HOME_PACKAGE))
        assertNotNull(config.blockedReason("in.startv.hotstar"))
        assertNotNull(config.blockedReason(null))
        assertNotNull(BubbleConfig(scope=BubbleScope.SELECTED).blockedReason(TV_HOME_PACKAGE))
    }
    @Test fun volumeStripEmitsKeysForEveryWhole24Dp() {
        val strip=VolumeKeyStrip()
        assertTrue(strip.travel(-23f).isEmpty())
        assertEquals(listOf(Key.VOL_UP),strip.travel(-1f))
        assertEquals(listOf(Key.VOL_UP,Key.VOL_UP),strip.travel(-48f))
        assertEquals(listOf(Key.VOL_DOWN,Key.VOL_DOWN,Key.VOL_DOWN),strip.travel(72f))
        assertTrue(strip.travel(Float.NaN).isEmpty())
    }
    @Test fun volumeStripRetainsRemainderAcrossDirectionChanges() {
        val strip=VolumeKeyStrip()
        assertTrue(strip.travel(20f).isEmpty())
        assertTrue(strip.travel(-20f).isEmpty())
        assertTrue(strip.travel(-12f).isEmpty())
        assertEquals(listOf(Key.VOL_UP),strip.travel(-12f))
    }
    @Test fun pendingPointerKeepsAndCombinesDeltaUntilOperationLockIsAcquired() {
        val lock=Mutex(locked=true);val pending=PendingPointer()
        pending.add("tv",30,5)
        assertNull(pending.tryTake(lock.tryLock()))
        pending.add("tv",20,-2)
        assertNull(pending.tryTake(lock.tryLock()))
        lock.unlock()
        assertTrue(lock.tryLock())
        try { assertEquals(PointerDelta("tv",50,3),pending.tryTake(true));assertNull(pending.tryTake(true)) }
        finally { lock.unlock() }
    }
    @Test fun pointerSwitchingTvDropsOldTvDelta() {
        val pending=PendingPointer();pending.add("old",20,5);pending.add("new",10,3)
        assertEquals(PointerDelta("new",10,3),pending.tryTake(true))
        pending.add("new",10,3);pending.clear();assertNull(pending.tryTake(true))
    }
}
