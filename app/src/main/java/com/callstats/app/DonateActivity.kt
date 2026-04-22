package com.callstats.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class DonateActivity : AppCompatActivity() {

    // 支付宝吱口令
    private val alipayCode = "给我转账 Z:/DIxHmJU82hZ#  W:/j MU3481 \$459"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)

        // 关闭按钮
        findViewById<MaterialButton>(R.id.btnClose).setOnClickListener {
            finish()
        }

        // 微信打赏按钮
        findViewById<MaterialButton>(R.id.btnWechatDonate).setOnClickListener {
            openWechat()
        }

        // 支付宝打赏按钮
        findViewById<MaterialButton>(R.id.btnAlipayDonate).setOnClickListener {
            copyAndOpenAlipay()
        }

        // 微信收款码长按保存
        findViewById<ImageView>(R.id.ivWechatPay).setOnLongClickListener {
            // P12: 图片保存移到后台线程
            saveImageToGallery(R.drawable.wechat_pay, "微信收款码")
            true
        }

        // 支付宝收款码长按保存
        findViewById<ImageView>(R.id.ivAlipay).setOnLongClickListener {
            // P12: 图片保存移到后台线程
            saveImageToGallery(R.drawable.alipay, "支付宝收款码")
            true
        }
    }

    private fun openWechat() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("weixin://"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "请安装微信", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyAndOpenAlipay() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("支付宝吱口令", alipayCode)
            clipboard.setPrimaryClip(clip)

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("alipays://"))
            startActivity(intent)

            Toast.makeText(this, "吱口令已复制，打开支付宝粘贴", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://render.alipay.com/p/s/i"))
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "请安装支付宝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * P12: 图片保存 - 在后台线程解码并保存
     */
    private fun saveImageToGallery(drawableId: Int, imageName: String) {
        lifecycleScope.launch {
            try {
                // P12: 在 IO 线程解码图片
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeResource(resources, drawableId)
                }

                // 在主线程保存
                val saved = saveBitmapToGallery(bitmap, imageName)

                // Toast 需要在主线程
                withContext(Dispatchers.Main) {
                    if (saved) {
                        Toast.makeText(this@DonateActivity, "$imageName 已保存到相册", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@DonateActivity, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DonateActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val outputStream: OutputStream?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CallStats")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                outputStream = uri?.let { contentResolver.openOutputStream(it) }
            } else {
                // Android 9 及以下
                @Suppress("DEPRECATION")
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(imagesDir, "CallStats")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val imageFile = File(appDir, "$fileName.png")
                outputStream = FileOutputStream(imageFile)

                // 通知图库更新
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(imageFile)
                sendBroadcast(mediaScanIntent)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
