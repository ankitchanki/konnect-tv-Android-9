package io.konnecttv

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.konnecttv.data.SavedTv
import io.konnecttv.data.Store
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SavedTvStorageTest {
    @Test fun oldSavedTvsMigrateAndNamesTagsAndLastAddressSurviveRestart() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(base.cacheDir, "store-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = dir }
        try {
            File(dir, "tvs.json").writeText("""[{"id":"old","name":"Bedroom","host":"192.168.1.5","port":5555},null,{"broken":true}]""")
            val store = Store(context)
            assertEquals("", store.tvs().single().tag)
            store.saveTv(SavedTv("friend", "Sam's TV", "192.168.1.9", 5555, tag = "Friend's home"))
            assertEquals("friend", Store(context).tvs().first().id)
            store.updateTv(store.tvs().first().copy(host = "192.168.1.11", name = "Living room"))
            val saved = Store(context).tvs()
            assertEquals(2, saved.size)
            assertEquals("Friend's home", saved.first().tag)
            assertEquals("192.168.1.11", saved.first().host)
            assertEquals("Bedroom", saved.last().name)
            store.forgetTv("friend")
            assertEquals("old", Store(context).tvs().single().id)
        } finally { dir.deleteRecursively() }
    }
}
