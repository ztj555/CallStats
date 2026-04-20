package com.callstats.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.CallLog
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.callstats.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var startDate: Long = 0
    private var endDate: Long = 0
    private var timeRangeText: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setDefaultDates()
        checkPermission()
    }

    private fun initViews() {
        binding.btnStartDate.setOnClickListener { showDatePicker(true) }
        binding.btnEndDate.setOnClickListener { showDatePicker(false) }
        binding.btnQuery.setOnClickListener { queryCallStats() }
    }

    private fun setDefaultDates() {
        val calendar = Calendar.getInstance()
        endDate = calendar.timeInMillis

        calendar.add(Calendar.MONTH, -1)
        startDate = calendar.timeInMillis

        updateDateButtons()
    }

    private fun updateDateButtons() {
        binding.btnStartDate.text = displayDateFormat.format(Date(startDate))
        binding.btnEndDate.text = displayDateFormat.format(Date(endDate))
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = if (isStartDate) startDate else endDate

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                if (isStartDate) {
                    startDate = calendar.timeInMillis
                } else {
                    endDate = calendar.timeInMillis
                }
                updateDateButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CALL_LOG),
                REQUEST_READ_CALL_LOG
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_CALL_LOG) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "权限被拒绝，无法读取通话记录", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun queryCallStats() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先授予通话记录读取权限", Toast.LENGTH_SHORT).show()
            checkPermission()
            return
        }

        // 构建时间段文本
        timeRangeText = "${displayDateFormat.format(Date(startDate))} 至 ${displayDateFormat.format(Date(endDate))}"

        // 设置水印
        binding.watermarkContainer.setWatermarkText(timeRangeText)

        // 查询通话记录
        val stats = queryCallLog()

        // 显示结果
        binding.tvTimeRange.text = "统计时间段：$timeRangeText"
        binding.tvTotalCalls.text = stats.totalCalls.toString()
        binding.tvTotalDuration.text = formatDuration(stats.totalDuration)

        binding.tvIncomingCalls.text = "${stats.incomingCalls} 次"
        binding.tvIncomingDuration.text = formatDuration(stats.incomingDuration)

        binding.tvOutgoingCalls.text = "${stats.outgoingCalls} 次"
        binding.tvOutgoingDuration.text = formatDuration(stats.outgoingDuration)

        binding.tvMissedCalls.text = "${stats.missedCalls} 次"

        binding.cardResults.visibility = View.VISIBLE
    }

    private fun queryCallLog(): CallStats {
        val stats = CallStats()

        // 设置结束时间为当天的23:59:59
        val endCalendar = Calendar.getInstance()
        endCalendar.timeInMillis = endDate
        endCalendar.set(Calendar.HOUR_OF_DAY, 23)
        endCalendar.set(Calendar.MINUTE, 59)
        endCalendar.set(Calendar.SECOND, 59)
        endCalendar.set(Calendar.MILLISECOND, 999)
        val endTime = endCalendar.timeInMillis

        // 设置开始时间为当天00:00:00
        val startCalendar = Calendar.getInstance()
        startCalendar.timeInMillis = startDate
        startCalendar.set(Calendar.HOUR_OF_DAY, 0)
        startCalendar.set(Calendar.MINUTE, 0)
        startCalendar.set(Calendar.SECOND, 0)
        startCalendar.set(Calendar.MILLISECOND, 0)
        val startTime = startCalendar.timeInMillis

        val selection = "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?"
        val selectionArgs = arrayOf(startTime.toString(), endTime.toString())

        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION
            ),
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val type = it.getInt(typeIndex)
                val duration = it.getInt(durationIndex)

                when (type) {
                    CallLog.Calls.INCOMING_TYPE -> {
                        stats.incomingCalls++
                        stats.incomingDuration += duration
                        stats.totalCalls++
                        stats.totalDuration += duration
                    }
                    CallLog.Calls.OUTGOING_TYPE -> {
                        stats.outgoingCalls++
                        stats.outgoingDuration += duration
                        stats.totalCalls++
                        stats.totalDuration += duration
                    }
                    CallLog.Calls.MISSED_TYPE -> {
                        stats.missedCalls++
                        stats.totalCalls++
                    }
                }
            }
        }

        return stats
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> String.format("%d小时%d分%d秒", hours, minutes, secs)
            minutes > 0 -> String.format("%d分%d秒", minutes, secs)
            else -> String.format("%d秒", secs)
        }
    }

    data class CallStats(
        var totalCalls: Int = 0,
        var totalDuration: Int = 0,
        var incomingCalls: Int = 0,
        var incomingDuration: Int = 0,
        var outgoingCalls: Int = 0,
        var outgoingDuration: Int = 0,
        var missedCalls: Int = 0
    )

    companion object {
        private const val REQUEST_READ_CALL_LOG = 1001
    }
}
