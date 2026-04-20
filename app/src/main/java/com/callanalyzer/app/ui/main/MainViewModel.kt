package com.callanalyzer.app.ui.main

import android.app.Application
import androidx.lifecycle.*
import com.callanalyzer.app.data.dao.CallStatsResult
import com.callanalyzer.app.data.entity.CallLogEntity
import com.callanalyzer.app.data.repository.CallLogRepository
import com.callanalyzer.app.utils.QueryFilter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CallLogRepository(application)

    // 当前查询条件（默认：今天）
    private val _filter = MutableStateFlow(QueryFilter.today())
    val filter: StateFlow<QueryFilter> = _filter.asStateFlow()

    // 通话列表（随 filter 变化自动更新）
    val callLogs: LiveData<List<CallLogEntity>> = _filter
        .flatMapLatest { repository.queryLogs(it) }
        .asLiveData()

    // 统计结果
    private val _stats = MutableLiveData<CallStatsResult?>()
    val stats: LiveData<CallStatsResult?> = _stats

    // 同步状态
    private val _syncState = MutableLiveData<SyncState>(SyncState.Idle)
    val syncState: LiveData<SyncState> = _syncState

    // 错误信息
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        syncCallLogs()
    }

    /**
     * 同步系统通话记录到本地缓存
     */
    fun syncCallLogs() {
        viewModelScope.launch {
            _syncState.value = SyncState.Loading
            try {
                val count = repository.syncFromSystem()
                val total = repository.totalCached()
                _syncState.value = SyncState.Success("已同步 $count 条新记录，共缓存 $total 条")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "同步失败")
            }
        }
    }

    /**
     * 强制全量刷新
     */
    fun forceRefresh() {
        viewModelScope.launch {
            _syncState.value = SyncState.Loading
            try {
                val count = repository.forceRefresh()
                _syncState.value = SyncState.Success("已重新加载 $count 条记录")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "刷新失败")
            }
        }
    }

    /**
     * 更新查询条件
     */
    fun applyFilter(newFilter: QueryFilter) {
        _filter.value = newFilter
        // 统计数据重置（等用户手动触发统计）
        _stats.value = null
    }

    /**
     * 执行统计分析
     */
    fun loadStats() {
        viewModelScope.launch {
            try {
                val result = repository.queryStats(_filter.value)
                _stats.value = result
            } catch (e: Exception) {
                _error.value = "统计失败：${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}
