package com.callstats.app

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * 云端同步管理器
 * 支持 GitHub 和 Gitee 双平台
 * 数据按用户分类存储
 */
class CloudSyncManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cloud_sync_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    // GitHub 配置（Token 从 SharedPreferences 读取）
    private val githubOwner = "ztj555"
    private val githubRepo = "tonghuashuju"
    private fun getGitHubToken(): String = prefs.getString("github_token", null) ?: ""

    // Gitee 配置（Token 从 SharedPreferences 读取）
    private val giteeOwner = "zuo-tingjun"
    private val giteeRepo = "tonghuashuju"
    private fun getGiteeToken(): String = prefs.getString("gitee_token", null) ?: ""

    /**
     * 获取或生成设备ID（用于区分不同用户）
     */
    fun getDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            // 尝试获取 Android ID
            deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (deviceId.isNullOrBlank()) {
                // 如果获取失败，生成随机ID
                deviceId = UUID.randomUUID().toString().take(8)
            }
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    /**
     * 获取用户显示名称（昵称优先，否则用设备ID）
     */
    fun getUserDisplayName(): String {
        val nickname = prefs.getString("user_nickname", null)
        return if (nickname.isNullOrBlank()) {
            getDeviceId()
        } else {
            nickname
        }
    }

    /**
     * 获取用户目录名（用于仓库路径）
     */
    fun getUserDirName(): String {
        val nickname = prefs.getString("user_nickname", null)
        return if (nickname.isNullOrBlank()) {
            getDeviceId()
        } else {
            // 昵称中的空格替换为下划线，特殊字符移除
            nickname.trim().replace(" ", "_").replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5]"), "")
        }
    }

    /**
     * 同步统计数据到云端
     * @param stats 通话统计数据
     * @param callback 回调
     */
    suspend fun syncStats(
        stats: CallStats,
        callback: (SyncResult) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 检查 Token 是否已配置
                val githubToken = getGitHubToken()
                val giteeToken = getGiteeToken()

                if (githubToken.isBlank() && giteeToken.isBlank()) {
                    val result = SyncResult(
                        success = false,
                        githubSuccess = false,
                        giteeSuccess = false,
                        message = "请先配置云端 Token"
                    )
                    withContext(Dispatchers.Main) {
                        callback(result)
                    }
                    return@withContext
                }

                val userDir = getUserDirName()
                val today = Date()
                val dateStr = dateFormat.format(today)
                val monthStr = monthFormat.format(today)

                // 构建数据
                val summaryData = buildString {
                    append("{")
                    append("\"date\": \"$dateStr\",")
                    append("\"deviceId\": \"${getDeviceId()}\",")
                    append("\"totalCalls\": ${stats.totalCalls},")
                    append("\"totalDuration\": ${stats.totalDuration},")
                    append("\"incomingCalls\": ${stats.incomingCalls},")
                    append("\"incomingDuration\": ${stats.incomingDuration},")
                    append("\"outgoingCalls\": ${stats.outgoingCalls},")
                    append("\"outgoingDuration\": ${stats.outgoingDuration},")
                    append("\"missedCalls\": ${stats.missedCalls}")
                    append("}")
                }

                // 同步到 GitHub
                val githubResult = if (githubToken.isNotBlank()) {
                    syncToGitHub(userDir, monthStr, dateStr, summaryData)
                } else {
                    SyncPlatformResult(success = false, message = "未配置 Token")
                }

                // 同步到 Gitee
                val giteeResult = if (giteeToken.isNotBlank()) {
                    syncToGitee(userDir, monthStr, dateStr, summaryData)
                } else {
                    SyncPlatformResult(success = false, message = "未配置 Token")
                }

                // 更新索引文件
                if (githubToken.isNotBlank()) {
                    updateIndexFile(userDir, dateStr)
                }

                val result = SyncResult(
                    success = githubResult.success || giteeResult.success,
                    githubSuccess = githubResult.success,
                    giteeSuccess = giteeResult.success,
                    message = buildString {
                        if (githubResult.success) append("GitHub ✓ ") else append("GitHub ✗ ")
                        if (giteeResult.success) append("Gitee ✓") else append("Gitee ✗")
                    }
                )

                withContext(Dispatchers.Main) {
                    callback(result)
                }

            } catch (e: Exception) {
                val result = SyncResult(
                    success = false,
                    githubSuccess = false,
                    giteeSuccess = false,
                    message = "同步失败: ${e.message}"
                )
                withContext(Dispatchers.Main) {
                    callback(result)
                }
            }
        }
    }

    /**
     * 同步到 GitHub
     */
    private suspend fun syncToGitHub(userDir: String, monthStr: String, dateStr: String, data: String): SyncPlatformResult {
        return withContext(Dispatchers.IO) {
            try {
                // 创建月份目录
                createGitHubDirectory("$userDir/data/$monthStr")

                // 上传文件
                val path = "$userDir/data/$monthStr/summary_$dateStr.json"
                val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/contents/$path")

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Authorization", "token ${getGitHubToken()}")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                // 检查文件是否存在，获取 SHA
                val existingSha = getGitHubFileSha(path)

                val body = buildString {
                    append("{")
                    append("\"message\": \"Update $dateStr stats\",")
                    append("\"content\": \"${Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)}\"")
                    if (existingSha != null) {
                        append(",\"sha\": \"$existingSha\"")
                    }
                    append("}")
                }

                conn.outputStream.write(body.toByteArray())
                conn.outputStream.flush()

                val responseCode = conn.responseCode
                conn.disconnect()

                SyncPlatformResult(success = responseCode in 200..299, message = "HTTP $responseCode")

            } catch (e: Exception) {
                SyncPlatformResult(success = false, message = e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 获取 GitHub 文件 SHA（用于更新）
     */
    private fun getGitHubFileSha(path: String): String? {
        return try {
            val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/contents/$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "token ${getGitHubToken()}")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                conn.disconnect()

                // 简单解析 SHA
                val shaMatch = Regex("\"sha\":\\s*\"([^\"]+)\"").find(response)
                shaMatch?.groupValues?.get(1)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建 GitHub 目录（通过创建 .gitkeep 文件）
     */
    private fun createGitHubDirectory(path: String) {
        try {
            val gitkeepPath = "$path/.gitkeep"
            val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/contents/$gitkeepPath")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "token ${getGitHubToken()}")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = "{\"message\": \"Create directory $path\"}"
            conn.outputStream.write(body.toByteArray())
            conn.outputStream.flush()
            conn.disconnect()
        } catch (e: Exception) {
            // 忽略目录创建错误
        }
    }

    /**
     * 同步到 Gitee
     */
    private suspend fun syncToGitee(userDir: String, monthStr: String, dateStr: String, data: String): SyncPlatformResult {
        return withContext(Dispatchers.IO) {
            try {
                // 创建月份目录
                createGiteeDirectory("$userDir/data/$monthStr")

                // 上传文件
                val path = "$userDir/data/$monthStr/summary_$dateStr.json"
                val url = URL("https://gitee.com/api/v5/repos/$giteeOwner/$giteeRepo/contents/$path")

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                // Gitee API 需要通过查询参数传 token
                val body = buildString {
                    append("{")
                    append("\"access_token\": \"${getGiteeToken()}\",")
                    append("\"message\": \"Update $dateStr stats\",")
                    append("\"content\": \"${Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)}\"")
                    append("}")
                }

                conn.outputStream.write(body.toByteArray())
                conn.outputStream.flush()

                val responseCode = conn.responseCode
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                // Gitee 返回 200 或 201 表示成功
                val success = responseCode in 200..299 || response.contains("\"created_at\"") || response.contains("\"updated_at\"")

                SyncPlatformResult(success = success, message = "HTTP $responseCode")

            } catch (e: Exception) {
                SyncPlatformResult(success = false, message = e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 创建 Gitee 目录（通过创建 .gitkeep 文件）
     */
    private fun createGiteeDirectory(path: String) {
        try {
            val gitkeepPath = "$path/.gitkeep"
            val url = URL("https://gitee.com/api/v5/repos/$giteeOwner/$giteeRepo/contents/$gitkeepPath")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = "{\"access_token\": \"${getGiteeToken()}\", \"message\": \"Create directory $path\"}"
            conn.outputStream.write(body.toByteArray())
            conn.outputStream.flush()
            conn.disconnect()
        } catch (e: Exception) {
            // 忽略目录创建错误
        }
    }

    /**
     * 更新索引文件
     */
    private suspend fun updateIndexFile(userDir: String, dateStr: String) {
        // GitHub 索引
        try {
            val indexPath = "$userDir/data/summary_latest.json"
            val today = Date()
            val monthStr = monthFormat.format(today)

            val indexData = buildString {
                append("{")
                append("\"date\": \"$dateStr\",")
                append("\"user\": \"${getUserDisplayName()}\",")
                append("\"deviceId\": \"${getDeviceId()}\",")
                append("\"currentMonth\": \"$monthStr\"")
                append("}")
            }

            val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/contents/$indexPath")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "token ${getGitHubToken()}")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val existingSha = getGitHubFileSha(indexPath)

            val body = buildString {
                append("{")
                append("\"message\": \"Update index\",")
                append("\"content\": \"${Base64.encodeToString(indexData.toByteArray(), Base64.NO_WRAP)}\"")
                if (existingSha != null) {
                    append(",\"sha\": \"$existingSha\"")
                }
                append("}")
            }

            conn.outputStream.write(body.toByteArray())
            conn.outputStream.flush()
            conn.disconnect()
        } catch (e: Exception) {
            // 忽略索引更新错误
        }
    }

    /**
     * 获取上次同步时间
     */
    fun getLastSyncTime(): Long {
        return prefs.getLong("last_sync_time", 0)
    }

    /**
     * 保存同步时间
     */
    fun saveSyncTime(time: Long) {
        prefs.edit().putLong("last_sync_time", time).apply()
    }

    /**
     * 格式化同步时间
     */
    fun formatLastSyncTime(): String {
        val lastSync = getLastSyncTime()
        return if (lastSync == 0L) {
            "从未同步"
        } else {
            val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            format.format(Date(lastSync))
        }
    }

    data class SyncResult(
        val success: Boolean,
        val githubSuccess: Boolean,
        val giteeSuccess: Boolean,
        val message: String
    )

    data class SyncPlatformResult(
        val success: Boolean,
        val message: String
    )
}
