package com.yunchuang.browser

import android.net.Uri
import org.json.JSONObject
import java.util.UUID

/**
 * 一个站点的全部配置。每个站点都可以有自己独立的 UA、页面宽度、缩放与方向策略，
 * 以及 JavaScript / 图片 / Cookie / 缩放限制 等专业选项。
 */
data class Site(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var url: String = "",
    var colorIndex: Int = 0,
    var uaMode: Int = UA_DESKTOP,
    var customUa: String = "",
    var viewportWidth: Int = 1280,
    /** 手机版默认使用 WebView 的设备宽度；关闭自动后才使用 viewportWidth。 */
    var viewportAuto: Boolean = true,
    var autoFit: Boolean = true,
    var customScale: Int = 100,
    var orientation: Int = ORIENT_LANDSCAPE,
    var injectViewport: Boolean = true,
    var textZoom: Int = 100,
    var jsEnabled: Boolean = true,
    var loadImages: Boolean = true,
    var thirdPartyCookies: Boolean = true,
    var forceZoom: Boolean = false,
    var keepScreenOn: Boolean = false,
    var reloadOnChange: Boolean = true,
    var fingerprint: Fingerprint = Fingerprint(),
    var proxy: ProxyCfg = ProxyCfg(),
    val createdAt: Long = System.currentTimeMillis()
) {

    fun host(): String {
        return try {
            Uri.parse(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }

    fun resolvedUa(): String = when (uaMode) {
        UA_MOBILE -> MOBILE_UA
        UA_CUSTOM -> customUa.ifBlank { DESKTOP_UA }
        else -> DESKTOP_UA
    }

    /** 手机版页面按响应式渲染；桌面版/自定义按宽视口 + 缩放渲染 */
    fun isDesktopLayout(): Boolean = uaMode != UA_MOBILE

    fun modeLabel(context: android.content.Context): String = when (uaMode) {
        UA_MOBILE -> context.getString(R.string.mode_mobile)
        UA_CUSTOM -> context.getString(R.string.mode_custom)
        else -> context.getString(R.string.mode_desktop)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("color", colorIndex)
        .put("ua", uaMode)
        .put("cua", customUa)
        .put("vw", viewportWidth)
        .put("vauto", viewportAuto)
        .put("fit", autoFit)
        .put("scale", customScale)
        .put("orient", orientation)
        .put("inject", injectViewport)
        .put("tz", textZoom)
        .put("js", jsEnabled)
        .put("img", loadImages)
        .put("tpc", thirdPartyCookies)
        .put("fz", forceZoom)
        .put("kso", keepScreenOn)
        .put("roc", reloadOnChange)
        .put("fp", fingerprint.toJson())
        .put("proxy", proxy.toJson())
        .put("created", createdAt)

    companion object {
        const val UA_DESKTOP = 0
        const val UA_MOBILE = 1
        const val UA_CUSTOM = 2

        const val ORIENT_FOLLOW = 0
        const val ORIENT_PORTRAIT = 1
        const val ORIENT_LANDSCAPE = 2

        const val VIEWPORT_MIN = 320
        const val VIEWPORT_MAX = 2560

        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"

        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        /** 自定义 UA 的快速预设 */
        val UA_PRESETS: List<Pair<String, String>> = listOf(
            "Windows Chrome" to DESKTOP_UA,
            "Windows Edge" to (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
                ),
            "macOS Safari" to (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                    "Version/17.4 Safari/605.1.15"
                ),
            "iPad" to (
                "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                    "Version/17.4 Mobile/15E148 Safari/604.1"
                ),
            "Android 平板" to (
                "Mozilla/5.0 (Linux; Android 14; SM-X910) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36"
                ),
            "微信内置" to (
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36 " +
                    "MicroMessenger/8.0.47.2560(0x28002F35) XWEB/1160"
                )
        )

        /** 常用视口宽度预设 */
        val VIEWPORT_PRESETS = intArrayOf(980, 1024, 1280, 1366, 1440, 1600, 1920)

        fun fromJson(o: JSONObject): Site = Site(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", ""),
            url = o.optString("url", ""),
            colorIndex = o.optInt("color", 0),
            uaMode = o.optInt("ua", UA_DESKTOP),
            customUa = o.optString("cua", ""),
            viewportWidth = o.optInt("vw", 1280),
            viewportAuto = o.optBoolean("vauto", true),
            autoFit = o.optBoolean("fit", true),
            customScale = o.optInt("scale", 100),
            orientation = o.optInt("orient", ORIENT_LANDSCAPE),
            injectViewport = o.optBoolean("inject", true),
            textZoom = o.optInt("tz", 100),
            jsEnabled = o.optBoolean("js", true),
            loadImages = o.optBoolean("img", true),
            thirdPartyCookies = o.optBoolean("tpc", true),
            forceZoom = o.optBoolean("fz", false),
            keepScreenOn = o.optBoolean("kso", false),
            reloadOnChange = o.optBoolean("roc", true),
            fingerprint = Fingerprint.fromJson(o.optJSONObject("fp")),
            proxy = ProxyCfg.fromJson(o.optJSONObject("proxy")),
            createdAt = o.optLong("created", 0L)
        )

        /** 统一补协议：无 http/https 前缀时默认 https:// */
        fun normalizeUrl(raw: String): String {
            val value = raw.trim()
            if (value.isBlank() || value.any { it.isWhitespace() }) return ""
            val candidate = if (value.contains("://")) value else "https://$value"
            val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return ""
            if (uri.scheme?.lowercase() !in listOf("http", "https") || uri.host.isNullOrBlank()) return ""
            return uri.normalizeScheme().toString()
        }
    }
}
