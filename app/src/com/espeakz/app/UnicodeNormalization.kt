/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2026 Alexandr Epaneshnikov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.espeakz.app

import java.text.BreakIterator
import java.text.Normalizer

/**
 * NFKC normalization of synthesis input, so that stylized Unicode text (such
 * as the Mathematical Alphanumeric Symbols popular on social media, fullwidth
 * forms, and enclosed letters) is spoken as the words it spells rather than
 * one codepoint at a time.
 *
 * This mirrors what NVDA and speech-dispatcher do before text reaches a
 * synthesizer. Like NVDA, a supplementary mapping handles the negative
 * circled and negative squared Latin letters, which are decorative but have
 * no compatibility decomposition in Unicode.
 *
 * NFKC can change the length of the text, but word boundary events must be
 * reported against the text the caller supplied, so normalization also builds
 * an offset map from the normalized string back to the original one.
 */
object UnicodeNormalization {

    /** A normalized string together with its offset map into the original. */
    class Result internal constructor(
        /** The normalized text, to be handed to the synthesizer. */
        @JvmField val text: String,
        /**
         * Boundary map of size `text.length + 1`: entry `i` is the UTF-16
         * offset in the original string corresponding to UTF-16 offset `i` in
         * the normalized string. Within a run the normalization changed,
         * interior positions map to the end of the original run, so a word
         * ending inside it covers the whole run and mapped ranges never invert.
         */
        private val offsets: IntArray,
    ) {
        /** Maps a UTF-16 offset in [text] to one in the original string. */
        fun toOriginalOffset(offset: Int): Int = when {
            offset <= 0 -> 0
            offset >= offsets.size -> offsets[offsets.size - 1]
            else -> offsets[offset]
        }

        /**
         * This result, made from [inner]'s text, with offsets mapped on to
         * [inner]'s original, so that two changes in a row keep one map.
         */
        internal fun through(inner: Result): Result =
            Result(text, IntArray(offsets.size) { inner.toOriginalOffset(offsets[it]) })
    }

    /**
     * Normalizes [text] to NFKC, including the supplementary mapping.
     *
     * @return the normalized text with its offset map, or null when the text
     * is already normalized and can be used as-is (the common case, where
     * exact identity of offsets is guaranteed).
     */
    @JvmStatic
    fun normalize(text: String): Result? {
        if (!hasSupplementaryMappings(text) && Normalizer.isNormalized(text, Normalizer.Form.NFKC)) {
            return null
        }

        // Normalize one grapheme cluster at a time. Canonical composition
        // never crosses a cluster boundary (combining marks belong to their
        // base's cluster), so this matches whole-string NFKC for real text
        // while keeping an unambiguous offset for every cluster.
        val graphemes = BreakIterator.getCharacterInstance()
        graphemes.setText(text)

        val normalized = StringBuilder(text.length)
        var offsets = IntArray(text.length + 1)
        var offsetCount = 0

        var start = graphemes.first()
        var end = graphemes.next()
        while (end != BreakIterator.DONE) {
            val part = text.substring(start, end)
            val norm = Normalizer.normalize(applySupplementaryMappings(part), Normalizer.Form.NFKC)
            normalized.append(norm)

            if (offsetCount + norm.length >= offsets.size) {
                offsets = offsets.copyOf(maxOf(offsets.size * 2, offsetCount + norm.length + 1))
            }
            if (norm == part) {
                for (i in norm.indices) {
                    offsets[offsetCount++] = start + i
                }
            } else if (norm.isNotEmpty()) {
                offsets[offsetCount++] = start
                for (i in 1 until norm.length) {
                    offsets[offsetCount++] = end
                }
            }
            start = end
            end = graphemes.next()
        }
        offsets[offsetCount++] = text.length

        if (offsetCount != offsets.size) {
            offsets = offsets.copyOf(offsetCount)
        }
        return Result(normalized.toString(), offsets)
    }

    /**
     * Maps one decorative code point that NFKC leaves alone, or returns it
     * unchanged. Same ranges and emoji exclusions as NVDA's supplementary
     * normalization table.
     */
    private fun mapSupplementaryCodePoint(codePoint: Int): Int {
        // Negative Circled Latin Capital Letters.
        if (codePoint in 0x1F150..0x1F169) {
            return 'A'.code + (codePoint - 0x1F150)
        }
        // Negative Squared Latin Capital Letters, except the four that carry
        // emoji semantics: 🅰 🅱 🅾 🅿 (blood types and the parking sign).
        if (codePoint in 0x1F170..0x1F189) {
            return when (codePoint) {
                0x1F170, 0x1F171, 0x1F17E, 0x1F17F -> codePoint
                else -> 'A'.code + (codePoint - 0x1F170)
            }
        }
        return codePoint
    }

    private fun hasSupplementaryMappings(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (mapSupplementaryCodePoint(codePoint) != codePoint) {
                return true
            }
            i += Character.charCount(codePoint)
        }
        return false
    }

    private fun applySupplementaryMappings(part: String): String {
        var mapped: StringBuilder? = null
        var i = 0
        while (i < part.length) {
            val codePoint = part.codePointAt(i)
            val replacement = mapSupplementaryCodePoint(codePoint)
            if (replacement != codePoint && mapped == null) {
                mapped = StringBuilder(part.length)
                mapped.append(part, 0, i)
            }
            mapped?.appendCodePoint(replacement)
            i += Character.charCount(codePoint)
        }
        return mapped?.toString() ?: part
    }
}
