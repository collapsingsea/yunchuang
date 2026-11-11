package com.yunchuang.browser

import org.json.JSONArray
import org.json.JSONObject

/**
 * 浏览器指纹配置。启用后会在页面自身脚本执行之前，注入一段 JS 覆盖掉
 * 时区、平台、语言、屏幕、CPU/内存、WebGL、Canvas 等可被用于追踪的特征，
 * 使不同站点/账号呈现互相独立且稳定的"设备身份"。
 */
data class Fingerprint(
    var enabled: Boolean = false,
    /** 预设下标；等于 [CUSTOM] 时表示用户自定义 */
    var preset: Int = 0,
    var platform: String = "Win32",
    var vendor: String = "Google Inc.",
    var language: String = "zh-CN",
    var languages: String = "zh-CN,zh,en",
    var timezone: String = "Asia/Shanghai",
    /** getTimezoneOffset() 约定：UTC+8 记为 -480 */
    var tzOffset: Int = -480,
    var screenW: Int = 1920,
    var screenH: Int = 1080,
    var dpr: Double = 1.0,
    var cores: Int = 8,
    var memory: Int = 8,
    var glVendor: String = "Google Inc. (NVIDIA)",
    var glRenderer: String = "ANGLE (NVIDIA, NVIDIA GeForce GTX 1660 Direct3D11 vs_5_0 ps_5_0, D3D11)",
    var canvasNoise: Boolean = true,
    var touch: Boolean = false
) {

    fun presetLabel(context: android.content.Context): String =
        if (preset == CUSTOM || preset !in PRESET_LABELS.indices) context.getString(R.string.mode_custom) else PRESET_LABELS[preset]

    fun summary(context: android.content.Context): String = if (!enabled) context.getString(R.string.fp_off) else context.getString(R.string.fp_enabled_summary, presetLabel(context))

    /** Canvas 加噪种子：由稳定特征派生，保证同一身份每次读出一致的"假指纹" */
    private fun canvasSeed(): Int = ((platform + timezone + glRenderer).hashCode() and 0xff)

    fun toJson(): JSONObject = JSONObject()
        .put("en", enabled)
        .put("ps", preset)
        .put("plat", platform)
        .put("ven", vendor)
        .put("lang", language)
        .put("langs", languages)
        .put("tz", timezone)
        .put("tzo", tzOffset)
        .put("sw", screenW)
        .put("sh", screenH)
        .put("dpr", dpr)
        .put("cores", cores)
        .put("mem", memory)
        .put("glv", glVendor)
        .put("glr", glRenderer)
        .put("cn", canvasNoise)
        .put("touch", touch)

    /** 生成在文档开始时注入的覆盖脚本；未启用时返回空串 */
    fun buildInjectionScript(): String {
        if (!enabled) return ""
        fun q(s: String) = JSONObject.quote(s)
        val langsArr = languages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val langsJson = JSONArray(langsArr).toString()
        val dprStr = if (dpr % 1.0 == 0.0) dpr.toInt().toString() else dpr.toString()
        val touchPoints = if (touch) 5 else 0

        val canvas = if (canvasNoise) {
            """
            try{
              var __seed=${canvasSeed()};
              var __tdu=HTMLCanvasElement.prototype.toDataURL;
              HTMLCanvasElement.prototype.toDataURL=function(){
                try{var c=this.getContext('2d');if(c){var w=this.width,h=this.height;
                  if(w>0&&h>0){var d=c.getImageData(0,0,w,h);
                    for(var i=0;i<d.data.length;i+=1499){d.data[i]=d.data[i]^(__seed&0xff);}
                    c.putImageData(d,0,0);}}}catch(e){}
                return __tdu.apply(this,arguments);
              };
              var __gid=CanvasRenderingContext2D.prototype.getImageData;
              CanvasRenderingContext2D.prototype.getImageData=function(){
                var r=__gid.apply(this,arguments);
                try{for(var i=0;i<r.data.length;i+=1499){r.data[i]=r.data[i]^(__seed&0xff);}}catch(e){}
                return r;
              };
            }catch(e){}
            """.trimIndent()
        } else ""

        return """
        (function(){'use strict';
        function def(o,p,v){try{Object.defineProperty(o,p,{get:function(){return v;},configurable:true});}catch(e){}}
        try{
          def(navigator,'platform',${q(platform)});
          def(navigator,'vendor',${q(vendor)});
          def(navigator,'language',${q(language)});
          def(navigator,'languages',Object.freeze($langsJson));
          def(navigator,'hardwareConcurrency',$cores);
          def(navigator,'deviceMemory',$memory);
          def(navigator,'webdriver',false);
          def(navigator,'maxTouchPoints',$touchPoints);
        }catch(e){}
        try{
          def(screen,'width',$screenW);def(screen,'height',$screenH);
          def(screen,'availWidth',$screenW);def(screen,'availHeight',$screenH);
          def(screen,'colorDepth',24);def(screen,'pixelDepth',24);
          def(window,'devicePixelRatio',$dprStr);
        }catch(e){}
        var __tz=${q(timezone)},__off=$tzOffset;
        try{var D=Intl.DateTimeFormat;function P(l,o){o=o||{};if(o.timeZone===undefined){o.timeZone=__tz;}return new D(l,o);}
          P.prototype=D.prototype;P.supportedLocalesOf=D.supportedLocalesOf;Intl.DateTimeFormat=P;}catch(e){}
        try{Date.prototype.getTimezoneOffset=function(){return __off;};}catch(e){}
        try{
          function pg(proto){if(!proto)return;var gp=proto.getParameter;
            proto.getParameter=function(p){if(p===37445)return ${q(glVendor)};if(p===37446)return ${q(glRenderer)};return gp.call(this,p);};}
          pg(window.WebGLRenderingContext&&WebGLRenderingContext.prototype);
          pg(window.WebGL2RenderingContext&&WebGL2RenderingContext.prototype);
        }catch(e){}
        $canvas
        })();
        """.trimIndent()
    }

    companion object {
        const val CUSTOM = -1

        val PRESET_LABELS = listOf(
            "Windows · Chrome",
            "macOS · Safari",
            "iPhone · Safari",
            "Android · Chrome",
            "Linux · Firefox"
        )

        /** 时区显示名 → (IANA, getTimezoneOffset 分钟) */
        val TIMEZONES: List<Triple<String, String, Int>> = listOf(
            Triple("UTC+8 北京/香港/新加坡", "Asia/Shanghai", -480),
            Triple("UTC+9 东京/首尔", "Asia/Tokyo", -540),
            Triple("UTC+7 曼谷/雅加达", "Asia/Bangkok", -420),
            Triple("UTC+5:30 印度", "Asia/Kolkata", -330),
            Triple("UTC+4 迪拜", "Asia/Dubai", -240),
            Triple("UTC+3 莫斯科", "Europe/Moscow", -180),
            Triple("UTC+1 柏林/巴黎", "Europe/Berlin", -60),
            Triple("UTC+0 伦敦", "Europe/London", 0),
            Triple("UTC-3 圣保罗", "America/Sao_Paulo", 180),
            Triple("UTC-5 纽约/多伦多", "America/New_York", 300),
            Triple("UTC-6 芝加哥", "America/Chicago", 360),
            Triple("UTC-8 洛杉矶/西雅图", "America/Los_Angeles", 480),
            Triple("UTC+10 悉尼", "Australia/Sydney", -600)
        )

        val PLATFORMS = listOf("Win32", "MacIntel", "Linux x86_64", "Linux armv8l", "iPhone", "iPad")

        fun preset(index: Int): Fingerprint = when (index) {
            1 -> Fingerprint(
                enabled = true, preset = 1,
                platform = "MacIntel", vendor = "Apple Computer, Inc.",
                language = "en-US", languages = "en-US,en",
                timezone = "America/New_York", tzOffset = 300,
                screenW = 2560, screenH = 1440, dpr = 2.0, cores = 10, memory = 8,
                glVendor = "Apple", glRenderer = "Apple M1 Pro", canvasNoise = true, touch = false
            )
            2 -> Fingerprint(
                enabled = true, preset = 2,
                platform = "iPhone", vendor = "Apple Computer, Inc.",
                language = "en-US", languages = "en-US,en",
                timezone = "America/Los_Angeles", tzOffset = 480,
                screenW = 390, screenH = 844, dpr = 3.0, cores = 6, memory = 4,
                glVendor = "Apple GPU", glRenderer = "Apple GPU", canvasNoise = true, touch = true
            )
            3 -> Fingerprint(
                enabled = true, preset = 3,
                platform = "Linux armv8l", vendor = "Google Inc.",
                language = "zh-CN", languages = "zh-CN,zh,en",
                timezone = "Asia/Shanghai", tzOffset = -480,
                screenW = 393, screenH = 873, dpr = 2.75, cores = 8, memory = 8,
                glVendor = "Qualcomm", glRenderer = "Adreno (TM) 730", canvasNoise = true, touch = true
            )
            4 -> Fingerprint(
                enabled = true, preset = 4,
                platform = "Linux x86_64", vendor = "",
                language = "en-US", languages = "en-US,en",
                timezone = "Europe/London", tzOffset = 0,
                screenW = 1920, screenH = 1080, dpr = 1.0, cores = 12, memory = 16,
                glVendor = "Mesa", glRenderer = "llvmpipe (LLVM 15.0.7, 256 bits)",
                canvasNoise = true, touch = false
            )
            else -> Fingerprint(
                enabled = true, preset = 0,
                platform = "Win32", vendor = "Google Inc.",
                language = "zh-CN", languages = "zh-CN,zh,en",
                timezone = "Asia/Shanghai", tzOffset = -480,
                screenW = 1920, screenH = 1080, dpr = 1.0, cores = 8, memory = 8,
                glVendor = "Google Inc. (NVIDIA)",
                glRenderer = "ANGLE (NVIDIA, NVIDIA GeForce GTX 1660 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                canvasNoise = true, touch = false
            )
        }

        fun fromJson(o: JSONObject?): Fingerprint {
            if (o == null) return Fingerprint()
            return Fingerprint(
                enabled = o.optBoolean("en", false),
                preset = o.optInt("ps", 0),
                platform = o.optString("plat", "Win32"),
                vendor = o.optString("ven", "Google Inc."),
                language = o.optString("lang", "zh-CN"),
                languages = o.optString("langs", "zh-CN,zh,en"),
                timezone = o.optString("tz", "Asia/Shanghai"),
                tzOffset = o.optInt("tzo", -480),
                screenW = o.optInt("sw", 1920),
                screenH = o.optInt("sh", 1080),
                dpr = o.optDouble("dpr", 1.0),
                cores = o.optInt("cores", 8),
                memory = o.optInt("mem", 8),
                glVendor = o.optString("glv", "Google Inc. (NVIDIA)"),
                glRenderer = o.optString("glr", ""),
                canvasNoise = o.optBoolean("cn", true),
                touch = o.optBoolean("touch", false)
            )
        }
    }
}

