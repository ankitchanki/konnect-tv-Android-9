package io.konnecttv.plus

import io.konnecttv.plus.adb.DiscoveryInventory
import io.konnecttv.plus.adb.DiscoveryKind
import io.konnecttv.plus.adb.withStreamDeadline
import io.konnecttv.plus.diagnostics.LogSanitizer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RemoteControlsTest {
    @Test fun quickLaunchPicksTheFirstInstalledPackage() {
        val pm = "package:com.netflix.ninja\npackage:com.google.android.youtube\npackage:com.google.android.youtube.tv\npackage:in.startv.hotstar.dplus.tv\n"
        assertEquals("com.google.android.youtube.tv", QuickApp.YouTube.installedIn(pm))
        assertEquals("in.startv.hotstar.dplus.tv", QuickApp.Hotstar.installedIn(pm))
        assertNull(QuickApp.Hotstar.installedIn("package:in.startv.hotstarx\npackage:com.netflix.ninja"))
    }

    @Test fun cloudstreamAndJioHotstarKeepTheirOwnPackagePriority() {
        assertEquals("JioHotstar",QuickApp.Hotstar.label)
        assertEquals("com.lagradost.cloudstream3",QuickApp.Cloudstream.installedIn("package:com.lagradost.cloudstream3.prerelease\npackage:com.lagradost.cloudstream3"))
        assertNull(QuickApp.Cloudstream.installedIn("package:com.lagradost.cloudstream3.fake"))
    }
    @Test fun tvBubbleContentsNeverEnterDetailedLogs() {
        val command="content call --uri content://io.konnecttv.glowlauncher.remote --method message --arg PRIVATE_PAYLOAD"
        assertFalse(LogSanitizer.command(command).contains("PRIVATE_PAYLOAD"))
        assertFalse(LogSanitizer.output(command,"PRIVATE_PAYLOAD").contains("PRIVATE_PAYLOAD"))
    }
    @Test fun lostPairingServiceClearsOnlyThatPort() {
        val index = DiscoveryInventory()
        index.update("remote", index.begin("remote"), "192.168.1.1", "TV", DiscoveryKind.REMOTE, 6466)
        index.update("pair", index.begin("pair"), "192.168.1.1", "adb-secret", DiscoveryKind.PAIR, 3333)
        index.update("connect", index.begin("connect"), "192.168.1.1", "adb-secret", DiscoveryKind.CONNECT, 4444)
        index.lost("pair")
        assertNull(index.list().single().pairPort)
        assertEquals(4444, index.list().single().tlsPort)
        assertEquals("TV", index.list().single().name)
    }
    @Test fun lateResolutionCannotResurrectAnOfflineTv() {
        val index = DiscoveryInventory();val generation=index.begin("tv")
        index.lost("tv")
        index.update("tv", generation, "192.168.1.1", "TV", DiscoveryKind.REMOTE, 6466)
        assertTrue(index.list().isEmpty())
    }
    @Test fun updatedAddressReplacesOldAndOldCallbackCannotRemoveIt() {
        val index = DiscoveryInventory();val old=index.begin("tv")
        index.update("tv", old, "192.168.1.1", "TV", DiscoveryKind.REMOTE, 6466)
        val current=index.begin("tv")
        index.update("tv", current, "192.168.1.2", "TV", DiscoveryKind.REMOTE, 6466)
        index.lost("tv",old)
        assertEquals("192.168.1.2",index.list().single().host)
        index.lost("tv",current)
        assertTrue(index.list().isEmpty())
    }
    @Test fun streamTimeoutClosesBlockedIoAndNeverReturnsFalseSuccess() {
        val closed=CountDownLatch(1)
        try {
            withStreamDeadline(50,{closed.countDown()}) { assertTrue(closed.await(2,TimeUnit.SECONDS)); "not success" }
            fail("Timed-out transfer must fail")
        } catch(e:IOException) { assertTrue(e.message!!.contains("timed out")) }
    }
    @Test fun completedTransferCancelsItsWatchdog() {
        val closed=AtomicBoolean(false)
        assertEquals("ok",withStreamDeadline(150,{closed.set(true)}) { "ok" })
        Thread.sleep(220)
        assertFalse(closed.get())
    }
    @Test fun positionUsesPlaybackSpeedAndClampsToTrackBounds() {
        assertEquals(12000L,mediaPosition(10000,60000,true,2f,1000))
        assertEquals(10000L,mediaPosition(10000,60000,false,2f,1000))
        assertEquals(60000L,mediaPosition(59000,60000,true,1f,5000))
        assertEquals(0L,mediaPosition(-2,60000,true,1f,-200))
        assertEquals(0L,mediaPosition(10000,0,true,1f,1000))
        assertEquals(10000L,mediaPosition(10000,60000,true,Float.NaN,1000))
    }
    @Test fun volumeUsesTheTvsActualRange() {
        assertEquals(50,RemoteVolume(7,2,12,true).percent)
        assertEquals(0,RemoteVolume(0,0,0,false).percent)
        assertEquals(100,RemoteVolume(30,0,15,true).percent)
    }
    @Test fun mediaTimeSupportsLongVideos() {
        assertEquals("0:00",mediaTime(-1))
        assertEquals("3:05",mediaTime(185000))
        assertEquals("1:02:03",mediaTime(3723000))
    }
    @Test fun mediaMetadataIsNotWrittenToAdbLogs() {
        assertFalse(LogSanitizer.output("content call --uri content://io.konnecttv.glowlauncher.media --method status","Private viewing title").contains("Private viewing title"))
    }
}
