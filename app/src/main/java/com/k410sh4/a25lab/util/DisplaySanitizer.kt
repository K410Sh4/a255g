package com.k410sh4.a25lab.util

import java.util.Locale

object DisplaySanitizer {
    fun safeSingleLine(
        input: String,
        maxCodePoints: Int,
    ): String = safeText(
        input = input,
        maxCodePoints = maxCodePoints,
    )
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')

    fun safeText(
        input: String,
        maxCodePoints: Int,
    ): String {
        require(maxCodePoints > 0)

        val output = StringBuilder(
            minOf(input.length, maxCodePoints),
        )
        var index = 0
        var count = 0

        while (index < input.length && count < maxCodePoints) {
            val codePoint = input.codePointAt(index)
            if (isUnsafeForDisplay(codePoint)) {
                if (codePoint <= 0xFFFF) {
                    output.append(
                        String.format(
                            Locale.ROOT,
                            "\\u%04X",
                            codePoint,
                        ),
                    )
                } else {
                    output.append(
                        String.format(
                            Locale.ROOT,
                            "\\u{%X}",
                            codePoint,
                        ),
                    )
                }
            } else {
                output.appendCodePoint(codePoint)
            }

            index += Character.charCount(codePoint)
            count++
        }

        if (index < input.length) {
            output.append('…')
        }

        return output.toString()
    }

    private fun isUnsafeForDisplay(codePoint: Int): Boolean {
        if (codePoint == '\n'.code ||
            codePoint == '\r'.code ||
            codePoint == '\t'.code
        ) {
            return false
        }

        if (codePoint in 0x0000..0x001F ||
            codePoint in 0x007F..0x009F
        ) {
            return true
        }

        return codePoint == 0x061C ||
            codePoint == 0x200B ||
            codePoint == 0x200E ||
            codePoint == 0x200F ||
            codePoint in 0x2028..0x202E ||
            codePoint == 0x2060 ||
            codePoint in 0x2066..0x2069 ||
            codePoint == 0xFEFF
    }
}
