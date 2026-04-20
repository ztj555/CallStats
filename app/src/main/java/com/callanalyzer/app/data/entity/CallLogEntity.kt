package com.callanalyzer.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 通话记录实体
 * 对应 Room 数据库表 call_logs
 */
@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 电话号码 */
    val number: String,

    /** 联系人姓名（无联系人则为空） */
    val name: String?,

    /** 通话类型：1=来电 2=去电 3=未接 */
    val callType: Int,

    /** 通话开始时间戳（毫秒） */
    val date: Long,

    /** 通话时长（秒） */
    val duration: Long,

    /** 系统通话记录 ID（用于去重） */
    val systemId: Long = 0
) {
    companion object {
        const val TYPE_INCOMING = 1   // 来电
        const val TYPE_OUTGOING = 2   // 去电
        const val TYPE_MISSED = 3     // 未接

        fun typeLabel(type: Int): String = when (type) {
            TYPE_INCOMING -> "来电"
            TYPE_OUTGOING -> "去电"
            TYPE_MISSED -> "未接"
            else -> "未知"
        }
    }
}
