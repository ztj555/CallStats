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
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import com.callstats.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var startDate: Long = 0
    private var endDate: Long = 0
    private var timeRangeText: String = ""
    private var lastQueryDateRange: String = ""  // 记录上次查询的时间段，避免重复查询
    private var isCompactMode = true // 默认显示查询界面
    private var isCallLogMode = false // 通话记录界面

    private lateinit var prefs: SharedPreferences

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val weekdayFormat = SimpleDateFormat("EEE", Locale.CHINESE)

    // 联系人缓存（ConcurrentHashMap避免锁竞争）
    private val contactCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    // 后台刷新 Handler
    private val contactRefreshHandler = Handler(Looper.getMainLooper())
    // 查询协程Job，用于取消重复查询
    private var queryJob: kotlinx.coroutines.Job? = null
    // P17: 联系人刷新Job，防止并发执行
    private var refreshJob: kotlinx.coroutines.Job? = null
    // P20: DatePickerDialog 引用，用于在销毁时 dismiss
    private var datePickerDialog: DatePickerDialog? = null
    // 预加载信号量，限制并发数（最多3个并发查询）
    private val preloadSemaphore = java.util.concurrent.Semaphore(3)
    // P6: 防抖机制 - 防止快速连续点击
    private var lastQueryTime = 0L
    private val QUERY_DEBOUNCE_MS = 300L  // 300ms 防抖阈值
    // 云端同步管理器
    private lateinit var cloudSyncManager: CloudSyncManager
    // 当前统计数据（用于同步）
    private var currentStats: CallStats? = null
    // P24: 定期刷新 Runnable，避免匿名对象创建
    private val refreshContactsRunnable = object : Runnable {
        override fun run() {
            refreshContactsInBackground()
            contactRefreshHandler.postDelayed(this, 5 * 60 * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化SharedPreferences
        prefs = getSharedPreferences("callstats_prefs", Context.MODE_PRIVATE)

        // 初始化云端同步管理器
        cloudSyncManager = CloudSyncManager(this)
        initCloudSyncUI()

        initViews()
        initNickname()
        initRecyclerView()
        // 默认显示当天
        setToday()
        checkPermission()

        // 默认显示查询界面
        switchToCompactMode()

        // 启动后台联系人刷新（延迟3秒启动，避开启动高峰）
        // P24: 使用类级别 Runnable，避免匿名对象
        contactRefreshHandler.postDelayed({
            refreshContactsInBackground()
            contactRefreshHandler.postDelayed(refreshContactsRunnable, 5 * 60 * 1000L)
        }, 3 * 1000L)
    }

    // 后台线程加载全部联系人到缓存（归一化号码提高命中率）
    private fun refreshContactsInBackground() {
        // P17: 防止并发执行，如果正在刷新则跳过
        if (refreshJob?.isActive == true) return
        refreshJob = lifecycleScope.launch {
            // P23: 直接在 ConcurrentHashMap 上操作，避免最后 putAll 的一次性大量操作
            withContext(Dispatchers.IO) {
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
                                // 直接写入缓存，同时存储原始号码和归一化号码
                                val normalized = normalizePhoneNumber(number)
                                contactCache[number] = name
                                contactCache[normalized] = name
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // P16: 取消正在进行的查询协程，避免泄漏
        queryJob?.cancel()
        refreshJob?.cancel()
        // P20: 移除所有待执行的刷新任务
        contactRefreshHandler.removeCallbacksAndMessages(null)
        // P20: dismiss DatePickerDialog 防止内存泄漏
        datePickerDialog?.dismiss()
        datePickerDialog = null
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
        // 更新云端显示的用户名
        updateCloudSyncUI()
    }

    // 初始化云端同步UI
    private fun initCloudSyncUI() {
        updateCloudSyncUI()
    }

    // 更新云端同步UI
    private fun updateCloudSyncUI() {
        binding.tvCurrentUser.text = cloudSyncManager.getUserDisplayName()
        binding.tvLastSync.text = "上次: ${cloudSyncManager.formatLastSyncTime()}"
    }

    // 同步数据到云端
    private fun syncToCloud() {
        val stats = currentStats
        if (stats == null) {
            Toast.makeText(this, "请先查询统计数据", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSync.isEnabled = false
        binding.btnSync.text = "同步中..."

        lifecycleScope.launch {
            cloudSyncManager.syncStats(stats) { result ->
                binding.btnSync.isEnabled = true
                binding.btnSync.text = "同步数据"

                if (result.success) {
                    cloudSyncManager.saveSyncTime(System.currentTimeMillis())
                    updateCloudSyncUI()
                    Toast.makeText(this@MainActivity, "同步成功！${result.message}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "同步失败: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 查看云端数据
    private fun viewCloudData() {
        // 弹出选择框选择平台
        val platforms = arrayOf("GitHub (国际)", "Gitee (国内)")
        val urls = arrayOf(
            "https://ztj555.github.io/tonghuashuju/",
            "https://gitee.com/zuo-tingjun/tonghuashuju"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择平台查看")
            .setItems(platforms) { _, which ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urls[which]))
                startActivity(intent)
            }
            .show()
    }

    // Token 配置对话框
    private fun showTokenConfigDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_token_config, null)
        val etGithubToken = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etGithubToken)
        val etGiteeToken = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etGiteeToken)

        // 加载已保存的 Token
        etGithubToken.setText(prefs.getString("github_token", "") ?: "")
        etGiteeToken.setText(prefs.getString("gitee_token", "") ?: "")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("配置云端 Token")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val githubToken = etGithubToken.text?.toString()?.trim() ?: ""
                val giteeToken = etGiteeToken.text?.toString()?.trim() ?: ""
                prefs.edit()
                    .putString("github_token", githubToken)
                    .putString("gitee_token", giteeToken)
                    .apply()
                Toast.makeText(this, "Token 已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
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

        // 同步按钮
        binding.btnSync.setOnClickListener {
            syncToCloud()
        }

        // 查看云端按钮
        binding.btnViewCloud.setOnClickListener {
            viewCloudData()
        }

        // Token 配置按钮
        binding.btnConfigToken.setOnClickListener {
            showTokenConfigDialog()
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
        // P26: 使用 CallLogAdapter，传入联系人查询 lambda
        binding.rvCallLogs.adapter = CallLogAdapter(
            getContactNameFunc = { number -> getContactName(number) },
            preloadContactNameFunc = { number -> preloadContactName(number) }
        )
        // P13: 设置固定高度，RecyclerView 不需要每次都重新计算
        binding.rvCallLogs.setHasFixedSize(true)
        // P13: 预取数量，提升滚动流畅度
        binding.rvCallLogs.setItemViewCacheSize(20)
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

        // P20: 保存 dialog 引用，防止内存泄漏
        datePickerDialog = DatePickerDialog(
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
        )
        datePickerDialog?.show()
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

    // P22: queryCallStats 内部添加 force 参数，用于 onResume 强制刷新（跳过防抖）
    private fun queryCallStats(force: Boolean = false) {
        // P6: 防抖 - 如果距离上次查询时间太短，忽略本次请求（除非是 force）
        val now = System.currentTimeMillis()
        if (!force && now - lastQueryTime < QUERY_DEBOUNCE_MS) {
            return
        }
        lastQueryTime = now

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先授予通话记录读取权限", Toast.LENGTH_SHORT).show()
            checkPermission()
            return
        }

        // 构建时间段文本
        timeRangeText = "${displayDateFormat.format(Date(startDate))} 至 ${displayDateFormat.format(Date(endDate))}"

        // P3: 取消旧的查询协程，避免重复查询
        queryJob?.cancel()

        // 后台线程查询通话记录，不阻塞UI
        queryJob = lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                queryCallLog()
            }

            // P19: 检查 Activity 是否已销毁，避免崩溃
            if (!isFinishing && !isDestroyed) {
                // 主线程更新UI
                updateUI(stats)
            }
        }
    }

    private fun queryCallLog(): Pair<CallStats, MutableList<CallLogItem>> {
        val stats = CallStats()
        val tempList = mutableListOf<CallLogItem>()

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

        // P25: 预定义通话类型文本，避免循环内重复创建
        val typeIncoming = "来电"
        val typeOutgoing = "去电"
        val typeMissed = "未接"
        val typeUnknown = "未知"

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
                    CallLog.Calls.INCOMING_TYPE -> typeIncoming
                    CallLog.Calls.OUTGOING_TYPE -> typeOutgoing
                    CallLog.Calls.MISSED_TYPE -> typeMissed
                    else -> typeUnknown
                }
                val weekdayText = weekdayFormat.format(dateObj)
                val timeText = timeFormat.format(dateObj)
                tempList.add(CallLogItem(
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

        return stats to tempList
    }

    // 更新UI（主线程）
    private fun updateUI(statsAndList: Pair<CallStats, List<CallLogItem>>) {
        val stats = statsAndList.first
        val tempList = statsAndList.second
        // 保存当前统计用于云端同步
        currentStats = stats

        // P5: 使用 submitList + AsyncListDiffer，自动计算 Diff 差异，只刷新变化的 item
        (binding.rvCallLogs.adapter as? CallLogAdapter)?.submitList(tempList)

        // 显示结果到标准界面（合并批量设置减少 requestLayout）
        val timeRangeStr = "统计时间段：$timeRangeText"
        val totalCallsStr = stats.totalCalls.toString()
        val totalDurationStr = formatDuration(stats.totalDuration)
        val incomingCallsStr = "${stats.incomingCalls} 次"
        val incomingDurationStr = formatDuration(stats.incomingDuration)
        val outgoingCallsStr = "${stats.outgoingCalls} 次"
        val outgoingDurationStr = formatDuration(stats.outgoingDuration)
        val missedCallsStr = "${stats.missedCalls} 次"

        // P7: 一次性设置所有 TextView，减少 measure/layout 次数
        binding.tvTimeRange.text = timeRangeStr
        binding.tvTotalCalls.text = totalCallsStr
        binding.tvTotalDuration.text = totalDurationStr
        binding.tvIncomingCalls.text = incomingCallsStr
        binding.tvIncomingDuration.text = incomingDurationStr
        binding.tvOutgoingCalls.text = outgoingCallsStr
        binding.tvOutgoingDuration.text = outgoingDurationStr
        binding.tvMissedCalls.text = missedCallsStr

        binding.cardResults.visibility = View.VISIBLE

        // 显示结果到查询界面
        binding.tvCompactTimeRange.text = timeRangeText
        binding.tvCompactTotalCalls.text = totalCallsStr
        binding.tvCompactTotalDuration.text = totalDurationStr
        binding.tvCompactIncomingCalls.text = incomingCallsStr
        binding.tvCompactIncomingDuration.text = incomingDurationStr
        binding.tvCompactOutgoingCalls.text = outgoingCallsStr
        binding.tvCompactOutgoingDuration.text = outgoingDurationStr
        binding.tvCompactMissedCalls.text = missedCallsStr
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60

        return when {
            minutes > 0 -> String.format("%d分%d秒", minutes, secs)
            else -> String.format("%d秒", secs)
        }
    }

    // 号码归一化：去除+-86、空格、-等，统一格式
    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }

    // 根据电话号码查询联系人备注/姓名（线程安全，无锁竞争）
    private fun getContactName(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        // 尝试原始号码
        contactCache[phoneNumber]?.let { return it }
        // 尝试归一化号码
        val normalized = normalizePhoneNumber(phoneNumber)
        if (normalized != phoneNumber) {
            contactCache[normalized]?.let { cachedName ->
                // P21: 只在有值时才写入缓存，避免空字符串污染
                contactCache[phoneNumber] = cachedName
                return cachedName
            }
        }
        // 兜底：返回空（不在滚动时查询数据库）
        return ""
    }

    // 后台预加载联系人到缓存（限流，最多3个并发）
    private fun preloadContactName(phoneNumber: String) {
        val normalized = normalizePhoneNumber(phoneNumber)
        // 已有缓存，跳过
        if (contactCache.containsKey(phoneNumber) || contactCache.containsKey(normalized)) {
            return
        }
        // P2: 信号量限制并发数，避免协程爆炸
        if (!preloadSemaphore.tryAcquire()) {
            return // 超过并发限制，丢弃本次请求
        }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
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
                                    contactCache[normalized] = name
                                    contactCache[phoneNumber] = name
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            } finally {
                preloadSemaphore.release()
            }
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

    // 通话记录数据类
    data class CallLogItem(
        val number: String,
        val type: String,
        val duration: String,
        val date: String,
        val weekday: String,
        val time: String
    )

    // P26: 通话记录适配器 - 使用 ListAdapter + DiffUtil 增量更新
    // 改为独立类，通过构造方法传入联系人查询回调
    class CallLogAdapter(
        private val getContactNameFunc: (String) -> String,
        private val preloadContactNameFunc: (String) -> Unit
    ) : RecyclerView.Adapter<CallLogAdapter.ViewHolder>() {

        // DiffUtil.ItemCallback：精确判断哪些 item 变了
        private object DiffCallback : DiffUtil.ItemCallback<CallLogItem>() {
            override fun areItemsTheSame(oldItem: CallLogItem, newItem: CallLogItem): Boolean {
                return oldItem.date == newItem.date &&
                       oldItem.time == newItem.time &&
                       oldItem.number == newItem.number
            }
            override fun areContentsTheSame(oldItem: CallLogItem, newItem: CallLogItem): Boolean {
                return oldItem == newItem
            }
        }

        // P5: 使用 ListAdapter + AsyncListDiffer，支持异步 Diff 计算
        private val differ = AsyncListDiffer(this, DiffCallback)

        fun submitList(newList: List<CallLogItem>) {
            differ.submitList(newList)
        }

        override fun getItemCount(): Int = differ.currentList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_call_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = differ.currentList[position]
            val contactName = getContactNameFunc(item.number)
            holder.tvName.text = contactName.ifBlank { item.number }
            holder.tvNumber.text = if (contactName.isNotBlank()) item.number else ""
            holder.tvType.text = item.type
            holder.tvDuration.text = item.duration
            holder.tvDate.text = item.date
            holder.tvWeekday.text = " ${item.weekday}"
            holder.tvTime.text = item.time
            // 只在缓存未命中时预加载
            if (contactName.isBlank()) {
                preloadContactNameFunc(item.number)
            }
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvName)
            val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)
            val tvType: TextView = itemView.findViewById(R.id.tvType)
            val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
            val tvDate: TextView = itemView.findViewById(R.id.tvDate)
            val tvWeekday: TextView = itemView.findViewById(R.id.tvWeekday)
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        }
    }

    override fun onResume() {
        super.onResume()
        // 按需刷新：只有日期范围变了才重新查询
        // P22: onResume 使用 force=true 跳过防抖，确保恢复页面时能刷新
        val currentRange = "$startDate-$endDate"
        if ((isCallLogMode || isCompactMode) && currentRange != lastQueryDateRange) {
            lastQueryDateRange = currentRange
            queryCallStats(force = true)
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }
}
