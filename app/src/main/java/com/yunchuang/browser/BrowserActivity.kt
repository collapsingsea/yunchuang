package com.yunchuang.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.UUID

class BrowserActivity : AppCompatActivity() {

    private lateinit var store: SiteStore
    private lateinit var site: Site
    private var isTemp = false

    private lateinit var browserRoot: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomBar: View
    private lateinit var bubble: ImageButton
    private lateinit var videoContainer: FrameLayout
    private lateinit var dismissZone: View
    private lateinit var dismissCircle: View
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton

    private var pageTitle: String = ""
    private var lastFailedUrl: String? = null
    private var pageFailed = false
    private var loginPromptVisible = false

    /** 工具栏可见性状态机：滚动自动隐藏 / 手动收起成浮钮 / 本次会话彻底隐藏 */
    private var manualCollapsed = false
    private var autoHidden = false
    private var sessionDismissed = false
    private var dismissHot = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var editorLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private data class DownloadReq(
        val url: String,
        val ua: String,
        val contentDisposition: String?,
        val mime: String?
    )

    private var pendingDownload: DownloadReq? = null
    private val sslAllowedHosts = mutableSetOf<String>()

    /** 指纹注入：优先用文档开始脚本，不支持时退化为 onPageStarted 注入 */
    private var fpScriptHandler: ScriptHandler? = null
    private var fpFallbackScript: String = ""
    private var proxyApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SiteStore(this)

