package com.cloudmelody.util

import com.cloudmelody.model.LyricLine

object LrcParser {

    private val TAG_REGEX = Regex("\\[(\\d{2}:\\d{2}\\.\\d{2,3})\\]")

    fun parse(lrc: String): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        for (rawLine in lrc.lines()) {
            val matches = TAG_REGEX.findAll(rawLine)
            val text = TAG_REGEX.replace(rawLine, "").trim()
            if (text.isEmpty()) continue
            for (match in matches) {
                val timeMs = TimeUtils.parseTimeTag(match.value)
                lines.add(LyricLine(timeMs = timeMs, text = text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
