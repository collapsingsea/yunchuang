package com.yunchuang.browser

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SiteEditorActivity : AppCompatActivity() {

    private lateinit var store: SiteStore
    private lateinit var site: Site
    private var isEdit = false

    private lateinit var previewTile: TextView
    private lateinit var previewName: TextView
    private lateinit var previewHost: TextView
    private lateinit var edtName: TextInputEditText
    private lateinit var edtUrl: TextInputEditText
    private lateinit var tilUrl: TextInputLayout
    private lateinit var groupMode: MaterialButtonToggleGroup
    private lateinit var customUaBlock: View
    private lateinit var edtCustomUa: TextInputEditText
    private lateinit var chipsUaPresets: ChipGroup
    private lateinit var desktopBlock: View
    private lateinit var mobileViewportAutoBlock: View
    private lateinit var chipsViewport: ChipGroup
    private lateinit var sliderViewport: Slider
    private lateinit var tilExactWidth: TextInputLayout
    private lateinit var edtExactWidth: TextInputEditText
    private lateinit var switchViewportAuto: MaterialSwitch
    private lateinit var switchAutoFit: MaterialSwitch
    private lateinit var tilCustomScale: TextInputLayout
    private lateinit var edtCustomScale: TextInputEditText
    private lateinit var switchInject: MaterialSwitch
    private lateinit var groupOrientation: MaterialButtonToggleGroup
    private lateinit var proHeader: View
    private lateinit var proChevron: ImageView
    private lateinit var proContent: View
    private lateinit var sliderTextZoom: Slider
    private lateinit var txtTextZoomValue: TextView
    private lateinit var switchJs: MaterialSwitch
    private lateinit var switchImages: MaterialSwitch
    private lateinit var switchThirdCookies: MaterialSwitch
    private lateinit var switchForceZoom: MaterialSwitch
    private lateinit var switchKeepScreenOn: MaterialSwitch
    private lateinit var switchReloadOnChange: MaterialSwitch
    private lateinit var rowFingerprint: View
    private lateinit var txtFpSummary: TextView
    private lateinit var groupProxyMode: MaterialButtonToggleGroup
    private lateinit var proxyDetail: View
    private lateinit var groupProxyType: MaterialButtonToggleGroup
    private lateinit var edtProxyHost: TextInputEditText
    private lateinit var edtProxyPort: TextInputEditText
    private lateinit var edtProxyUser: TextInputEditText
    private lateinit var edtProxyPass: TextInputEditText

    private lateinit var pendingFp: Fingerprint
    private lateinit var fpLauncher: ActivityResultLauncher<Intent>

    /** 防止 滑杆 ↔ 输入框 双向同步时互相触发 */
    private var syncingViewport = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        setupStatusBarStyle()

        store = SiteStore(this)
        val existing = store.get(intent.getStringExtra(EXTRA_SITE_ID))
        isEdit = existing != null
        val draft = intent.getStringExtra(EXTRA_DRAFT)?.let {
            runCatching { Site.fromJson(org.json.JSONObject(it)) }.getOrNull()
        }
        site = existing?.copy() ?: draft ?: Site(
            url = intent.getStringExtra(EXTRA_PREFILL_URL).orEmpty(),
            colorIndex = (store.list().size) % Ui.gradientCount
        )
        pendingFp = site.fingerprint.copy()
        registerFpLauncher()

        bindViews()
        buildChips()
        fillForm()
        wireListeners()
    }

    private fun setupStatusBarStyle() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    private fun bindViews() {
        previewTile = findViewById(R.id.previewTile)
        previewName = findViewById(R.id.previewName)
        previewHost = findViewById(R.id.previewHost)
        edtName = findViewById(R.id.edtName)
        edtUrl = findViewById(R.id.edtUrl)
        tilUrl = findViewById(R.id.tilUrl)
        groupMode = findViewById(R.id.groupMode)
        customUaBlock = findViewById(R.id.customUaBlock)
        edtCustomUa = findViewById(R.id.edtCustomUa)
        chipsUaPresets = findViewById(R.id.chipsUaPresets)
        desktopBlock = findViewById(R.id.desktopBlock)
        mobileViewportAutoBlock = findViewById(R.id.mobileViewportAutoBlock)
        chipsViewport = findViewById(R.id.chipsViewport)
        sliderViewport = findViewById(R.id.sliderViewport)
        tilExactWidth = findViewById(R.id.tilExactWidth)
        edtExactWidth = findViewById(R.id.edtExactWidth)
        switchViewportAuto = findViewById(R.id.switchViewportAuto)
        switchAutoFit = findViewById(R.id.switchAutoFit)
        tilCustomScale = findViewById(R.id.tilCustomScale)
        edtCustomScale = findViewById(R.id.edtCustomScale)
        switchInject = findViewById(R.id.switchInject)
        groupOrientation = findViewById(R.id.groupOrientation)
        proHeader = findViewById(R.id.proHeader)
        proChevron = findViewById(R.id.proChevron)
        proContent = findViewById(R.id.proContent)
        sliderTextZoom = findViewById(R.id.sliderTextZoom)
        txtTextZoomValue = findViewById(R.id.txtTextZoomValue)
        switchJs = findViewById(R.id.switchJs)
        switchImages = findViewById(R.id.switchImages)
        switchThirdCookies = findViewById(R.id.switchThirdCookies)
        switchForceZoom = findViewById(R.id.switchForceZoom)
        switchKeepScreenOn = findViewById(R.id.switchKeepScreenOn)
        switchReloadOnChange = findViewById(R.id.switchReloadOnChange)
        rowFingerprint = findViewById(R.id.rowFingerprint)
        txtFpSummary = findViewById(R.id.txtFpSummary)
        groupProxyMode = findViewById(R.id.groupProxyMode)
        proxyDetail = findViewById(R.id.proxyDetail)
        groupProxyType = findViewById(R.id.groupProxyType)
        edtProxyHost = findViewById(R.id.edtProxyHost)
        edtProxyPort = findViewById(R.id.edtProxyPort)
        edtProxyUser = findViewById(R.id.edtProxyUser)
        edtProxyPass = findViewById(R.id.edtProxyPass)
    }

    private fun registerFpLauncher() {
        fpLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val json = result.data?.getStringExtra(FingerprintActivity.EXTRA_FP_JSON)
                if (json != null) {
                    pendingFp = Fingerprint.fromJson(org.json.JSONObject(json))
                    refreshFpSummary()
                }
            }
        }
    }

    private fun refreshFpSummary() {
        txtFpSummary.text = pendingFp.summary(this)
    }

    private fun buildChips() {
        Site.VIEWPORT_PRESETS.forEach { width ->
            chipsViewport.addView(Chip(this).apply {
                text = "$width"
                isCheckable = false
                setOnClickListener { setViewportValue(width) }
            })
        }
        Site.UA_PRESETS.forEach { (label, ua) ->
            chipsUaPresets.addView(Chip(this).apply {
                text = when (label) {
                    "Android 平板" -> getString(R.string.android_tablet)
                    "微信内置" -> getString(R.string.wechat_ua)
                    else -> label
                }
                isCheckable = false
                setOnClickListener { edtCustomUa.setText(ua) }
            })
        }
    }

    private fun fillForm() {
        findViewById<TextView>(R.id.txtEditorTitle).text =
            getString(if (isEdit) R.string.editor_title_edit else R.string.editor_title_add)

        edtName.setText(site.name)
        edtUrl.setText(site.url)
        edtCustomUa.setText(site.customUa)
        edtCustomScale.setText(site.customScale.toString())

        setViewportValue(site.viewportWidth)
        switchAutoFit.isChecked = site.autoFit
        switchViewportAuto.isChecked = site.viewportAuto
        switchInject.isChecked = site.injectViewport

        sliderTextZoom.value = ((site.textZoom.coerceIn(50, 200) / 5) * 5).toFloat()
        txtTextZoomValue.text = "${sliderTextZoom.value.toInt()}%"
        switchJs.isChecked = site.jsEnabled
        switchImages.isChecked = site.loadImages
        switchThirdCookies.isChecked = site.thirdPartyCookies
        switchForceZoom.isChecked = site.forceZoom
        switchKeepScreenOn.isChecked = site.keepScreenOn
        switchReloadOnChange.isChecked = site.reloadOnChange

        refreshFpSummary()
        groupProxyMode.check(
            when (site.proxy.mode) {
                ProxyCfg.MODE_DIRECT -> R.id.btnProxyDirect
                ProxyCfg.MODE_CUSTOM -> R.id.btnProxyCustom
                else -> R.id.btnProxySystem
            }
        )
        groupProxyType.check(
            if (site.proxy.type == ProxyCfg.TYPE_SOCKS) R.id.btnProxySocks else R.id.btnProxyHttp
        )
        edtProxyHost.setText(site.proxy.host)
        edtProxyPort.setText(if (site.proxy.port > 0) site.proxy.port.toString() else "")
        edtProxyUser.setText(site.proxy.username)
        edtProxyPass.setText(site.proxy.password)
        proxyDetail.visibility = if (site.proxy.mode == ProxyCfg.MODE_CUSTOM) View.VISIBLE else View.GONE

        groupMode.check(
            when (site.uaMode) {
                Site.UA_MOBILE -> R.id.btnModeMobile
                Site.UA_CUSTOM -> R.id.btnModeCustom
                else -> R.id.btnModeDesktop
            }
        )
        groupOrientation.check(
            when (site.orientation) {
                Site.ORIENT_PORTRAIT -> R.id.btnOrientPortrait
                Site.ORIENT_LANDSCAPE -> R.id.btnOrientLandscape
                else -> R.id.btnOrientFollow
            }
        )

        findViewById<MaterialButton>(R.id.btnDeleteSite).visibility =
            if (isEdit) View.VISIBLE else View.GONE

        // 非默认的专业设置：默认展开，方便看到已改了什么
        if (!site.jsEnabled || !site.loadImages || !site.thirdPartyCookies ||
            site.forceZoom || site.keepScreenOn || site.textZoom != 100
        ) {
            setProExpanded(true)
        }

        refreshPreview()
        refreshVisibility()
    }

    /** 统一入口：把宽度写进滑杆和输入框 */
    private fun setViewportValue(width: Int) {
        if (syncingViewport) return
        syncingViewport = true
        val w = width.coerceIn(Site.VIEWPORT_MIN, Site.VIEWPORT_MAX)
        sliderViewport.value = w.toFloat()
        edtExactWidth.setText(w.toString())
        tilExactWidth.error = null
        if (selectedUaMode() == Site.UA_MOBILE) switchViewportAuto.isChecked = false
        syncingViewport = false
    }

    private fun wireListeners() {
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        previewTile.setOnClickListener {
            site.colorIndex = (site.colorIndex + 1) % Ui.gradientCount
            refreshPreview()
        }

        val previewWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshPreview()
        }
        edtName.addTextChangedListener(previewWatcher)
        edtUrl.addTextChangedListener(previewWatcher)

        groupMode.addOnButtonCheckedListener { _, _, _ -> refreshVisibility() }
        switchAutoFit.setOnCheckedChangeListener { _, _ -> refreshVisibility() }
        switchViewportAuto.setOnCheckedChangeListener { _, _ -> refreshVisibility() }
        groupProxyMode.addOnButtonCheckedListener { _, _, _ ->
            proxyDetail.visibility = if (selectedProxyMode() == ProxyCfg.MODE_CUSTOM) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        // 滑杆 → 输入框
        sliderViewport.setLabelFormatter { "${it.toInt()} px" }
        sliderViewport.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !syncingViewport) {
                syncingViewport = true
                edtExactWidth.setText(value.toInt().toString())
                tilExactWidth.error = null
                if (selectedUaMode() == Site.UA_MOBILE) switchViewportAuto.isChecked = false
                syncingViewport = false
            }
        }

        // 输入框 → 滑杆
        edtExactWidth.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (syncingViewport) return
                val value = s?.toString()?.trim()?.toIntOrNull() ?: return
                if (value in Site.VIEWPORT_MIN..Site.VIEWPORT_MAX) {
                    syncingViewport = true
                    sliderViewport.value = value.toFloat()
                    tilExactWidth.error = null
                    if (selectedUaMode() == Site.UA_MOBILE) switchViewportAuto.isChecked = false
                    syncingViewport = false
                } else {
                    tilExactWidth.error = "${Site.VIEWPORT_MIN} - ${Site.VIEWPORT_MAX}"
                }
            }
        })

        sliderTextZoom.setLabelFormatter { "${it.toInt()}%" }
        sliderTextZoom.addOnChangeListener { _, value, _ ->
            txtTextZoomValue.text = "${value.toInt()}%"
        }

        proHeader.setOnClickListener { setProExpanded(proContent.visibility != View.VISIBLE) }

        rowFingerprint.setOnClickListener {
            fpLauncher.launch(
                Intent(this, FingerprintActivity::class.java)
                    .putExtra(FingerprintActivity.EXTRA_FP_JSON, pendingFp.toJson().toString())
            )
        }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btnResetDefaults).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.reset_settings))
                .setMessage(getString(R.string.reset_message))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.reset)) { _, _ ->
                    site = Site(id = site.id, name = edtName.text.toString(),
                        url = edtUrl.text.toString(), colorIndex = site.colorIndex, createdAt = site.createdAt)
                    pendingFp = site.fingerprint.copy()
                    fillForm()
                }.show()
        }
        findViewById<MaterialButton>(R.id.btnDeleteSite).setOnClickListener { confirmDelete() }
    }

    private fun setProExpanded(expanded: Boolean) {
        proContent.visibility = if (expanded) View.VISIBLE else View.GONE
        proChevron.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
    }

    private fun selectedUaMode(): Int = when (groupMode.checkedButtonId) {
        R.id.btnModeMobile -> Site.UA_MOBILE
        R.id.btnModeCustom -> Site.UA_CUSTOM
        else -> Site.UA_DESKTOP
    }

    private fun selectedOrientation(): Int = when (groupOrientation.checkedButtonId) {
        R.id.btnOrientPortrait -> Site.ORIENT_PORTRAIT
        R.id.btnOrientLandscape -> Site.ORIENT_LANDSCAPE
        else -> Site.ORIENT_FOLLOW
    }

    private fun selectedProxyMode(): Int = when (groupProxyMode.checkedButtonId) {
        R.id.btnProxyDirect -> ProxyCfg.MODE_DIRECT
        R.id.btnProxyCustom -> ProxyCfg.MODE_CUSTOM
        else -> ProxyCfg.MODE_SYSTEM
    }

    private fun refreshVisibility() {
        val mode = selectedUaMode()
        val mobileAuto = mode == Site.UA_MOBILE && switchViewportAuto.isChecked
        customUaBlock.visibility = if (mode == Site.UA_CUSTOM) View.VISIBLE else View.GONE
        desktopBlock.visibility = View.VISIBLE
        mobileViewportAutoBlock.visibility = if (mode == Site.UA_MOBILE) View.VISIBLE else View.GONE
        switchAutoFit.visibility = if (mobileAuto) View.GONE else View.VISIBLE
        tilCustomScale.visibility = if (mobileAuto || switchAutoFit.isChecked) View.GONE else View.VISIBLE
    }

    private fun refreshPreview() {
        val name = edtName.text?.toString().orEmpty()
        val url = Site.normalizeUrl(edtUrl.text?.toString().orEmpty())
        val shadow = site.copy(name = name, url = url)
        Ui.bindTile(previewTile, shadow, 16f)
        previewName.text = name.ifBlank { shadow.host().ifBlank { getString(R.string.site_name) } }
        previewHost.text = shadow.host().ifBlank { "example.com" }
    }

    private fun save() {
        val url = Site.normalizeUrl(edtUrl.text?.toString().orEmpty())
        if (url.isBlank()) {
            tilUrl.error = getString(R.string.url_required)
            return
        }
        tilUrl.error = null

        val width = edtExactWidth.text?.toString()?.trim()?.toIntOrNull()
            ?: sliderViewport.value.toInt()
        if (width !in Site.VIEWPORT_MIN..Site.VIEWPORT_MAX) {
            tilExactWidth.error = "${Site.VIEWPORT_MIN} - ${Site.VIEWPORT_MAX}"
            return
        }
        tilExactWidth.error = null

        val scale = edtCustomScale.text?.toString()?.trim()?.toIntOrNull() ?: 100
        if (!(selectedUaMode() == Site.UA_MOBILE && switchViewportAuto.isChecked) &&
            !switchAutoFit.isChecked && scale !in 20..200) {
            tilCustomScale.error = getString(R.string.scale_range)
            return
        }
        tilCustomScale.error = null

        val proxyMode = selectedProxyMode()
        if (proxyMode == ProxyCfg.MODE_CUSTOM) {
            val pHost = edtProxyHost.text?.toString()?.trim().orEmpty()
            val pPort = edtProxyPort.text?.toString()?.trim()?.toIntOrNull() ?: 0
            if (pHost.isBlank() || pPort !in 1..65535) {
                Toast.makeText(this, getString(R.string.proxy_invalid), Toast.LENGTH_SHORT).show()
                return
            }
        }

        site.apply {
            this.url = url
            // 留空表示自动命名：首次加载网页标题后写回，首页显示时仍以域名兜底。
            name = edtName.text?.toString()?.trim().orEmpty()
            uaMode = selectedUaMode()
            customUa = edtCustomUa.text?.toString()?.trim().orEmpty()
            viewportWidth = width
            viewportAuto = switchViewportAuto.isChecked
            autoFit = switchAutoFit.isChecked
            customScale = scale.coerceIn(20, 200)
            orientation = selectedOrientation()
            injectViewport = switchInject.isChecked
            textZoom = sliderTextZoom.value.toInt()
            jsEnabled = switchJs.isChecked
            loadImages = switchImages.isChecked
            thirdPartyCookies = switchThirdCookies.isChecked
            forceZoom = switchForceZoom.isChecked
            keepScreenOn = switchKeepScreenOn.isChecked
            reloadOnChange = switchReloadOnChange.isChecked
            fingerprint = pendingFp
            proxy = ProxyCfg(
                mode = proxyMode,
                enabled = proxyMode == ProxyCfg.MODE_CUSTOM,
                type = if (groupProxyType.checkedButtonId == R.id.btnProxySocks)
                    ProxyCfg.TYPE_SOCKS else ProxyCfg.TYPE_HTTP,
                host = edtProxyHost.text?.toString()?.trim().orEmpty(),
                port = edtProxyPort.text?.toString()?.trim()?.toIntOrNull() ?: 0,
                username = edtProxyUser.text?.toString()?.trim().orEmpty(),
                password = edtProxyPass.text?.toString().orEmpty()
            )
        }

        if (isEdit) store.update(site) else store.add(site)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_ID, site.id))
        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_site))
            .setMessage(getString(R.string.delete_message, site.name.ifBlank { site.host() }))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                store.delete(site.id)
                setResult(RESULT_DELETED)
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_DRAFT = "site_draft"
        const val EXTRA_SITE_ID = "site_id"
        const val EXTRA_PREFILL_URL = "prefill_url"
        const val EXTRA_RESULT_ID = "result_id"
        const val RESULT_DELETED = 9
    }
}
