package com.cloudmelody.util

import com.cloudmelody.model.LyricLine
import com.cloudmelody.model.LyricResult

/**
 * LRC / YLYRIC 歌词解析器
 *
 * Fixed:
 * - Handles single-line LRC format ([mm:ss.xx]text)
 * - Handles multi-tag lines (e.g. [00:12.34][01:23.45]text)
 * - Sorts lines by timestamp
 * - Merges translated lyrics (from [tl:...] tags or tlyric)
 * - Falls back to showing original-only when no translation is available
 */
object LyricParser {

    /**
     * 解析歌词结果（原始 + 翻译）为排序好的 LyricLine 列表
     */
    fun parse(result: LyricResult): List<LyricLine> {
        val originalLines = parseLrc(result.original)
        if (result.translated.isBlank()) return originalLines

        val translatedLines = parseLrc(result.translated)

        // Merge translations by matching timestamps
        val translationMap = translatedLines.associateBy { it.timeMs }
        return originalLines.map { line ->
            line.copy(translation = translationMap[line.timeMs]?.text.orEmpty())
        }
    }

    /**
     * 解析单首 LRC 内容为 LyricLine 列表
     * Handles:
     * - [mm:ss.xx]text
     * - [mm:ss]text
     * - Multi-tag lines: [00:12.00][01:30.50]repeated text
     * - Offset tags: [offset:+500] (ignored)
     * - Metadata tags: [ti:...], [ar:...], etc. (ignored)
     */
    private fun parseLrc(lrc: String): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        val offsetRegex = Regex("""\[offset:([+-]?\d+)]""")
        val offsetMs = offsetRegex.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        // Match all [mm:ss.xx] or [mm:ss.xxx] or [mm:ss]
        val tagRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

        lrc.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ti:") ||
                trimmed.startsWith("[ar:") || trimmed.startsWith("[al:") ||
                trimmed.startsWith("[by:") || trimmed.startsWith("[offset:") ||
                trimmed.startsWith("[tl:") || trimmed.startsWith("[tr:")
            ) return@forEach

            val matches = tagRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) return@forEach

            // Get text after the last tag
            val lastMatch = matches.last()
            val text = trimmed.substringAfter(lastMatch.value).trim().takeIf { it.isNotBlank() }
                ?.replace("\\n", "\n") ?: return@forEach

            // Emit one line per time tag (supports repeated tags) with offset adjustment
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3].takeIf { it.isNotEmpty() }
                    ?.let { frac ->
                        val normalized = frac.padEnd(3, '0').take(3)
                        normalized.toLong()
                    } ?: 0L

                val timeMs = (minutes * 60L + seconds) * 1000L + fraction + offsetMs

                lines.add(LyricLine(timeMs = timeMs, text = text))
            }
        }

        return lines.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
    }

    /**
     * 根据播放位置查找当前应高亮的歌词行索引
     */
    fun findCurrentLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        return lines.indexOfLast { it.timeMs <= positionMs }
    }
}
