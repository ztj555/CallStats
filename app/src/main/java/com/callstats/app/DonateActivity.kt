package com.callstats.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class DonateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)

        // 关闭按钮
        findViewById<MaterialButton>(R.id.btnClose).setOnClickListener {
            finish()
        }

        // 微信打赏按钮
        findViewById<MaterialButton>(R.id.btnWechatDonate).setOnClickListener {
            openWechatDonate()
        }

        // 支付宝打赏按钮
        findViewById<MaterialButton>(R.id.btnAlipayDonate).setOnClickListener {
            openAlipayDonate()
        }
    }

    private fun openWechatDonate() {
        try {
            // 尝试打开微信
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("weixin://"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "请安装微信后使用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAlipayDonate() {
        try {
            // 尝试打开支付宝
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("alipays://"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "请安装支付宝后使用", Toast.LENGTH_SHORT).show()
        }
    }

    // 检查应用是否安装
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
