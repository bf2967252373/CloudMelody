package com.cloudmelody.util

object TimeUtils {

    /** 毫秒 → mm:ss 格式 */
    fun formatMs(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /**
     * 解析 LRC 时间标签 [mm:ss.xx] / [mm:ss.xxx] / [mm:ss] → 毫秒
     * 支持任意位数小数，异常输入安全返回 0。
     */
    fun parseTimeTag(tag: String): Long {
        return try {
            val clean = tag.trim().trimStart('[').trimEnd(']')
            val colonIdx = clean.indexOf(':')
            if (colonIdx < 0) return 0L

            val dotIdx = clean.indexOfAny(charArrayOf('.', ':'), startIndex = colonIdx + 1)
                .takeIf { it > colonIdx } ?: -1

            val minutes = clean.substring(0, colonIdx).trim().toLongOrNull() ?: return 0L
            val seconds = clean.substring(
                colonIdx + 1,
                if (dotIdx >= 0) dotIdx else clean.length
            ).trim().toLongOrNull() ?: return 0L

            val millis = if (dotIdx >= 0) {
                val frac = clean.substring(dotIdx + 1).filter { it.isDigit() }
                when {
                    frac.isEmpty() -> 0L
                    frac.length == 1 -> frac.toLong() * 100L
                    frac.length == 2 -> frac.toLong() * 10L
                    frac.length == 3 -> frac.toLong()
                    else -> frac.substring(0, 3).toLong()
                }
            } else 0L

            (minutes * 60L + seconds) * 1000L + millis
        } catch (_: Exception) {
            0L
        }
    }
}
