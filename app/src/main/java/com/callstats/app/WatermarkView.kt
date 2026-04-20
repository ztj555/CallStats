package com.callstats.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * 单独的水印View组件
 * 可以嵌入到其他布局中使用
 */
class WatermarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var watermarkText: String = ""
    private val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 透明度50%（0x80 = 128，约50%）
        color = Color.parseColor("#80333333")  // 深灰色，透明度50%
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    /**
     * 设置水印文本
     */
    fun setWatermarkText(text: String) {
        watermarkText = text
        invalidate()
    }

    /**
     * 设置水印颜色
     */
    fun setWatermarkColor(color: Int) {
        watermarkPaint.color = color
        invalidate()
    }

    /**
     * 设置水印透明度（0-255）
     */
    fun setWatermarkAlpha(alpha: Int) {
        watermarkPaint.alpha = alpha.coerceIn(0, 255)
        invalidate()
    }

    /**
     * 设置水印文字大小
     */
    fun setWatermarkTextSize(size: Float) {
        watermarkPaint.textSize = size
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (watermarkText.isNotEmpty()) {
            drawDiagonalWatermark(canvas)
        }
    }

    /**
     * 绘制倾斜45度的对角线水印
     */
    private fun drawDiagonalWatermark(canvas: Canvas) {
        val text = watermarkText

        canvas.save()

        // 旋转画布45度
        canvas.rotate(-45f, width / 2f, height / 2f)

        // 绘制居中的水印
        val xPos = width / 2f
        val yPos = height / 2f - (watermarkPaint.descent() + watermarkPaint.ascent()) / 2

        canvas.drawText(text, xPos, yPos, watermarkPaint)

        canvas.restore()
    }
}
