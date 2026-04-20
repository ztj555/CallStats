package com.callanalyzer.app.ui.main

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.callanalyzer.app.R
import com.callanalyzer.app.data.entity.CallLogEntity
import com.callanalyzer.app.databinding.ActivityMainBinding
import com.callanalyzer.app.utils.DurationFormatter
import com.callanalyzer.app.utils.QueryFilter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = CallLogAdapter()

    private val sdfDisplay = SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.READ_CALL_LOG] == true) {
            viewModel.syncCallLogs()
        } else {
            Snackbar.make(binding.root, "需要通话记录权限才能使用本应用", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupButtons()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupObservers() {
        // 通话列表
        viewModel.callLogs.observe(this) { logs ->
            adapter.submitList(logs)
            binding.tvCount.text = "共 ${logs.size} 条"
            binding.tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        }

        // 水印与标题同步
        viewModel.filter.collect(this) { filter ->
            binding.watermarkView.setWatermark(filter.rangeLabel)
            binding.tvDateRange.text = filter.rangeLabel
        }

        // 同步状态
        viewModel.syncState.observe(this) { state ->
            when (state) {
                is SyncState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is SyncState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                }
                is SyncState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Snackbar.make(binding.root, "⚠️ ${state.message}", Snackbar.LENGTH_LONG).show()
                }
                else -> binding.progressBar.visibility = View.GONE
            }
        }

        // 统计结果
        viewModel.stats.observe(this) { stats ->
            if (stats != null) showStatsDialog(stats)
        }

        // 错误
        viewModel.error.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun setupButtons() {
        // 悬浮查询按钮
        binding.fabQuery.setOnClickListener { showQueryDialog() }
        // 悬浮统计按钮
        binding.fabStats.setOnClickListener { viewModel.loadStats() }
        // 刷新按钮
        binding.btnRefresh.setOnClickListener { viewModel.forceRefresh() }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.syncCallLogs()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    // ================== 查询弹窗 ==================

    private fun showQueryDialog() {
        val current = viewModel.filter.value
        val dialogView = layoutInflater.inflate(R.layout.dialog_query, null)

        // 时间范围选择
        val rgTime = dialogView.findViewById<RadioGroup>(R.id.rg_time_range)
        val layoutCustom = dialogView.findViewById<View>(R.id.layout_custom_date)
        val tvStartDate = dialogView.findViewById<TextView>(R.id.tv_start_date)
        val tvEndDate = dialogView.findViewById<TextView>(R.id.tv_end_date)

        // 通话类型
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rg_call_type)

        // 关键词
        val etKeyword = dialogView.findViewById<EditText>(R.id.et_keyword)

        // 最短时长
        val etMinDuration = dialogView.findViewById<EditText>(R.id.et_min_duration)

        // 自定义日期时间戳（用数组包装，使 lambda 可修改）
        val customRange = longArrayOf(current.startMs, current.endMs)
        tvStartDate.text = sdfDisplay.format(Date(customRange[0]))
        tvEndDate.text = sdfDisplay.format(Date(customRange[1]))

        // 恢复当前条件
        etKeyword.setText(current.keyword)
        etMinDuration.setText(if (current.minDurationSec > 0) current.minDurationSec.toString() else "")
        when (current.callType) {
            CallLogEntity.TYPE_INCOMING -> rgType.check(R.id.rb_incoming)
            CallLogEntity.TYPE_OUTGOING -> rgType.check(R.id.rb_outgoing)
            CallLogEntity.TYPE_MISSED -> rgType.check(R.id.rb_missed)
            else -> rgType.check(R.id.rb_all)
        }

        // 若上次用的是自定义范围，打开弹窗时自动选中「自定义」并展开
        val wasCustom = current.rangeLabel.contains(" - ") &&
                !current.rangeLabel.startsWith("本")
        if (wasCustom) {
            rgTime.check(R.id.rb_custom)
            layoutCustom.visibility = View.VISIBLE
        }

        // 时间范围切换
        rgTime.setOnCheckedChangeListener { _, id ->
            layoutCustom.visibility = if (id == R.id.rb_custom) View.VISIBLE else View.GONE
        }

        // 自定义开始日期
        tvStartDate.setOnClickListener {
            val initCal = Calendar.getInstance().apply { timeInMillis = customRange[0] }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    // 用全新 Calendar 构建时间戳，避免对象复用副作用
                    customRange[0] = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    tvStartDate.text = sdfDisplay.format(Date(customRange[0]))
                    // 若结束日期早于开始日期，自动同步
                    if (customRange[1] < customRange[0]) {
                        customRange[1] = Calendar.getInstance().apply {
                            set(year, month, day, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }.timeInMillis
                        tvEndDate.text = sdfDisplay.format(Date(customRange[1]))
                    }
                },
                initCal.get(Calendar.YEAR),
                initCal.get(Calendar.MONTH),
                initCal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 自定义结束日期
        tvEndDate.setOnClickListener {
            val initCal = Calendar.getInstance().apply { timeInMillis = customRange[1] }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    customRange[1] = Calendar.getInstance().apply {
                        set(year, month, day, 23, 59, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                    tvEndDate.text = sdfDisplay.format(Date(customRange[1]))
                    // 若开始日期晚于结束日期，自动同步
                    if (customRange[0] > customRange[1]) {
                        customRange[0] = Calendar.getInstance().apply {
                            set(year, month, day, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        tvStartDate.text = sdfDisplay.format(Date(customRange[0]))
                    }
                },
                initCal.get(Calendar.YEAR),
                initCal.get(Calendar.MONTH),
                initCal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("筛选条件")
            .setView(dialogView)
            .setPositiveButton("查询") { _, _ ->
                val baseFilter = when (rgTime.checkedRadioButtonId) {
                    R.id.rb_today -> QueryFilter.today()
                    R.id.rb_week -> QueryFilter.thisWeek()
                    R.id.rb_month -> QueryFilter.thisMonth()
                    else -> QueryFilter.custom(customRange[0], customRange[1])
                }
                val callType = when (rgType.checkedRadioButtonId) {
                    R.id.rb_incoming -> CallLogEntity.TYPE_INCOMING
                    R.id.rb_outgoing -> CallLogEntity.TYPE_OUTGOING
                    R.id.rb_missed -> CallLogEntity.TYPE_MISSED
                    else -> 0
                }
                val keyword = etKeyword.text.toString().trim()
                val minDuration = etMinDuration.text.toString().toLongOrNull() ?: 0L

                viewModel.applyFilter(
                    baseFilter.copy(
                        callType = callType,
                        keyword = keyword,
                        minDurationSec = minDuration
                    )
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ================== 统计弹窗 ==================

    private fun showStatsDialog(stats: com.callanalyzer.app.data.dao.CallStatsResult) {
        val filter = viewModel.filter.value
        val content = buildString {
            appendLine("📅 统计时间段")
            appendLine(filter.rangeLabel)
            appendLine()
            appendLine("📊 通话次数")
            appendLine("  合计：${stats.totalCount} 次")
            appendLine("  去电：${stats.outgoingCount} 次")
            appendLine("  来电：${stats.incomingCount} 次")
            appendLine("  未接：${stats.missedCount} 次")
            appendLine()
            appendLine("⏱ 通话时长")
            appendLine("  合计：${DurationFormatter.format(stats.totalDuration)}")
            appendLine("  去电：${DurationFormatter.format(stats.outgoingDuration)}")
            appendLine("  来电：${DurationFormatter.format(stats.incomingDuration)}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("通话统计")
            .setMessage(content)
            .setPositiveButton("确定", null)
            .show()
    }

    // 扩展函数：在 Activity 中收集 StateFlow
    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collect(
        owner: androidx.lifecycle.LifecycleOwner,
        action: (T) -> Unit
    ) {
        androidx.lifecycle.lifecycleScope.launch {
            androidx.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                collect { action(it) }
            }
        }
    }
}
