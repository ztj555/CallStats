package com.callstats.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

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

        // 微信打赏按钮 - 打开微信
        findViewById<MaterialButton>(R.id.btnWechatDonate).setOnClickListener {
            openWechat()
        }

        // 支付宝打赏按钮 - 复制吱口令并打开支付宝
        findViewById<MaterialButton>(R.id.btnAlipayDonate).setOnClickListener {
            copyAndOpenAlipay()
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
            // 复制吱口令到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("支付宝吱口令", alipayCode)
            clipboard.setPrimaryClip(clip)
            
            // 打开支付宝
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("alipays://"))
            startActivity(intent)
            
            Toast.makeText(this, "吱口令已复制，打开支付宝粘贴", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // 如果没有支付宝，尝试网页版
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://render.alipay.com/p/s/i"))
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "请安装支付宝", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
