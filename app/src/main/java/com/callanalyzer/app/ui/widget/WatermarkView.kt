package com.callanalyzer.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt

/**
 * 水印 View
 * - 半透明文字
 * - 倾斜 -15°
 * - 重复平铺
 * - 水印内容随查询条件同步更新
 */
class WatermarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var watermarkText: String = ""

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33888888")   // 灰色，透明度约20%
        textSize = 44f
        isFakeBoldText = false
        isAntiAlias = true
    }

    init {
        // 不拦截触摸事件，允许下层 View 响应
        isClickable = false
        isFocusable = false
    }

    /**
     * 更新水印内容，自动重绘
     */
    fun setWatermark(text: String) {
        if (watermarkText != text) {
            watermarkText = text
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (watermarkText.isBlank()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // 旋转画布 -15 度
        canvas.save()
        canvas.rotate(-15f, w / 2, h / 2)

        val textWidth = paint.measureText(watermarkText)
        val textHeight = paint.textSize

        // 计算平铺步长（加间距）
        val stepX = textWidth + 120f
        val stepY = textHeight + 100f

        // 扩大绘制范围以覆盖旋转后的空白
        val extra = sqrt((w * w + h * h).toDouble()).toFloat()
        val startX = -(extra - w) / 2 - stepX
        val startY = -(extra - h) / 2 - stepY

        var y = startY
        var rowIndex = 0
        while (y < h + extra) {
            var x = startX + if (rowIndex % 2 == 0) 0f else stepX / 2
            while (x < w + extra) {
                canvas.drawText(watermarkText, x, y, paint)
                x += stepX
            }
            y += stepY
            rowIndex++
        }

        canvas.restore()
    }
}
