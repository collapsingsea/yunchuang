package com.yunchuang.browser

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.EditText
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN")
class RegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearSites() {
        context.getSharedPreferences("yunchuang_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun invalidUrlsAreRejected() {
        assertEquals("https://example.com", Site.normalizeUrl(" example.com "))
        assertEquals("https://example.com/path", Site.normalizeUrl("HTTPS://example.com/path"))
        assertEquals("", Site.normalizeUrl("not a url"))
        assertEquals("", Site.normalizeUrl("javascript://alert(1)"))
    }

    @Test fun restoringDefaultsKeepsNameAndUrl() {
        val controller = Robolectric.buildActivity(SiteEditorActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<EditText>(R.id.edtUrl).setText("example.com")
        activity.findViewById<EditText>(R.id.edtName).setText("My site")
        activity.findViewById<MaterialButton>(R.id.btnResetDefaults).performClick()
        org.robolectric.shadows.ShadowDialog.getLatestDialog().let {
            (it as androidx.appcompat.app.AlertDialog).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        activity.findViewById<MaterialButton>(R.id.btnSave).performClick()
        val saved = SiteStore(context).list().single()
        assertEquals("My site", saved.name)
        assertEquals("https://example.com", saved.url)
        assertTrue(saved.viewportAuto)
        assertEquals(Site.ORIENT_LANDSCAPE, saved.orientation)
        controller.pause().stop().destroy()
    }

    @Test @Config(qualifiers = "w740dp-h360dp-land")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun browserMenuStartsExpandedAndMobileWidthIsEditable() {
        val intent = Intent(context, BrowserActivity::class.java)
            .putExtra(BrowserActivity.EXTRA_URL, "https://example.com")
        val controller = Robolectric.buildActivity(BrowserActivity::class.java, intent).setup()
        val activity = controller.get()
        activity.findViewById<View>(R.id.btnNavMore).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as com.google.android.material.bottomsheet.BottomSheetDialog
        assertEquals(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED, dialog.behavior.state)
        assertEquals(5, dialog.findViewById<android.widget.GridLayout>(R.id.gridActions)!!.columnCount)
        val root = dialog.findViewById<android.widget.GridLayout>(R.id.gridActions)!!.parent as android.view.ViewGroup
        val width = (740 * activity.resources.displayMetrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, width, root.measuredHeight)
        assertTrue(root.height <= (320 * activity.resources.displayMetrics.density).toInt())
        val bitmap = android.graphics.Bitmap.createBitmap(width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        root.draw(canvas)
        val directory = java.io.File("build/qa").apply { mkdirs() }
        java.io.File(directory, "landscape-menu.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        dialog.dismiss()
        val field = BrowserActivity::class.java.getDeclaredField("site").apply { isAccessible = true }
        (field.get(activity) as Site).uaMode = Site.UA_MOBILE
        BrowserActivity::class.java.getDeclaredMethod("showViewportDialog").apply { isAccessible = true }.invoke(activity)
        val widthDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(widthDialog.findViewById<View>(R.id.edtDialogWidth))
        widthDialog.findViewById<EditText>(R.id.edtDialogWidth)!!.setText("720")
        widthDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(720, (field.get(activity) as Site).viewportWidth)
        assertFalse((field.get(activity) as Site).viewportAuto)
        controller.pause().stop().destroy()
    }

    @Test fun oldProxyAndMobileDefaultsSurviveJson() {
        val site = Site.fromJson(JSONObject("""{"url":"https://example.com","ua":1,"proxy":{"en":true,"host":"localhost","port":8080}}"""))
        assertTrue(site.viewportAuto)
        assertTrue(site.proxy.isValid())
        assertEquals(Site.ORIENT_LANDSCAPE, site.orientation)
        site.proxy = ProxyCfg(mode = ProxyCfg.MODE_DIRECT)
        assertTrue(Site.fromJson(site.toJson()).proxy.isDirect())
    }

    @Test fun reorderingPreservesNewerSiteData() {
        val store = SiteStore(context)
        val a = Site(name = "A", url = "https://a.example")
        val b = Site(name = "B", url = "https://b.example")
        store.add(a)
        store.add(b)
        store.update(a.copy(name = "Updated title"))
        val c = Site(name = "C", url = "https://c.example")
        store.add(c)
        store.replaceOrder(listOf(b, a))
        assertEquals(listOf(b.id, a.id, c.id), store.list().map { it.id })
        assertEquals("Updated title", store.get(a.id)?.name)
    }

    @Test fun explicitOrientationIsNotOverwrittenOnUpgrade() {
        val store = SiteStore(context)
        val site = Site(url = "https://example.com", orientation = Site.ORIENT_FOLLOW)
        store.add(site)
        store.seedIfNeeded()
        assertEquals(Site.ORIENT_FOLLOW, store.get(site.id)?.orientation)
    }

    @Test fun temporaryPageSettingsRetainUrlAndConfiguration() {
        val draft = Site(url = "https://example.com/login", uaMode = Site.UA_MOBILE,
            viewportAuto = false, viewportWidth = 720)
        val intent = Intent(context, SiteEditorActivity::class.java)
            .putExtra(SiteEditorActivity.EXTRA_DRAFT, draft.toJson().toString())
        val controller = Robolectric.buildActivity(SiteEditorActivity::class.java, intent).setup()
        val activity = controller.get()
        assertEquals(draft.url, activity.findViewById<EditText>(R.id.edtUrl).text.toString())
        activity.findViewById<MaterialButton>(R.id.btnSave).performClick()
        val saved = SiteStore(context).get(draft.id)!!
        assertEquals(720, saved.viewportWidth)
        assertFalse(saved.viewportAuto)
        assertEquals("", saved.name)
        controller.pause().stop().destroy()
    }

    @Test fun desktopSaveDoesNotDisableMobileAutoWidth() {
        val controller = Robolectric.buildActivity(SiteEditorActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<EditText>(R.id.edtUrl).setText("example.com")
        activity.findViewById<MaterialButton>(R.id.btnSave).performClick()
        assertTrue(SiteStore(context).list().single().viewportAuto)
        controller.pause().stop().destroy()
    }

    @Test fun editingFingerprintPresetChangesLabelAndKeepsFieldEnabled() {
        val intent = Intent(context, FingerprintActivity::class.java)
            .putExtra(FingerprintActivity.EXTRA_FP_JSON, Fingerprint.preset(0).toJson().toString())
        val controller = Robolectric.buildActivity(FingerprintActivity::class.java, intent).setup()
        val activity = controller.get()
        val field = activity.findViewById<EditText>(R.id.edtFpScreenW)
        assertTrue(field.isEnabled)
        assertEquals(1f, field.alpha)
        field.setText("1440")
        assertEquals("自定义", activity.findViewById<MaterialAutoCompleteTextView>(R.id.dropPreset).text.toString())
        controller.pause().stop().destroy()
    }

    @Test @Config(qualifiers = "w740dp-h360dp-land")
    fun landscapeMenuHasScrollableContentAndEditorControlsExist() {
        val controller = Robolectric.buildActivity(SiteEditorActivity::class.java).setup()
        val activity = controller.get()
        val menu = activity.layoutInflater.inflate(R.layout.sheet_browser_menu, null)
        assertTrue(menu is NestedScrollView)
        activity.findViewById<MaterialButtonToggleGroup>(R.id.groupMode).check(R.id.btnModeMobile)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mobileViewportAutoBlock).visibility)
        assertTrue(activity.findViewById<MaterialSwitch>(R.id.switchViewportAuto).isChecked)
        controller.pause().stop().destroy()
    }
}
