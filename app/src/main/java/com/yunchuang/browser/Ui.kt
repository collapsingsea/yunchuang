package com.yunchuang.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import android.widget.Toast

/**
 * 视觉工具：站点磁贴的渐变配色、单字图标、标题渐变等。
 */
object Ui {

    /** 精选渐变配色（左上 → 右下），白字可读 */
    private val GRADIENTS = arrayOf(
        longArrayOf(0xFF6366F1, 0xFFA855F7), // 靛紫
        longArrayOf(0xFF0284C7, 0xFF06B6D4), // 碧海
        longArrayOf(0xFF059669, 0xFF14B8A6), // 青翠
        longArrayOf(0xFFEA580C, 0xFFF59E0B), // 落日
        longArrayOf(0xFFE11D48, 0xFFFB7185), // 蔷薇
        longArrayOf(0xFF7C3AED, 0xFFC084FC), // 葡萄
        longArrayOf(0xFF334155, 0xFF64748B), // 石墨
        longArrayOf(0xFFDB2777, 0xFFF472B6), // 樱粉
        longArrayOf(0xFF2563EB, 0xFF60A5FA)  // 天青
    )

    val gradientCount: Int get() = GRADIENTS.size

    private fun colorsAt(index: Int): IntArray {
        val pair = GRADIENTS[Math.floorMod(index, GRADIENTS.size)]
        return intArrayOf(pair[0].toInt(), pair[1].toInt())
    }

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    /** 站点磁贴背景 */
    fun tileDrawable(context: Context, colorIndex: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colorsAt(colorIndex)).apply {
            cornerRadius = dp(context, radiusDp).toFloat()
        }
    }

    /** 磁贴上的单字：取名称第一个字符 */
    fun monogram(name: String): String {
        val ch = name.trim().firstOrNull() ?: '网'
        return ch.uppercaseChar().toString()
    }

    fun bindTile(view: TextView, site: Site, radiusDp: Float) {
        view.background = tileDrawable(view.context, site.colorIndex, radiusDp)
        view.text = monogram(site.name.ifBlank { site.host() })
    }

    /** 生成用于桌面快捷方式的全出血位图（adaptive icon 会自动裁形） */
    fun tileBitmap(site: Site, sizePx: Int = 324): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colors = colorsAt(site.colorIndex)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                colors[0], colors[1], Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = sizePx * 0.34f
            textAlign = Paint.Align.CENTER
        }
        val baseline = sizePx / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(monogram(site.name.ifBlank { site.host() }), sizePx / 2f, baseline, textPaint)
        return bmp
    }

    /** 首页标题的品牌渐变 */
    fun applyTitleGradient(view: TextView, startColor: Int, endColor: Int) {
        val width = view.paint.measureText(view.text.toString())
        if (width <= 0f) return
        view.paint.shader =
            LinearGradient(0f, 0f, width, 0f, startColor, endColor, Shader.TileMode.CLAMP)
        view.invalidate()
    }

    fun copyText(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", text))
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_link)))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