/**
 * 代理服务器配置。HTTP 代理支持账号密码鉴权（通过 407 挑战回填）；
 * SOCKS5 仅支持免密（WebView 限制）。
 */
data class ProxyCfg(
    /** SYSTEM follows Android/Clash settings, DIRECT bypasses system proxy, CUSTOM uses the fields below. */
    var mode: Int = MODE_SYSTEM,
    /** Kept for JSON/source compatibility with the previous version. */
    var enabled: Boolean = false,
    var type: Int = TYPE_HTTP,
    var host: String = "",
    var port: Int = 0,
    var username: String = "",
    var password: String = ""
) {

    fun isDirect(): Boolean = mode == MODE_DIRECT

    fun isCustom(): Boolean = mode == MODE_CUSTOM

    fun isValid(): Boolean = isCustom() && enabled && host.isNotBlank() && port in 1..65535

    /** ProxyController 规则，例如 http://1.2.3.4:8080 或 socks5://1.2.3.4:1080 */
    fun rule(): String {
        val scheme = if (type == TYPE_SOCKS) "socks5" else "http"
        return "$scheme://$host:$port"
    }

    fun summary(context: android.content.Context): String = when {
        isDirect() -> context.getString(R.string.proxy_direct_summary)
        isValid() -> "${typeLabel()} · $host:$port"
        else -> context.getString(R.string.proxy_system_summary)
    }

    fun typeLabel(): String = if (type == TYPE_SOCKS) "SOCKS5" else "HTTP"

    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode)
        .put("en", enabled)
        .put("type", type)
        .put("host", host)
        .put("port", port)
        .put("user", username)
        .put("pass", password)

    companion object {
        const val TYPE_HTTP = 0
        const val TYPE_SOCKS = 1
        const val MODE_SYSTEM = 0
        const val MODE_DIRECT = 1
        const val MODE_CUSTOM = 2

        fun fromJson(o: JSONObject?): ProxyCfg {
            if (o == null) return ProxyCfg()
            val oldEnabled = o.optBoolean("en", false)
            val rawMode = o.optInt("mode", if (oldEnabled) MODE_CUSTOM else MODE_SYSTEM)
            val mode = rawMode.coerceIn(MODE_SYSTEM, MODE_CUSTOM)
            return ProxyCfg(
                mode = mode,
                enabled = mode == MODE_CUSTOM && oldEnabled,
                type = o.optInt("type", TYPE_HTTP),
                host = o.optString("host", ""),
                port = o.optInt("port", 0),
                username = o.optString("user", ""),
                password = o.optString("pass", "")
            )
        }
    }
}
