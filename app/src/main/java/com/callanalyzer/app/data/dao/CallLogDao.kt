package com.callanalyzer.app.data.dao

import androidx.room.*
import com.callanalyzer.app.data.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<CallLogEntity>)

    @Query("DELETE FROM call_logs")
    suspend fun clearAll()

    /**
     * 多条件查询：时间范围 + 类型 + 号码/姓名关键词 + 最短时长
     */
    @Query("""
        SELECT * FROM call_logs
        WHERE date >= :startMs
          AND date <= :endMs
          AND (:callType = 0 OR callType = :callType)
          AND (:keyword = '' OR number LIKE '%' || :keyword || '%' OR name LIKE '%' || :keyword || '%')
          AND duration >= :minDuration
        ORDER BY date DESC
    """)
    fun queryFiltered(
        startMs: Long,
        endMs: Long,
        callType: Int,
        keyword: String,
        minDuration: Long
    ): Flow<List<CallLogEntity>>

    /**
     * 统计查询：返回指定条件下的汇总数据
     */
    @Query("""
        SELECT COUNT(*) as totalCount,
               SUM(CASE WHEN callType = 2 THEN 1 ELSE 0 END) as outgoingCount,
               SUM(CASE WHEN callType = 1 THEN 1 ELSE 0 END) as incomingCount,
               SUM(CASE WHEN callType = 3 THEN 1 ELSE 0 END) as missedCount,
               SUM(duration) as totalDuration,
               SUM(CASE WHEN callType = 2 THEN duration ELSE 0 END) as outgoingDuration,
               SUM(CASE WHEN callType = 1 THEN duration ELSE 0 END) as incomingDuration
        FROM call_logs
        WHERE date >= :startMs
          AND date <= :endMs
          AND (:callType = 0 OR callType = :callType)
          AND (:keyword = '' OR number LIKE '%' || :keyword || '%' OR name LIKE '%' || :keyword || '%')
          AND duration >= :minDuration
    """)
    suspend fun queryStats(
        startMs: Long,
        endMs: Long,
        callType: Int,
        keyword: String,
        minDuration: Long
    ): CallStatsResult

    @Query("SELECT COUNT(*) FROM call_logs")
    suspend fun count(): Int

    @Query("SELECT systemId FROM call_logs")
    suspend fun getAllSystemIds(): List<Long>
}

/**
 * 统计查询结果（Room 支持 POJO 映射）
 */
data class CallStatsResult(
    val totalCount: Int,
    val outgoingCount: Int,
    val incomingCount: Int,
    val missedCount: Int,
    val totalDuration: Long,
    val outgoingDuration: Long,
    val incomingDuration: Long
)
