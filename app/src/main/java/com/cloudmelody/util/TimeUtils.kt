package com.cloudmelody.util

object TimeUtils {

    /**
     * Format milliseconds as mm:ss (e.g. 3:45).
     */
    fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /**
     * Parse LRC time tag [mm:ss.xx] or [mm:ss.xxx] → milliseconds.
     */
    fun parseTimeTag(tag: String): Long {
        return try {
            val clean = tag.trimStart('[').trimEnd(']')
            val colonIdx = clean.indexOf(':')
            val dotIdx   = clean.indexOf('.')
            val minutes  = clean.substring(0, colonIdx).toLong()
            val seconds  = clean.substring(colonIdx + 1,
                if (dotIdx >= 0) dotIdx else clean.length).toLong()
            val millis   = if (dotIdx >= 0) {
                val frac = clean.substring(dotIdx + 1)
                when (frac.length) {
                    2 -> frac.toLong() * 10
                    3 -> frac.toLong()
                    else -> 0L
                }
            } else 0L
            (minutes * 60 + seconds) * 1000 + millis
        } catch (e: Exception) {
            0L
        }
    }
}
