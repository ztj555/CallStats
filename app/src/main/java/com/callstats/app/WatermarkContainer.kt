package com.callstats.app

import android.content.Context
import android.graphics.Bitmap
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
 *
 * 性能优化：
 * - 使用 Bitmap 缓存，避免每次 onDraw 都计算
 * - 只在文本变化时重新绘制 bitmap
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

    // P11: 缓存绘制结果，避免每次 onDraw 都计算
    private var cachedBitmap: Bitmap? = null
    private var cachedText: String = ""
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0

    init {
        // 添加一个自定义View来绘制水印
        addView(object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                // P11: 直接绘制缓存的 Bitmap，避免重复计算
                cachedBitmap?.let { bitmap ->
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                }
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * 设置水印文本
     */
    fun setWatermarkText(text: String) {
        if (watermarkText == text) return  // 文本没变，跳过
        watermarkText = text

        // 延迟绘制，等 View 尺寸确定后再绘制
        post {
            regenerateBitmap()
        }
    }

    /**
     * 获取水印文本
     */
    fun getWatermarkText(): String = watermarkText

    /**
     * P11: 重新生成水印 Bitmap 并缓存
     */
    private fun regenerateBitmap() {
        if (watermarkText.isEmpty()) {
            cachedBitmap?.recycle()
            cachedBitmap = null
            cachedText = ""
            // P26: 重绘 FrameLayout 本身
            invalidate()
            return
        }

        // 如果尺寸没变且文本没变，跳过
        if (width > 0 && height > 0 &&
            width == cachedWidth && height == cachedHeight &&
            watermarkText == cachedText) {
            return
        }

        // 回收旧 Bitmap
        cachedBitmap?.recycle()

        cachedWidth = width
        cachedHeight = height

        try {
            // 创建与 View 同样大小的 Bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

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

            // P11: 预计算 diagonal，避免每次绘制都计算 Math.sqrt
            val diagonal = (Math.sqrt((width * width + height * height).toDouble()) * 1.5).toInt()

            // 绘制重复水印
            var x = -diagonal
            var y = -diagonal

            while (y < diagonal * 2) {
                while (x < diagonal * 2) {
                    canvas.drawText(text, x.toFloat(), y.toFloat(), watermarkPaint)
                    x += spacingX
                }
                x = -diagonal
                y += spacingY
            }

            // 恢复画布状态
            canvas.restore()

            cachedBitmap = bitmap
            cachedText = watermarkText

            // P26: 重绘 FrameLayout 本身
            invalidate()

        } catch (e: OutOfMemoryError) {
            // 内存不足时降级处理
            cachedBitmap = null
            cachedText = ""
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 清理 Bitmap 缓存
        cachedBitmap?.recycle()
        cachedBitmap = null
    }
}
