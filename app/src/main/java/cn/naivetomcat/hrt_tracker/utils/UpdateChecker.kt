package cn.naivetomcat.hrt_tracker.utils

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * 最新 Release 信息
 */
data class ReleaseInfo(
    val tagName: String,
    val releaseUrl: String
)

/**
 * GitHub Release 更新检查工具
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val API_URL =
        "https://api.github.com/repos/NaiveTomcat/HRTTracker/releases/latest"

    /**
     * 从 GitHub API 获取最新 Release 信息（网络操作，需在 IO 线程调用）
     */
    fun fetchLatestRelease(): ReleaseInfo? {
        return try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            val obj = Json.parseToJsonElement(response).jsonObject
            val tagName = obj["tag_name"]?.jsonPrimitive?.content ?: return null
            val releaseUrl = obj["html_url"]?.jsonPrimitive?.content ?: return null
            ReleaseInfo(tagName = tagName, releaseUrl = releaseUrl)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch latest release", e)
            null
        }
    }

    /**
     * 判断远端版本是否比当前版本更新
     *
     * @param tagName GitHub Release 的 tag（仅含数字和小数点，如 "v1.0.2"）
     * @param currentVersionName 当前应用版本名（如 "v1.0.1-43-g634d692-debug" 或 "v1.0.1"）
     */
    fun isNewerVersion(tagName: String, currentVersionName: String): Boolean {
        val current = currentVersionName.split('-')[0].removePrefix("v")
        val remote = tagName.removePrefix("v")
        return compareVersions(remote, current) > 0
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
