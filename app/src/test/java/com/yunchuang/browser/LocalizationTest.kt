package com.yunchuang.browser

import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalizationTest {
    @Before fun clearSites() {
        RuntimeEnvironment.getApplication().getSharedPreferences("yunchuang_prefs", 0)
            .edit().clear().commit()
    }

    @Test @Config(qualifiers = "en-rUS")
    fun englishHomeAndFingerprintEditorOpen() {
        val home = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals("Yunchuang", home.findViewById<TextView>(R.id.txtTitle).text.toString())
        assertEquals("Bilibili", SiteStore(home).list().first().name)
        assertEquals(listOf("https://www.bilibili.com/", "https://www.youtube.com/"), SiteStore(home).list().map { it.url })
        assertEquals("Desktop", SiteStore(home).list().first().modeLabel(home))
        val editor = Robolectric.buildActivity(FingerprintActivity::class.java).setup().get()
        assertEquals("Off", Fingerprint(enabled = false).summary(editor))
        assertTrue(editor.resources.getStringArray(R.array.timezone_labels).first().contains("Beijing"))
    }

    @Test @Config(qualifiers = "zh-rCN")
    fun chineseHomeAndFingerprintEditorOpen() {
        val home = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals("云窗", home.findViewById<TextView>(R.id.txtTitle).text.toString())
        assertEquals("哔哩哔哩", SiteStore(home).list().first().name)
        assertEquals("桌面版", SiteStore(home).list().first().modeLabel(home))
        val editor = Robolectric.buildActivity(FingerprintActivity::class.java).setup().get()
        assertEquals("未启用", Fingerprint(enabled = false).summary(editor))
        assertTrue(editor.resources.getStringArray(R.array.timezone_labels).first().contains("北京"))
    }
}
