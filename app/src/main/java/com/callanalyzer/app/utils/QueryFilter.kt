package com.callanalyzer.app.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * 查询过滤条件
 */
data class QueryFilter(
    val startMs: Long,
    val endMs: Long,
    val callType: Int = 0,          // 0=全部 1=来电 2=去电 3=未接
    val keyword: String = "",        // 号码/姓名关键词
    val minDurationSec: Long = 0,    // 最短通话时长（秒）
    val rangeLabel: String = ""      // 时间段标签（用于水印）
) {
    companion object {
        private val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
        private val sdfFull = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA)

        /** 今天 */
        fun today(): QueryFilter {
            val cal = Calendar.getInstance()
            val start = dayStart(cal).timeInMillis
            val end = dayEnd(cal).timeInMillis
            val label = sdf.format(Date(start))
            return QueryFilter(start, end, rangeLabel = label)
        }

        /** 本周（周一~周日） */
        fun thisWeek(): QueryFilter {
            val cal = Calendar.getInstance()
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val start = dayStart(cal).timeInMillis
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY &&
                cal.get(Calendar.DAY_OF_WEEK) < Calendar.MONDAY) {
                cal.add(Calendar.WEEK_OF_YEAR, 1)
            }
            // 修正：周日对应本周最后一天
            val calEnd = Calendar.getInstance()
            calEnd.firstDayOfWeek = Calendar.MONDAY
            calEnd.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calEnd.add(Calendar.DAY_OF_YEAR, 6)
            val end = dayEnd(calEnd).timeInMillis
            val calStart2 = Calendar.getInstance()
            calStart2.firstDayOfWeek = Calendar.MONDAY
            calStart2.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val start2 = dayStart(calStart2).timeInMillis
            val label = "本周（${sdf.format(Date(start2))}-${sdf.format(Date(end))}）"
            return QueryFilter(start2, end, rangeLabel = label)
        }

        /** 本月 */
        fun thisMonth(): QueryFilter {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val start = dayStart(cal).timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val end = dayEnd(cal).timeInMillis
            val label = "本月（${sdf.format(Date(start))}-${sdf.format(Date(end))}）"
            return QueryFilter(start, end, rangeLabel = label)
        }

        /** 自定义日期范围 */
        fun custom(startMs: Long, endMs: Long): QueryFilter {
            val label = "${sdf.format(Date(startMs))} - ${sdf.format(Date(endMs))}"
            return QueryFilter(startMs, endMs, rangeLabel = label)
        }

        private fun dayStart(cal: Calendar): Calendar {
            return (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        private fun dayEnd(cal: Calendar): Calendar {
            return (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
        }
    }
}

/**
 * 时长格式化工具
 */
object DurationFormatter {
    fun format(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}秒"
            seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
            else -> "${seconds / 3600}时${(seconds % 3600) / 60}分${seconds % 60}秒"
        }
    }
}
