package com.callstats.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * 水印容器 - 用于显示统计界面的水印
 * 水印特性：
 * - 透明度50%
 * - 倾斜45度
 * - 与背景有反差，清晰可见
 * - 显示选定的统计时间段
 */
class WatermarkContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var watermarkText: String = ""
    private val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 透明度50%（0x80 = 128，约50%）
        color = Color.parseColor("#80333333")  // 深灰色，透明度50%
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    init {
        // 添加一个自定义View来绘制水印
        addView(object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (watermarkText.isNotEmpty()) {
                    drawWatermark(canvas)
                }
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * 设置水印文本
     */
    fun setWatermarkText(text: String) {
        watermarkText = text
        // 重绘所有子View
        for (i in 0 until childCount) {
            getChildAt(i).invalidate()
        }
    }

    /**
     * 获取水印文本
     */
    fun getWatermarkText(): String = watermarkText

    /**
     * 绘制倾斜45度的重复水印
     */
    private fun drawWatermark(canvas: Canvas) {
        val text = watermarkText
        val textWidth = watermarkPaint.measureText(text)
        val textHeight = watermarkPaint.textSize

        // 保存画布状态
        canvas.save()

        // 旋转画布45度
        canvas.rotate(-45f, width / 2f, height / 2f)

        // 计算水印间距
        val spacingX = (textWidth + 150).toInt()
        val spacingY = (textHeight * 4).toInt()

        // 计算绘制范围（需要扩大以覆盖旋转后的区域）
        val diagonal = Math.sqrt((width * width + height * height).toDouble()).toInt()

        // 绘制重复水印
        var x = -diagonal
        var y = -diagonal

        while (y < diagonal * 2) {
            while (x < diagonal * 2) {
                // 绘制水印文字
                canvas.drawText(text, x.toFloat(), y.toFloat(), watermarkPaint)
                x += spacingX
            }
            x = -diagonal
            y += spacingY
        }

        // 恢复画布状态
        canvas.restore()
    }
}
