/*
 * Copyright (C) 2026 Harith Alhamdani
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

/**
 * Reads numbers longer than [MIN_DIGITS] digits in groups, such as phone
 * numbers and codes, by putting a space after every group of digits counted
 * from the left: 1234567 in pairs is read as "12 34 56 7". Shorter numbers,
 * and digits separated by anything else, are left alone.
 */
object NumberGrouping {
    /** Numbers with more digits than this are grouped. */
    const val MIN_DIGITS = 6

    /**
     * Groups the long numbers of [text] into [size] digits each, or returns
     * null when [size] is 0 or nothing changes. The offset map points each
     * inserted space at the digit after it.
     */
    fun group(text: String, size: Int): UnicodeNormalization.Result? {
        if (size <= 0) return null
        var out: StringBuilder? = null
        var offsets: IntArray? = null
        var count = 0
        var i = 0
        while (i < text.length) {
            var end = i
            while (end < text.length && Character.isDigit(text[end])) end++
            val digits = end - i
            if (digits > MIN_DIGITS) {
                if (out == null) {
                    out = StringBuilder(text.length + text.length / size + 1).append(text, 0, i)
                    offsets = IntArray(text.length + text.length / size + 2)
                    for (k in 0 until i) offsets[count++] = k
                }
                for (k in i until end) {
                    if (k > i && (k - i) % size == 0) {
                        out.append(' ')
                        offsets = ensure(offsets!!, count)
                        offsets[count++] = k
                    }
                    out.append(text[k])
                    offsets = ensure(offsets!!, count)
                    offsets[count++] = k
                }
                i = end
            } else {
                val stop = if (digits > 0) end else i + 1
                out?.append(text, i, stop)
                if (out != null) {
                    for (k in i until stop) {
                        offsets = ensure(offsets!!, count)
                        offsets[count++] = k
                    }
                }
                i = stop
            }
        }
        out ?: return null
        offsets = ensure(offsets!!, count)
        offsets[count++] = text.length
        return UnicodeNormalization.Result(out.toString(), offsets.copyOf(count))
    }

    private fun ensure(offsets: IntArray, count: Int): IntArray =
        if (count < offsets.size) offsets else offsets.copyOf(offsets.size * 2)
}
