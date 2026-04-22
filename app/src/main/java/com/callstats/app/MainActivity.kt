package com.callstats.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.callstats.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var startDate: Long = 0
    private var endDate: Long = 0
    private var timeRangeText: String = ""
    private var isCompactMode = true // 默认显示查询界面
    private var isCallLogMode = false // 通话记录界面

    private lateinit var prefs: SharedPreferences

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val weekdayFormat = SimpleDateFormat("EEE", Locale.CHINESE)

    // 通话记录列表数据
    private val callLogList = mutableListOf<CallLogItem>()
    // 联系人缓存，避免重复查询
    private val contactCache = mutableMapOf<String, String>()
    // 后台刷新 Handler
    private val contactRefreshHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化SharedPreferences
        prefs = getSharedPreferences("callstats_prefs", Context.MODE_PRIVATE)

        initViews()
        initNickname()
        initRecyclerView()
        // 默认显示当天
        setToday()
        checkPermission()

        // 默认显示查询界面
        switchToCompactMode()

        // 启动后台联系人刷新（5分钟一次）
        startContactRefresh()
    }

    // 启动后台联系人刷新
    private fun startContactRefresh() {
        // 首次立即加载一次
        refreshContactsInBackground()

        // 每5分钟定期刷新
        contactRefreshHandler.postDelayed(object : Runnable {
            override fun run() {
                refreshContactsInBackground()
                contactRefreshHandler.postDelayed(this, 5 * 60 * 1000L)
            }
        }, 5 * 60 * 1000L)
    }

    // 后台线程加载全部联系人到缓存
    private fun refreshContactsInBackground() {
        lifecycleScope.launch {
            val newCache = withContext(Dispatchers.IO) {
                val map = mutableMapOf<String, String>()
                try {
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        ),
                        null, null, null
                    )?.use { cursor ->
                        val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val number = cursor.getString(numberIdx) ?: ""
                            val name = cursor.getString(nameIdx) ?: ""
                            if (number.isNotBlank() && name.isNotBlank()) {
                                map[number] = name
                            }
                        }
                    }
                } catch (_: Exception) { }
                map
            }

            // 合并到主缓存，静默更新，不干扰 UI
            synchronized(contactCache) {
                contactCache.putAll(newCache)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contactRefreshHandler.removeCallbacksAndMessages(null)
    }

    // 初始化昵称显示
    private fun initNickname() {
        val savedNickname = prefs.getString("user_nickname", "") ?: ""
        binding.etNickname.setText(savedNickname)

        binding.etNickname.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveNickname()
                true
            } else {
                false
            }
        }

        binding.etNickname.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveNickname()
        }
    }

    // 保存昵称
    private fun saveNickname() {
        val nickname = binding.etNickname.text?.toString()?.trim() ?: ""
        prefs.edit().putString("user_nickname", nickname).apply()
        // 隐藏键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etNickname.windowToken, 0)
        binding.etNickname.clearFocus()
    }

    private fun initViews() {
        // 快捷日期按钮
        binding.btnToday.setOnClickListener { setToday() }
        binding.btnYesterday.setOnClickListener { setYesterday() }
        binding.btnWeek.setOnClickListener { setWeek() }
        binding.btnMonth.setOnClickListener { setMonth() }
        binding.btnCustom.setOnClickListener { showCustomPicker() }

        // 日期选择按钮
        binding.btnStartDate.setOnClickListener { showDatePicker(true) }
        binding.btnEndDate.setOnClickListener { showDatePicker(false) }

        // 打赏按钮
        binding.btnDonate.setOnClickListener {
            startActivity(Intent(this, DonateActivity::class.java))
        }

        // 标准界面查询按钮 - 切换到查询界面
        binding.btnQuery.setOnClickListener {
            queryCallStats()
            switchToCompactMode()
        }

        // 查询界面的FAB按钮 - 切换到标准界面
        binding.fabQuery.setOnClickListener {
            switchToStandardMode()
        }

        // 标题点击 - 切换到通话记录界面
        binding.tvTitle.setOnClickListener {
            switchToCallLogMode()
        }
    }

    private fun initRecyclerView() {
        binding.rvCallLogs.layoutManager = LinearLayoutManager(this)
        binding.rvCallLogs.adapter = CallLogAdapter(callLogList)
    }

    // 切换到查询界面（紧凑模式）
    private fun switchToCompactMode() {
        isCompactMode = true
        isCallLogMode = false
        binding.scrollStandard.visibility = View.GONE
        binding.fabQuery.visibility = View.VISIBLE
        binding.cardCompactResults.visibility = View.VISIBLE
        binding.rvCallLogs.visibility = View.VISIBLE
        binding.rvCallLogs.alpha = 0.3f
        binding.layoutFooter.visibility = View.GONE
        disableNicknameInput()
    }

    // 切换到标准界面
    private fun switchToStandardMode() {
        isCompactMode = false
        isCallLogMode = false
        binding.scrollStandard.visibility = View.VISIBLE
        binding.fabQuery.visibility = View.GONE
        binding.cardCompactResults.visibility = View.GONE
        binding.rvCallLogs.visibility = View.GONE
        binding.rvCallLogs.alpha = 1.0f
        binding.layoutFooter.visibility = View.VISIBLE
        binding.etNickname.setText(prefs.getString("user_nickname", "") ?: "")
        enableNicknameInput()
    }

    // 切换到通话记录界面
    private fun switchToCallLogMode() {
        isCallLogMode = true
        binding.scrollStandard.visibility = View.GONE
        binding.fabQuery.visibility = View.VISIBLE
        binding.cardCompactResults.visibility = View.GONE
        binding.rvCallLogs.visibility = View.VISIBLE
        binding.rvCallLogs.alpha = 1.0f
        binding.layoutFooter.visibility = View.GONE
        disableNicknameInput()
    }

    // 禁用昵称输入（非标准界面，不可编辑但视觉不变灰）
    private fun disableNicknameInput() {
        binding.etNickname.isFocusable = false
        binding.etNickname.isFocusableInTouchMode = false
        binding.etNickname.isClickable = false
    }

    // 启用昵称输入（标准界面，可正常编辑）
    private fun enableNicknameInput() {
        binding.etNickname.isFocusable = true
        binding.etNickname.isFocusableInTouchMode = true
        binding.etNickname.isClickable = true
        binding.etNickname.isEnabled = true
    }

    // 当天：今天到今天
    private fun setToday() {
        val calendar = Calendar.getInstance()
        startDate = calendar.timeInMillis
        endDate = calendar.timeInMillis
        updateDateButtons()
        updateButtonStates(0)
        queryCallStats()
    }

    // 昨天：昨天到昨天
    private fun setYesterday() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        startDate = calendar.timeInMillis
        endDate = calendar.timeInMillis
        updateDateButtons()
        updateButtonStates(1)
        queryCallStats()
    }

    // 一周：最近7天
    private fun setWeek() {
        val calendar = Calendar.getInstance()
        endDate = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        startDate = calendar.timeInMillis
        updateDateButtons()
        updateButtonStates(2)
        queryCallStats()
    }

    // 本月：当前月份1号到当天
    private fun setMonth() {
        val calendar = Calendar.getInstance()
        endDate = calendar.timeInMillis
        // 设为当月1号
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startDate = calendar.timeInMillis
        updateDateButtons()
        updateButtonStates(3)
        queryCallStats()
    }

    // 自定义：显示日期选择器
    private fun showCustomPicker() {
        updateButtonStates(4)
        showDatePicker(true)
    }

    // 更新按钮选中状态
    private fun updateButtonStates(selected: Int) {
        binding.btnToday.isChecked = selected == 0
        binding.btnYesterday.isChecked = selected == 1
        binding.btnWeek.isChecked = selected == 2
        binding.btnMonth.isChecked = selected == 3
        binding.btnCustom.isChecked = selected == 4
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
                    showDatePicker(false)
                } else {
                    endDate = calendar.timeInMillis
                    updateDateButtons()
                    queryCallStats()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun checkPermission() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALL_LOG)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
                queryCallStats()
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

        // 查询通话记录
        val stats = queryCallLog()

        // 显示结果到标准界面
        binding.tvTimeRange.text = "统计时间段：$timeRangeText"
        binding.tvTotalCalls.text = stats.totalCalls.toString()
        binding.tvTotalDuration.text = formatDuration(stats.totalDuration)

        binding.tvIncomingCalls.text = "${stats.incomingCalls} 次"
        binding.tvIncomingDuration.text = formatDuration(stats.incomingDuration)

        binding.tvOutgoingCalls.text = "${stats.outgoingCalls} 次"
        binding.tvOutgoingDuration.text = formatDuration(stats.outgoingDuration)

        binding.tvMissedCalls.text = "${stats.missedCalls} 次"

        binding.cardResults.visibility = View.VISIBLE

        // 显示结果到查询界面（完整内容，80%宽度）
        binding.tvCompactTimeRange.text = timeRangeText
        binding.tvCompactTotalCalls.text = stats.totalCalls.toString()
        binding.tvCompactTotalDuration.text = formatDuration(stats.totalDuration)

        binding.tvCompactIncomingCalls.text = "${stats.incomingCalls} 次"
        binding.tvCompactIncomingDuration.text = formatDuration(stats.incomingDuration)

        binding.tvCompactOutgoingCalls.text = "${stats.outgoingCalls} 次"
        binding.tvCompactOutgoingDuration.text = formatDuration(stats.outgoingDuration)

        binding.tvCompactMissedCalls.text = "${stats.missedCalls} 次"
    }

    private fun queryCallLog(): CallStats {
        val stats = CallStats()
        callLogList.clear()

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
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION,
                CallLog.Calls.DATE
            ),
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)

            while (it.moveToNext()) {
                val number = it.getString(numberIndex) ?: ""
                val type = it.getInt(typeIndex)
                val duration = it.getInt(durationIndex)
                val date = it.getLong(dateIndex)
                val dateObj = Date(date)

                val typeText = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "来电"
                    CallLog.Calls.OUTGOING_TYPE -> "去电"
                    CallLog.Calls.MISSED_TYPE -> "未接"
                    else -> "未知"
                }
                val weekdayText = weekdayFormat.format(dateObj)
                val timeText = timeFormat.format(dateObj)
                callLogList.add(CallLogItem(
                    number,
                    typeText,
                    formatDuration(duration),
                    dateFormat.format(dateObj),
                    weekdayText,
                    timeText
                ))

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

        // 更新RecyclerView
        binding.rvCallLogs.adapter?.notifyDataSetChanged()

        return stats
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60

        return when {
            minutes > 0 -> String.format("%d分%d秒", minutes, secs)
            else -> String.format("%d秒", secs)
        }
    }

    // 根据电话号码查询联系人备注/姓名（线程安全缓存）
    private fun getContactName(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        synchronized(contactCache) {
            contactCache[phoneNumber]?.let { return it }
        }
        try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex) ?: ""
                        synchronized(contactCache) {
                            contactCache[phoneNumber] = name
                        }
                        return name
                    }
                }
            }
        } catch (_: Exception) { }
        synchronized(contactCache) {
            contactCache[phoneNumber] = ""
        }
        return ""
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

    // 通话记录数据类
    data class CallLogItem(
        val number: String,
        val type: String,
        val duration: String,
        val date: String,
        val weekday: String,
        val time: String
    )

    // 通话记录适配器
    class CallLogAdapter(private val list: List<CallLogItem>) :
        RecyclerView.Adapter<CallLogAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvName)
            val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)
            val tvType: TextView = itemView.findViewById(R.id.tvType)
            val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
            val tvDate: TextView = itemView.findViewById(R.id.tvDate)
            val tvWeekday: TextView = itemView.findViewById(R.id.tvWeekday)
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_call_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val contactName = getContactName(item.number)
            holder.tvName.text = contactName.ifBlank { item.number }
            holder.tvNumber.text = if (contactName.isNotBlank()) item.number else ""
            holder.tvType.text = item.type
            holder.tvDuration.text = item.duration
            holder.tvDate.text = item.date
            holder.tvWeekday.text = " ${item.weekday}"
            holder.tvTime.text = item.time
        }

        override fun getItemCount() = list.size
    }

    override fun onResume() {
        super.onResume()
        // 如果在通话记录界面或查询界面，重新加载数据
        if (isCallLogMode || isCompactMode) {
            queryCallStats()
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }
}
