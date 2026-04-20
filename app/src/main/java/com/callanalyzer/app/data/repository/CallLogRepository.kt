package com.callanalyzer.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.CallLog
import com.callanalyzer.app.data.AppDatabase
import com.callanalyzer.app.data.dao.CallStatsResult
import com.callanalyzer.app.data.entity.CallLogEntity
import com.callanalyzer.app.utils.QueryFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.callLogDao()

    /**
     * 从系统通话记录同步到本地 Room 数据库
     * 仅插入新增记录（通过 systemId 去重）
     */
    suspend fun syncFromSystem(): Int = withContext(Dispatchers.IO) {
        val existingIds = dao.getAllSystemIds().toSet()
        val systemLogs = readSystemCallLogs(context.contentResolver)
        val newLogs = systemLogs.filter { it.systemId !in existingIds }
        if (newLogs.isNotEmpty()) {
            dao.insertAll(newLogs)
        }
        newLogs.size
    }

    /**
     * 强制全量刷新：清空再重新读取
     */
    suspend fun forceRefresh(): Int = withContext(Dispatchers.IO) {
        dao.clearAll()
        val systemLogs = readSystemCallLogs(context.contentResolver)
        dao.insertAll(systemLogs)
        systemLogs.size
    }

    /**
     * 读取系统通话记录
     */
    private fun readSystemCallLogs(cr: ContentResolver): List<CallLogEntity> {
        val logs = mutableListOf<CallLogEntity>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        val cursor = cr.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        ) ?: return logs

        cursor.use {
            val idxId = it.getColumnIndexOrThrow(CallLog.Calls._ID)
            val idxNumber = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val idxName = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val idxType = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val idxDate = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val idxDuration = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val rawType = it.getInt(idxType)
                // 标准化类型：只保留 1/2/3
                val callType = when (rawType) {
                    CallLog.Calls.INCOMING_TYPE -> CallLogEntity.TYPE_INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallLogEntity.TYPE_OUTGOING
                    CallLog.Calls.MISSED_TYPE -> CallLogEntity.TYPE_MISSED
                    else -> continue  // 忽略拒接、黑名单等
                }
                logs.add(
                    CallLogEntity(
                        number = it.getString(idxNumber) ?: "",
                        name = it.getString(idxName)?.takeIf { n -> n.isNotBlank() },
                        callType = callType,
                        date = it.getLong(idxDate),
                        duration = it.getLong(idxDuration),
                        systemId = it.getLong(idxId)
                    )
                )
            }
        }
        return logs
    }

    /**
     * 多条件筛选通话记录（Flow，UI层观察）
     */
    fun queryLogs(filter: QueryFilter): Flow<List<CallLogEntity>> {
        return dao.queryFiltered(
            startMs = filter.startMs,
            endMs = filter.endMs,
            callType = filter.callType,
            keyword = filter.keyword,
            minDuration = filter.minDurationSec
        )
    }

    /**
     * 统计查询
     */
    suspend fun queryStats(filter: QueryFilter): CallStatsResult {
        return dao.queryStats(
            startMs = filter.startMs,
            endMs = filter.endMs,
            callType = filter.callType,
            keyword = filter.keyword,
            minDuration = filter.minDurationSec
        )
    }

    suspend fun totalCached(): Int = dao.count()
}
