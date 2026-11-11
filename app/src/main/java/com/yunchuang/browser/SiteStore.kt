package com.yunchuang.browser

import android.content.Context
import org.json.JSONArray

/**
 * 站点仓库：JSON 序列化后存入 SharedPreferences，列表顺序即展示顺序。
 */
class SiteStore(private val context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("yunchuang_prefs", Context.MODE_PRIVATE)

    fun list(): MutableList<Site> {
        val raw = prefs.getString(KEY_SITES, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { Site.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun get(id: String?): Site? {
        if (id.isNullOrBlank()) return null
        return list().firstOrNull { it.id == id }
    }

    fun add(site: Site) {
        val all = list()
        all.add(site)
        save(all)
    }

    fun update(site: Site) {
        val all = list()
        val index = all.indexOfFirst { it.id == site.id }
        if (index >= 0) {
            all[index] = site
            save(all)
        } else {
            add(site)
        }
    }

    fun delete(id: String) {
        val all = list()
        all.removeAll { it.id == id }
        save(all)
    }

    fun moveToTop(id: String) {
        val all = list()
        val index = all.indexOfFirst { it.id == id }
        if (index > 0) {
            val site = all.removeAt(index)
            all.add(0, site)
            save(all)
        }
    }

    /** 保存拖动排序后的完整顺序。 */
    fun replaceOrder(sites: List<Site>) {
        val current = list()
        val byId = current.associateBy { it.id }
        val ordered = sites.mapNotNull { byId[it.id] }.distinctBy { it.id }
        val ids = ordered.map { it.id }.toSet()
        save(ordered + current.filter { it.id !in ids })
    }

    private fun save(sites: List<Site>) {
        val arr = JSONArray()
        sites.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SITES, arr.toString()).apply()
    }

    /** 首次启动时预置示例站点，展示两种显示模式 */
    fun seedIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        if (list().isNotEmpty()) return
        save(
            listOf(
                Site(
                    name = context.getString(R.string.seed_bilibili),
                    url = "https://www.bilibili.com/",
                    colorIndex = 0,
                    uaMode = Site.UA_DESKTOP,
                    viewportWidth = 1280,
                    orientation = Site.ORIENT_LANDSCAPE
                ),
                Site(
                    name = context.getString(R.string.seed_youtube),
                    url = "https://www.youtube.com/",
                    colorIndex = 8,
                    uaMode = Site.UA_MOBILE,
                    viewportAuto = true,
                    orientation = Site.ORIENT_LANDSCAPE
                )
            )
        )
    }

    companion object {
        private const val KEY_SITES = "sites_v1"
        private const val KEY_SEEDED = "seeded_v1"
    }
}
