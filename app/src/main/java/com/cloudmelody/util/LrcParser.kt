package com.cloudmelody.util

import com.cloudmelody.model.LyricLine

/**
 * LRC parser — supports standard [mm:ss.xx] timestamps and
 * extended [mm:ss.xxx] millisecond timestamps.
 *
 * Also merges a parallel translation LRC into the same list.
 */
object LrcParser {

    private val TIME_REGEX = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]")

    /**
     * Parse an LRC string into a sorted list of [LyricLine].
     * @param lrc      original LRC text
     * @param tLrc     optional translation LRC text
     */
    fun parse(lrc: String, tLrc: String = ""): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()

        val origMap = parseLrc(lrc)
        val transMap = if (tLrc.isNotBlank()) parseLrc(tLrc) else emptyMap()

        return origMap.map { (time, text) ->
            LyricLine(
                timeMs = time,
                text = text,
                translation = transMap[time] ?: ""
            )
        }.sortedBy { it.timeMs }
    }

    private fun parseLrc(raw: String): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        raw.lines().forEach { line ->
            val matches = TIME_REGEX.findAll(line)
            val timestamps = matches.map { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val ms  = m.groupValues[3].let {
                    if (it.length == 2) it.toLong() * 10 else it.toLong()
                }
                min * 60_000L + sec * 1_000L + ms
            }.toList()

            if (timestamps.isEmpty()) return@forEach
            // Text is everything after the last timestamp tag
            val text = line.substringAfterLast(']').trim()
            if (text.isNotEmpty()) {
                timestamps.forEach { ts -> result[ts] = text }
            }
        }
        return result
    }

    /**
     * Given sorted lyric lines and current position [posMs],
     * return the index of the currently active line.
     */
    fun findCurrentLine(lines: List<LyricLine>, posMs: Long): Int {
        if (lines.isEmpty()) return -1
        var index = 0
        for (i in lines.indices) {
            if (lines[i].timeMs <= posMs) index = i else break
        }
        return index
    }
}
