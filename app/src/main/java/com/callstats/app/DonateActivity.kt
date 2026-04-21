package com.callstats.app

import android.os.Bundle
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
    }
}