        val resolved = resolveSite()
        if (resolved == null) {
            Toast.makeText(this, getString(R.string.site_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        site = resolved

        setContentView(R.layout.activity_browser)
        bindViews()
        applyImmersive()
        registerLaunchers()
        configureWebView()
        setupBottomBar()
        setupBubbleDrag()
        setupBackHandling()

        applySiteConfig()
        applyOrientation()
        updateTaskDescription()

        applyFingerprint()
        applyProxyThen {
            webView.post {
                applyScale()
                webView.loadUrl(site.url)
            }
        }
    }

    // ---------- 站点解析 ----------

    private fun resolveSite(): Site? {
        val id = intent.getStringExtra(EXTRA_SITE_ID)
            ?: intent.data?.takeIf { it.scheme == "yunchuang" && it.host == "site" }?.lastPathSegment
        if (id != null) {
            isTemp = false
            return store.get(id)
        }

        val sharedText = if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null
        val url = sharedText?.let { Regex("https?://\\S+").find(it)?.value }
            ?: intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) return null

        isTemp = true
        val temp = Site(url = Site.normalizeUrl(url))
        // 临时打开也保留空名称，首次加载完成后用网页 title 命名。
        temp.name = ""
        return temp
    }

    // ---------- 视图与系统栏 ----------

    private fun bindViews() {
        browserRoot = findViewById(R.id.browserRoot)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        bottomBar = findViewById(R.id.bottomBar)
        bubble = findViewById(R.id.btnBubble)
        videoContainer = findViewById(R.id.videoContainer)
        dismissZone = findViewById(R.id.dismissZone)
        dismissCircle = findViewById(R.id.dismissCircle)
        btnBack = findViewById(R.id.btnNavBack)
        btnForward = findViewById(R.id.btnNavForward)
    }

    private fun applyImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @Suppress("DEPRECATION")
    private fun updateTaskDescription() {
        setTaskDescription(ActivityManager.TaskDescription(site.name.ifBlank { site.host() }))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    // ---------- 启动器注册 ----------

    private fun registerLaunchers() {
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

        editorLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            when (result.resultCode) {
                SiteEditorActivity.RESULT_DELETED -> {
                    Toast.makeText(this, getString(R.string.site_deleted), Toast.LENGTH_SHORT).show()
                    finish()
                }
                RESULT_OK -> {
                    val id = result.data?.getStringExtra(SiteEditorActivity.EXTRA_RESULT_ID)
                    store.get(id)?.let {
                        site = it
                        isTemp = false
                        applySiteConfig()
                        applyFingerprint()
                        applyOrientation()
                        updateTaskDescription()
                        if (site.reloadOnChange) {
                            reloadPage()
                        } else {
                            webView.post { applyScale(); injectPageFixes() }
                            Toast.makeText(this, getString(R.string.reload_to_apply), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val req = pendingDownload
            pendingDownload = null
            if (granted && req != null) enqueueDownload(req)
            else if (!granted) Toast.makeText(this, getString(R.string.storage_denied), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- WebView 配置 ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.defaultTextEncodingName = "UTF-8"
        s.setSupportMultipleWindows(true)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.mediaPlaybackRequiresUserGesture = false

        CookieManager.getInstance().setAcceptCookie(true)

        webView.setBackgroundColor(0xFFFFFFFF.toInt())
        webView.isHorizontalScrollBarEnabled = true
        webView.isVerticalScrollBarEnabled = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (manualCollapsed || sessionDismissed || customView != null) {
                return@setOnScrollChangeListener
            }
            val dy = scrollY - oldScrollY
            if (dy > 8 && scrollY > 120) setAutoHidden(true)
            else if (dy < -8 || scrollY <= 40) setAutoHidden(false)
        }

        webView.setDownloadListener { url, ua, contentDisposition, mime, _ ->
            handleDownload(url, ua, contentDisposition, mime)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return when {
                    url.startsWith(RETRY_URL) -> {
                        val target = lastFailedUrl ?: site.url
                        pageFailed = false
                        lastFailedUrl = null
                        view.post {
                            applyScale()
                            view.loadUrl(target)
                        }
                        true
                    }
                    request.isForMainFrame && isGoogleAuthUrl(url) -> {
                        offerBrowserLogin()
                        true
                    }
                    url.startsWith("http://") || url.startsWith("https://") -> false
                    url.startsWith("about:") -> false
                    else -> {
                        openExternalScheme(url)
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                pageTitle = ""
                if (url != lastFailedUrl) pageFailed = false
                if (fpFallbackScript.isNotEmpty()) {
                    view.evaluateJavascript(fpFallbackScript, null)
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                injectPageFixes()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.post { injectPageFixes() }
                if (!pageFailed && site.name.isBlank() && !isGoogleAuthUrl(url.orEmpty())) {
                    val title = view.title?.trim().orEmpty()
                    if (title.isNotBlank() && title != url && !title.startsWith("http")) {
                        site.name = title.take(200)
                        if (!isTemp) store.update(site)
                        updateTaskDescription()
                    }
                }
                updateNavButtons()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                updateNavButtons()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (!request.isForMainFrame) return
                val desc = error.description?.toString().orEmpty()
                if (desc.contains("ERR_ABORTED")) return
                showErrorPage(request.url.toString(), desc)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest,
                response: android.webkit.WebResourceResponse) {
                if (request.isForMainFrame) pageFailed = true
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: android.net.http.SslError
            ) {
                val host = try {
                    Uri.parse(error.url).host.orEmpty()
                } catch (e: Exception) {
                    ""
                }
                if (host.isNotEmpty() && host in sslAllowedHosts) {
                    handler.proceed()
                    return
                }
                MaterialAlertDialogBuilder(this@BrowserActivity)
                    .setTitle(getString(R.string.certificate_error))
                    .setMessage(getString(R.string.certificate_message, host))
                    .setNegativeButton(getString(R.string.cancel)) { _, _ -> handler.cancel() }
                    .setPositiveButton(getString(R.string.continue_visit)) { _, _ ->
                        if (host.isNotEmpty()) sslAllowedHosts.add(host)
                        handler.proceed()
                    }
                    .setOnCancelListener { handler.cancel() }
                    .show()
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String?,
                realm: String?
            ) {
                // 代理返回 407 时，若配置了代理账号密码则自动回填
                val p = site.proxy
                if (p.isValid() && p.username.isNotBlank() &&
                    host != null && host.equals(p.host, ignoreCase = true)
                ) {
                    handler.proceed(p.username, p.password)
                    return
                }
                showHttpAuthDialog(handler, host)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.alpha = 1f
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.animate().alpha(0f).setDuration(240)
                        .withEndAction { progressBar.visibility = View.GONE }
                }
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                pageTitle = title?.trim().orEmpty()
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                // 新窗口统一并回主 WebView：临时 WebView 只负责捕获目标地址
                val temp = WebView(this@BrowserActivity)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val target = request.url.toString()
                        if (isGoogleAuthUrl(target)) offerBrowserLogin()
                        else if (target.startsWith("http://") || target.startsWith("https://")) webView.loadUrl(target)
                        v.destroy()
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = temp
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                wv: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    fileChooserLauncher.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, false, false)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                videoContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                videoContainer.visibility = View.VISIBLE
                bottomBar.visibility = View.GONE
                bubble.visibility = View.GONE
                applyKeepScreenOn()
            }

            override fun onHideCustomView() {
                customView?.let { videoContainer.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                videoContainer.visibility = View.GONE
                applyKeepScreenOn()
                when {
                    sessionDismissed -> Unit
                    manualCollapsed -> bubble.visibility = View.VISIBLE
                    else -> bottomBar.visibility = View.VISIBLE
                }
            }
        }
    }

    // ---------- 站点配置应用 ----------

    private fun applySiteConfig() {
        val s = webView.settings
        s.userAgentString = if (site.uaMode == Site.UA_MOBILE) WebSettings.getDefaultUserAgent(this) else site.resolvedUa()
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.javaScriptEnabled = site.jsEnabled
        s.loadsImagesAutomatically = site.loadImages
        s.blockNetworkImage = !site.loadImages
        s.textZoom = site.textZoom.coerceIn(50, 200)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, site.thirdPartyCookies)
        applyKeepScreenOn()
    }

    /** 应用浏览器指纹：在文档开始脚本前注入覆盖脚本 */
    private fun applyFingerprint() {
        fpScriptHandler?.remove()
        fpScriptHandler = null
        fpFallbackScript = ""
        val script = site.fingerprint.buildInjectionScript()
        if (script.isEmpty()) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                fpScriptHandler =
                    WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
            } catch (e: Exception) {
                fpFallbackScript = script
            }
        } else {
            fpFallbackScript = script
        }
    }

    /** 应用/清除进程级代理，就绪后执行 [then]（如加载或刷新页面） */
    private fun applyProxyThen(then: () -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (site.proxy.mode != ProxyCfg.MODE_SYSTEM) {
                Toast.makeText(this, getString(R.string.proxy_unsupported), Toast.LENGTH_LONG).show()
                return
            }
            then()
            return
        }
        val controller = ProxyController.getInstance()
        val executor = ContextCompat.getMainExecutor(this)
        val p = site.proxy
        try {
            if (p.isDirect()) {
                val config = ProxyConfig.Builder().addDirect().build()
                controller.setProxyOverride(config, executor) {
                    proxyApplied = true
                    then()
                }
            } else if (p.isValid()) {
                val config = ProxyConfig.Builder().addProxyRule(p.rule()).build()
                controller.setProxyOverride(config, executor) {
                    proxyApplied = true
                    then()
                }
            } else {
                controller.clearProxyOverride(executor) {
                    proxyApplied = false
                    then()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.proxy_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
    }

    private fun reloadPage() {
        val retryTarget = lastFailedUrl
        pageFailed = false
        lastFailedUrl = null
        applyProxyThen {
            webView.post {
                applyScale()
                if (retryTarget != null) webView.loadUrl(retryTarget) else webView.reload()
            }
        }
    }

    private fun applyKeepScreenOn() {
        if (site.keepScreenOn || customView != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyScale() {
        if (!site.isDesktopLayout() && site.viewportAuto) {
            webView.setInitialScale(0)
            return
        }
        if (site.autoFit) {
            if (webView.width <= 0) return
            val availableDp = webView.width / resources.displayMetrics.density
            webView.setInitialScale(
                ((availableDp / site.viewportWidth.coerceAtLeast(Site.VIEWPORT_MIN)) * 100f).roundToInt().coerceIn(1, 100)
            )
        } else {
            webView.setInitialScale(site.customScale.coerceIn(20, 200))
        }
    }

    private fun applyOrientation() {
        requestedOrientation = when (site.orientation) {
            Site.ORIENT_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            Site.ORIENT_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** 页面级修正：桌面版注入宽视口；手机版按需强制放开缩放限制 */
    private fun injectPageFixes() {
        if (!site.jsEnabled || pageFailed) return
        if (site.injectViewport && (site.isDesktopLayout() || !site.viewportAuto)) {
            injectDesktopViewport()
        } else if (site.forceZoom) {
            injectForceZoom()
        }
    }

    private fun injectDesktopViewport() {
        val width = site.viewportWidth.coerceIn(Site.VIEWPORT_MIN, Site.VIEWPORT_MAX)
        webView.evaluateJavascript(
            """
            (function() {
              var width = $width;
              function apply(doc) {
                if (!doc) return;
                var head = doc.head || doc.getElementsByTagName('head')[0] || doc.documentElement;
                if (head) {
                  var meta = doc.querySelector('meta[name="viewport"]');
                  if (!meta) {
                    meta = doc.createElement('meta');
                    meta.name = 'viewport';
                    head.appendChild(meta);
                  }
                   meta.setAttribute('content', 'width=' + width + ', minimum-scale=0.1, maximum-scale=10, user-scalable=yes, viewport-fit=cover');
                  var style = doc.getElementById('yunchuang-fit-style');
                  if (!style) {
                    style = doc.createElement('style');
                    style.id = 'yunchuang-fit-style';
                    head.appendChild(style);
                  }
                  style.textContent = 'html,body{min-width:' + width + 'px !important;overflow-x:auto !important;}table{max-width:none !important;}';
                }
              }
              function applyAll() {
                apply(document);
                var frames = document.getElementsByTagName('iframe');
                for (var i = 0; i < frames.length; i++) {
                  try { apply(frames[i].contentDocument); } catch (e) {}
                }
              }
              applyAll();
              setTimeout(applyAll, 300);
              setTimeout(applyAll, 1000);
            })();
            """.trimIndent(),
            null
        )
    }

    private fun injectForceZoom() {
        webView.evaluateJavascript(
            """
            (function() {
              function apply(doc) {
                if (!doc) return;
                var head = doc.head || doc.getElementsByTagName('head')[0] || doc.documentElement;
                if (!head) return;
                var meta = doc.querySelector('meta[name="viewport"]');
                if (!meta) {
                  meta = doc.createElement('meta');
                  meta.name = 'viewport';
                  head.appendChild(meta);
                }
                meta.setAttribute('content', 'width=device-width, initial-scale=1, minimum-scale=0.1, maximum-scale=10, user-scalable=yes');
              }
              apply(document);
              setTimeout(function() { apply(document); }, 300);
            })();
            """.trimIndent(),
            null
        )
    }

    // ---------- 底部工具栏 ----------

    private fun setupBottomBar() {
        findViewById<ImageButton>(R.id.btnNavHome).setOnClickListener { goAppHome() }
        btnBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<ImageButton>(R.id.btnNavRefresh).setOnClickListener { refit() }
        findViewById<ImageButton>(R.id.btnNavMore).setOnClickListener { showMoreSheet() }
        findViewById<ImageButton>(R.id.btnNavHide).setOnClickListener { collapseBar() }
        bubble.setOnClickListener { expandBar() }
        updateNavButtons()
    }

    private fun updateNavButtons() {
        val canBack = webView.canGoBack()
        val canForward = webView.canGoForward()
        btnBack.isEnabled = canBack
        btnBack.alpha = if (canBack) 1f else 0.35f
        btnForward.isEnabled = canForward
        btnForward.alpha = if (canForward) 1f else 0.35f
    }

    private fun setAutoHidden(hidden: Boolean) {
        if (autoHidden == hidden) return
        autoHidden = hidden
        val offset = if (hidden) bottomBar.height + Ui.dp(this, 24f).toFloat() else 0f
        bottomBar.animate().translationY(offset).setDuration(200).start()
    }

    private fun collapseBar() {
        manualCollapsed = true
        bottomBar.visibility = View.GONE
        bubble.visibility = if (sessionDismissed) View.GONE else View.VISIBLE
    }

    private fun expandBar() {
        if (sessionDismissed) return
        manualCollapsed = false
        autoHidden = false
        bottomBar.translationY = 0f
        bottomBar.visibility = View.VISIBLE
        bubble.visibility = View.GONE
    }

    /** 浮钮：点击展开；拖动跟手 + 左右吸边；拖到底部销毁区 = 本次浏览不再显示 */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubbleDrag() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        bubble.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startX = v.x
                    startY = v.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        showDismissZone(true)
                    }
                    if (dragging) {
                        val pad = Ui.dp(this, 4f).toFloat()
                        v.x = (startX + dx).coerceIn(pad, (browserRoot.width - v.width - pad).coerceAtLeast(pad))
                        v.y = (startY + dy).coerceIn(pad, (browserRoot.height - v.height - pad).coerceAtLeast(pad))
                        updateDismissHot(isBubbleOverDismiss(v))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val overDismiss = ev.actionMasked == MotionEvent.ACTION_UP &&
                            isBubbleOverDismiss(v)
                        showDismissZone(false)
                        if (overDismiss) {
                            dismissForSession()
                        } else {
                            // 吸附到最近的左右边缘
                            val pad = Ui.dp(this, 10f).toFloat()
                            val targetX = if (v.x + v.width / 2f < browserRoot.width / 2f) pad
                            else browserRoot.width - v.width - pad
                            v.animate().x(targetX).setDuration(180).start()
                        }
                    } else if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showDismissZone(show: Boolean) {
        if (show) {
            dismissHot = false
            dismissZone.alpha = 0f
            dismissZone.visibility = View.VISIBLE
            dismissZone.animate().alpha(1f).setDuration(160).start()
        } else {
            dismissZone.animate().alpha(0f).setDuration(160)
                .withEndAction { dismissZone.visibility = View.GONE }.start()
            updateDismissHot(false)
        }
    }

    private fun isBubbleOverDismiss(v: View): Boolean {
        if (dismissZone.visibility != View.VISIBLE) return false
        val slack = Ui.dp(this, 22f).toFloat()
        val cx = v.x + v.width / 2f
        val cy = v.y + v.height / 2f
        return cx >= dismissZone.x - slack &&
            cx <= dismissZone.x + dismissZone.width + slack &&
            cy >= dismissZone.y - slack &&
            cy <= dismissZone.y + dismissZone.height + slack
    }

    private fun updateDismissHot(hot: Boolean) {
        if (dismissHot == hot) return
        dismissHot = hot
        val scale = if (hot) 1.18f else 1f
        dismissCircle.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        dismissCircle.backgroundTintList =
            if (hot) ColorStateList.valueOf(0xE6E11D48.toInt()) else null
    }

    private fun dismissForSession() {
        sessionDismissed = true
        manualCollapsed = true
        bubble.visibility = View.GONE
        bottomBar.visibility = View.GONE
        Toast.makeText(this, getString(R.string.toolbar_hidden), Toast.LENGTH_LONG).show()
    }

    private fun goAppHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
        )
        finish()
    }

    private fun refit() {
        reloadPage()
    }

    private fun currentUrl(): String = webView.url ?: site.url

    // ---------- 更多操作面板 ----------

    private fun showMoreSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_browser_menu, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.sheetPageTitle).text =
            pageTitle.ifBlank { site.name.ifBlank { site.host() } }
        view.findViewById<TextView>(R.id.sheetPageUrl).text = currentUrl()

        val grid = view.findViewById<GridLayout>(R.id.gridActions)
        grid.columnCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3
        fun add(label: String, icon: Int, action: () -> Unit) {
            grid.addView(actionTile(label, icon) {
                dialog.dismiss()
                action()
            })
        }

        if (site.uaMode == Site.UA_MOBILE) {
            add(getString(R.string.switch_desktop), R.drawable.ic_desktop) { toggleUa() }
        } else {
            add(getString(R.string.switch_mobile), R.drawable.ic_smartphone) { toggleUa() }
        }
        add(orientationLabel(), R.drawable.ic_rotate) { cycleOrientation() }
        add(getString(R.string.label_viewport), R.drawable.ic_ruler) { showViewportDialog() }
        add(getString(R.string.input_url), R.drawable.ic_link) { showUrlDialog() }
        add(getString(R.string.refit), R.drawable.ic_fit) { refit() }
        add(getString(R.string.copy_link), R.drawable.ic_copy) { Ui.copyText(this, currentUrl()) }
        add(getString(R.string.browser_login), R.drawable.ic_open_in_new) { offerBrowserLogin() }
        add(getString(R.string.save_site), R.drawable.ic_add_home) { saveCurrentSite() }
        add(getString(R.string.site_settings), R.drawable.ic_tune) { openEditor() }

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (sheet != null) {
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        Ui.dp(this, 16f) + bars.bottom
                    )
                    insets
                }
                ViewCompat.requestApplyInsets(sheet)
            }
            dialog.window?.let { sheetWindow ->
                WindowCompat.setDecorFitsSystemWindows(sheetWindow, true)
                WindowInsetsControllerCompat(sheetWindow, sheetWindow.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.setOnDismissListener { applyImmersive() }
        dialog.show()
    }

    private fun actionTile(label: String, iconRes: Int, onClick: () -> Unit): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@BrowserActivity, 4f), 0, Ui.dp(this@BrowserActivity, 4f))
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            setBackgroundResource(tv.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val iconSize = Ui.dp(this, 44f)
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this@BrowserActivity, R.color.textPrimary)
            )
            background = ContextCompat.getDrawable(this@BrowserActivity, R.drawable.bg_icon_circle)
            val pad = Ui.dp(this@BrowserActivity, 13f)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
        val text = TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@BrowserActivity, R.color.textSecondary))
        }
        root.addView(
            text,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = Ui.dp(this@BrowserActivity, 4f)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
        root.layoutParams = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f)
        ).apply { width = 0 }
        return root
    }

    /** 浏览中直接调页面宽度：滑杆 + 精确输入 + 预设，应用后立即重载 */
    private fun showViewportDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_viewport, null)
        val slider = view.findViewById<Slider>(R.id.sliderDialogWidth)
        val input = view.findViewById<TextInputEditText>(R.id.edtDialogWidth)
        val chips = view.findViewById<ChipGroup>(R.id.chipsDialogWidth)
        var syncing = false

        fun setValue(width: Int) {
            if (syncing) return
            syncing = true
            val w = width.coerceIn(Site.VIEWPORT_MIN, Site.VIEWPORT_MAX)
            slider.value = w.toFloat()
            input.setText(w.toString())
            syncing = false
        }

        Site.VIEWPORT_PRESETS.forEach { width ->
            chips.addView(Chip(this).apply {
                text = "$width"
                isCheckable = false
                setOnClickListener { setValue(width) }
            })
        }
        slider.setLabelFormatter { "${it.toInt()} px" }
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !syncing) {
                syncing = true
                input.setText(value.toInt().toString())
                syncing = false
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                val value = s?.toString()?.trim()?.toIntOrNull() ?: return
                if (value in Site.VIEWPORT_MIN..Site.VIEWPORT_MAX) {
                    syncing = true
                    slider.value = value.toFloat()
                    syncing = false
                }
            }
        })
        setValue(if (site.uaMode == Site.UA_MOBILE && site.viewportAuto)
            (webView.width / resources.displayMetrics.density).roundToInt() else site.viewportWidth)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.label_viewport))
            .setView(view)
            .setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton(getString(R.string.reset)) { _, _ ->
                site.viewportAuto = true
                site.viewportWidth = 1280
                site.autoFit = true
                if (!isTemp) store.update(site)
                applySiteConfig()
                reloadPage()
            }
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                val width = input.text?.toString()?.trim()?.toIntOrNull()
                    ?: slider.value.toInt()
                site.viewportWidth = width.coerceIn(Site.VIEWPORT_MIN, Site.VIEWPORT_MAX)
                site.viewportAuto = false
                site.injectViewport = true
                applySiteConfig()
                if (!isTemp) store.update(site)
                if (site.reloadOnChange) {
                    refit()
                } else {
                    webView.post { applyScale(); injectPageFixes() }
                }
            }
            .show()
    }

    private fun toggleUa() {
        site.uaMode = if (site.uaMode == Site.UA_MOBILE) Site.UA_DESKTOP else Site.UA_MOBILE
        if (!isTemp) store.update(site)
        applySiteConfig()
        // Reload removes the previous mode's injected viewport and CSS.
        reloadPage()
        Toast.makeText(this, getString(R.string.mode_switched, site.modeLabel(this)), Toast.LENGTH_SHORT).show()
    }

    private fun orientationLabel(): String = when (site.orientation) {
        Site.ORIENT_LANDSCAPE -> getString(R.string.direction_landscape)
        Site.ORIENT_PORTRAIT -> getString(R.string.direction_portrait)
        else -> getString(R.string.direction_follow)
    }

    private fun cycleOrientation() {
        site.orientation = when (site.orientation) {
            Site.ORIENT_FOLLOW -> Site.ORIENT_LANDSCAPE
            Site.ORIENT_LANDSCAPE -> Site.ORIENT_PORTRAIT
            else -> Site.ORIENT_FOLLOW
        }
        if (!isTemp) store.update(site)
        applyOrientation()
        Toast.makeText(this, orientationLabel().replace("·", "："), Toast.LENGTH_SHORT).show()
    }

    private fun openInBrowserForLogin(url: String = currentUrl()) {
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (ignored: Exception) {
                Toast.makeText(this, getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun offerBrowserLogin() {
        if (loginPromptVisible) return
        loginPromptVisible = true
        // Start the website's login flow in the browser so its OAuth state cookie is present.
        val target = currentUrl().takeUnless { isGoogleAuthUrl(it) } ?: site.url
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.login_in_browser))
            .setMessage(getString(R.string.browser_login_message))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.open_site)) { _, _ -> openInBrowserForLogin(target) }
            .setOnDismissListener { loginPromptVisible = false }
            .show()
    }

    private fun isGoogleAuthUrl(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = parsed.host.orEmpty().lowercase()
        return host == "accounts.google.com" &&
            (url.contains("oauth", ignoreCase = true) ||
                url.contains("signin", ignoreCase = true) ||
                url.contains("ServiceLogin", ignoreCase = true))
    }

    private fun openExternalScheme(url: String) {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.external_app_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTempSite() {
        if (site.name.isBlank()) site.name = pageTitle.trim()
        store.add(site)
        isTemp = false
        updateTaskDescription()
        Toast.makeText(this, getString(R.string.saved_home), Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentSite() {
        val url = Site.normalizeUrl(currentUrl())
        if (url.isBlank() || pageFailed) {
            Toast.makeText(this, getString(R.string.no_url_to_save), Toast.LENGTH_SHORT).show()
            return
        }
        if (isTemp) {
            site.url = url
            site.name = pageTitle.trim()
            saveTempSite()
            return
        }

        if (url == site.url) {
            if (site.name.isBlank() && pageTitle.isNotBlank()) site.name = pageTitle.trim()
            store.update(site)
            Toast.makeText(this, getString(R.string.saved_yunchuang), Toast.LENGTH_SHORT).show()
            return
        }

        val existing = store.list().firstOrNull { it.url == url }
        if (existing != null) {
            Toast.makeText(this, getString(R.string.already_saved), Toast.LENGTH_SHORT).show()
            return
        }
        val saved = site.copy(
            id = UUID.randomUUID().toString(),
            name = pageTitle.trim(),
            url = url,
            createdAt = System.currentTimeMillis()
        )
        store.add(saved)
        Toast.makeText(this, getString(R.string.saved_yunchuang), Toast.LENGTH_SHORT).show()
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(currentUrl())
            selectAll()
            hint = "https://example.com"
        }
        val pad = Ui.dp(this, 22f)
        val container = FrameLayout(this).apply {
            setPadding(pad, Ui.dp(this@BrowserActivity, 4f), pad, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.input_url))
            .setView(container)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.open)) { _, _ ->
                val url = Site.normalizeUrl(input.text?.toString().orEmpty())
                if (url.isBlank()) {
                    Toast.makeText(this, getString(R.string.enter_url), Toast.LENGTH_SHORT).show()
                } else {
                    lastFailedUrl = null
                    webView.loadUrl(url)
                }
            }
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }

    private fun openEditor() {
        editorLauncher.launch(
            Intent(this, SiteEditorActivity::class.java)
                .putExtra(SiteEditorActivity.EXTRA_SITE_ID, site.id)
                .putExtra(SiteEditorActivity.EXTRA_DRAFT, site.toJson().toString())
        )
    }

    // ---------- 下载 ----------

    private fun handleDownload(url: String, ua: String, contentDisposition: String?, mime: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(this, getString(R.string.unsupported_download), Toast.LENGTH_SHORT).show()
            return
        }
        val req = DownloadReq(url, ua, contentDisposition, mime)
        val needPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needPermission) {
            pendingDownload = req
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueueDownload(req)
        }
    }

    private fun enqueueDownload(req: DownloadReq) {
        try {
            val fileName = URLUtil.guessFileName(req.url, req.contentDisposition, req.mime)
            val request = DownloadManager.Request(Uri.parse(req.url)).apply {
                setMimeType(req.mime)
                addRequestHeader("User-Agent", req.ua)
                CookieManager.getInstance().getCookie(req.url)?.let {
                    addRequestHeader("Cookie", it)
                }
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, getString(R.string.download_started, fileName), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.download_failed, e.message.orEmpty()), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- HTTP 认证 ----------

    private fun showHttpAuthDialog(handler: HttpAuthHandler, host: String?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(this@BrowserActivity, 22f)
            setPadding(pad, Ui.dp(this@BrowserActivity, 8f), pad, 0)
        }
        val user = EditText(this).apply { hint = getString(R.string.username) }
        val pass = EditText(this).apply {
            hint = getString(R.string.password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(user)
        container.addView(pass)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.login_required))
            .setMessage(host.orEmpty())
            .setView(container)
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> handler.cancel() }
            .setPositiveButton(getString(R.string.login)) { _, _ ->
                handler.proceed(user.text.toString(), pass.text.toString())
            }
            .setOnCancelListener { handler.cancel() }
            .show()
    }

    // ---------- 错误页 ----------

    private fun showErrorPage(failedUrl: String, description: String) {
        pageFailed = true
        lastFailedUrl = failedUrl
        val host = try {
            Uri.parse(failedUrl).host ?: failedUrl
        } catch (e: Exception) {
            failedUrl
        }
        webView.setInitialScale(0)
        val html = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            :root{color-scheme:light dark}
            body{margin:0;font-family:system-ui,-apple-system,'Segoe UI',Roboto,'PingFang SC','Microsoft YaHei',sans-serif;
                 min-height:100vh;display:flex;align-items:center;justify-content:center;
                 background:#f5f6fb;color:#17181d}
            .card{background:#fff;border-radius:24px;padding:44px 32px;max-width:400px;margin:24px;
                  text-align:center;box-shadow:0 12px 44px rgba(23,24,29,.09)}
            @media(prefers-color-scheme:dark){body{background:#0e0f14;color:#ecedf3}
                  .card{background:#1a1b23;box-shadow:none}}
            .icon{width:74px;height:74px;border-radius:23px;margin:0 auto 22px;
                  background:linear-gradient(135deg,#6366f1,#22d3ee);
                  display:flex;align-items:center;justify-content:center;font-size:34px}
            h1{font-size:20px;margin:0 0 10px;font-weight:700}
            p{font-size:13px;line-height:1.8;opacity:.62;margin:0;word-break:break-all}
            a.btn{display:inline-block;margin-top:26px;padding:13px 46px;border-radius:999px;
                  background:linear-gradient(135deg,#6366f1,#22d3ee);color:#fff;
                  text-decoration:none;font-size:15px;font-weight:600}
            </style></head><body><div class="card">
            <div class="icon">&#9729;</div>
            <h1>${android.text.TextUtils.htmlEncode(getString(R.string.error_page_title))}</h1>
            <p>${android.text.TextUtils.htmlEncode(host)}</p><p>${android.text.TextUtils.htmlEncode(description)}</p>
            <a class="btn" href="$RETRY_URL">${android.text.TextUtils.htmlEncode(getString(R.string.retry))}</a>
            </div></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(failedUrl, html, "text/html", "utf-8", failedUrl)
    }

    // ---------- 返回与生命周期 ----------

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> webView.webChromeClient?.onHideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webView.postDelayed({
            if (isDestroyed || isFinishing) return@postDelayed
            applyScale()
            injectPageFixes()
            // 旋转后把浮钮拉回屏幕范围内
            if (bubble.visibility == View.VISIBLE && browserRoot.width > 0) {
                val pad = Ui.dp(this, 10f).toFloat()
                bubble.x = bubble.x.coerceIn(pad, (browserRoot.width - bubble.width - pad).coerceAtLeast(pad))
                bubble.y = bubble.y.coerceIn(pad, (browserRoot.height - bubble.height - pad).coerceAtLeast(pad))
            }
        }, 250)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        if (proxyApplied && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyController.getInstance().clearProxyOverride({ it.run() }, {})
            } catch (e: Exception) {
                // 忽略：进程退出时清理失败无影响
            }
        }
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SITE_ID = "site_id"
        const val EXTRA_URL = "url"
        private const val RETRY_URL = "yunchuang://retry"
    }
}
