package com.yunchuang.browser

import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

/**
 * 指纹配置页：选择身份预设，或切到「自定义」逐项编辑。
 * 通过 [EXTRA_FP_JSON] 传入当前指纹，保存后原样带回。
 */
class FingerprintActivity : AppCompatActivity() {

    private lateinit var fpBody: View
    private lateinit var switchEnable: MaterialSwitch
    private lateinit var dropPreset: MaterialAutoCompleteTextView
    private lateinit var dropPlatform: MaterialAutoCompleteTextView
    private lateinit var dropTimezone: MaterialAutoCompleteTextView
    private lateinit var edtLanguage: TextInputEditText
    private lateinit var edtLanguages: TextInputEditText
    private lateinit var edtVendor: TextInputEditText
    private lateinit var edtScreenW: TextInputEditText
    private lateinit var edtScreenH: TextInputEditText
    private lateinit var edtDpr: TextInputEditText
    private lateinit var edtCores: TextInputEditText
    private lateinit var edtMemory: TextInputEditText
    private lateinit var edtGlVendor: TextInputEditText
    private lateinit var edtGlRenderer: TextInputEditText
    private lateinit var switchCanvasNoise: MaterialSwitch
    private lateinit var switchTouch: MaterialSwitch

    private val presetItems: List<String> by lazy { Fingerprint.PRESET_LABELS + getString(R.string.mode_custom) }
    private val tzLabels: List<String> by lazy { resources.getStringArray(R.array.timezone_labels).toList() }
    private var currentPreset: Int = Fingerprint.CUSTOM
    private var updatingFields = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fingerprint)
        setupStatusBarStyle()

        val fp = Fingerprint.fromJson(
            intent.getStringExtra(EXTRA_FP_JSON)?.let { org.json.JSONObject(it) }
        )

        bindViews()
        setupAdapters()
        fillFields(fp)

        currentPreset = if (fp.preset in Fingerprint.PRESET_LABELS.indices) fp.preset else Fingerprint.CUSTOM
        dropPreset.setText(presetItems[if (currentPreset == Fingerprint.CUSTOM) presetItems.lastIndex else currentPreset], false)
        setFieldsEditable()
        applyEnableState(fp.enabled)

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
        fpBody = findViewById(R.id.fpBody)
        switchEnable = findViewById(R.id.switchFpEnable)
        dropPreset = findViewById(R.id.dropPreset)
        dropPlatform = findViewById(R.id.dropPlatform)
        dropTimezone = findViewById(R.id.dropTimezone)
        edtLanguage = findViewById(R.id.edtFpLanguage)
        edtLanguages = findViewById(R.id.edtFpLanguages)
        edtVendor = findViewById(R.id.edtFpVendor)
        edtScreenW = findViewById(R.id.edtFpScreenW)
        edtScreenH = findViewById(R.id.edtFpScreenH)
        edtDpr = findViewById(R.id.edtFpDpr)
        edtCores = findViewById(R.id.edtFpCores)
        edtMemory = findViewById(R.id.edtFpMemory)
        edtGlVendor = findViewById(R.id.edtFpGlVendor)
        edtGlRenderer = findViewById(R.id.edtFpGlRenderer)
        switchCanvasNoise = findViewById(R.id.switchCanvasNoise)
        switchTouch = findViewById(R.id.switchTouch)
    }

    private fun setupAdapters() {
        dropPreset.setSimpleItems(presetItems.toTypedArray())
        dropPlatform.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, Fingerprint.PLATFORMS)
        )
        dropTimezone.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, tzLabels)
        )
    }

    private fun fillFields(fp: Fingerprint) {
        updatingFields = true
        dropPlatform.setText(fp.platform, false)
        val tzIndex = Fingerprint.TIMEZONES.indexOfFirst { it.second == fp.timezone }.coerceAtLeast(0)
        dropTimezone.setText(tzLabels[tzIndex], false)
        edtLanguage.setText(fp.language)
        edtLanguages.setText(fp.languages)
        edtVendor.setText(fp.vendor)
        edtScreenW.setText(fp.screenW.toString())
        edtScreenH.setText(fp.screenH.toString())
        edtDpr.setText(if (fp.dpr % 1.0 == 0.0) fp.dpr.toInt().toString() else fp.dpr.toString())
        edtCores.setText(fp.cores.toString())
        edtMemory.setText(fp.memory.toString())
        edtGlVendor.setText(fp.glVendor)
        edtGlRenderer.setText(fp.glRenderer)
        switchCanvasNoise.isChecked = fp.canvasNoise
        switchTouch.isChecked = fp.touch
        updatingFields = false
    }

    private fun wireListeners() {
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        switchEnable.setOnCheckedChangeListener { _, checked -> applyEnableState(checked) }

        dropPreset.setOnItemClickListener { _, _, position, _ ->
            if (position in Fingerprint.PRESET_LABELS.indices) {
                currentPreset = position
                fillFields(Fingerprint.preset(position))
                setFieldsEditable()
            } else {
                currentPreset = Fingerprint.CUSTOM
                setFieldsEditable()
            }
        }

        dropPlatform.setOnItemClickListener { _, _, _, _ -> markCustom() }
        dropTimezone.setOnItemClickListener { _, _, _, _ -> markCustom() }
        listOf(
            edtLanguage, edtLanguages, edtVendor, edtScreenW, edtScreenH,
            edtDpr, edtCores, edtMemory, edtGlVendor, edtGlRenderer
        ).forEach { field ->
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { markCustom() }
            })
        }
        switchCanvasNoise.setOnCheckedChangeListener { _, _ -> markCustom() }
        switchTouch.setOnCheckedChangeListener { _, _ -> markCustom() }

        findViewById<MaterialButton>(R.id.btnFpSave).setOnClickListener { save() }
    }

    private fun applyEnableState(enabled: Boolean) {
        if (switchEnable.isChecked != enabled) switchEnable.isChecked = enabled
        fpBody.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    /** 预设只是填充一组初始值，用户仍可直接修改，修改后自动切换为自定义。 */
    private fun setFieldsEditable() {
        val fields = listOf<View>(
            dropPlatform, dropTimezone, edtLanguage, edtLanguages, edtVendor,
            edtScreenW, edtScreenH, edtDpr, edtCores, edtMemory,
            edtGlVendor, edtGlRenderer, switchCanvasNoise, switchTouch
        )
        fields.forEach {
            it.isEnabled = true
            it.alpha = 1f
        }
    }

    private fun markCustom() {
        if (updatingFields || currentPreset == Fingerprint.CUSTOM) return
        currentPreset = Fingerprint.CUSTOM
        dropPreset.setText(presetItems.last(), false)
    }

    private fun save() {
        val tzIndex = tzLabels.indexOf(dropTimezone.text?.toString()).coerceAtLeast(0)
        val tz = Fingerprint.TIMEZONES[tzIndex]
        val fp = Fingerprint(
            enabled = switchEnable.isChecked,
            preset = currentPreset,
            platform = dropPlatform.text?.toString()?.ifBlank { "Win32" } ?: "Win32",
            vendor = edtVendor.text?.toString().orEmpty(),
            language = edtLanguage.text?.toString()?.ifBlank { "en-US" } ?: "en-US",
            languages = edtLanguages.text?.toString()?.ifBlank { "en-US,en" } ?: "en-US,en",
            timezone = tz.second,
            tzOffset = tz.third,
            screenW = edtScreenW.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(200, 8000) ?: 1920,
            screenH = edtScreenH.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(200, 8000) ?: 1080,
            dpr = edtDpr.text?.toString()?.trim()?.toDoubleOrNull()?.coerceIn(0.5, 5.0) ?: 1.0,
            cores = edtCores.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 64) ?: 8,
            memory = edtMemory.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 64) ?: 8,
            glVendor = edtGlVendor.text?.toString().orEmpty(),
            glRenderer = edtGlRenderer.text?.toString().orEmpty(),
            canvasNoise = switchCanvasNoise.isChecked,
            touch = switchTouch.isChecked
        )
        setResult(RESULT_OK, intent.putExtra(EXTRA_FP_JSON, fp.toJson().toString()))
        finish()
    }

    companion object {
        const val EXTRA_FP_JSON = "fp_json"
    }
}
