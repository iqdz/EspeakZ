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

package com.espeakz.app.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.UnicodeNormalization
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the NFKC normalization applied to synthesis input and the offset map
 * used to keep SynthesisCallback.rangeStart anchored to the text the caller
 * supplied.
 */
@RunWith(AndroidJUnit4::class)
class UnicodeNormalizationTest {

    private fun normalized(text: String): UnicodeNormalization.Result {
        val result = UnicodeNormalization.normalize(text)
        assertThat(result, `is`(notNullValue()))
        return result!!
    }

    @Test
    fun plainTextIsAlreadyNormalized() {
        assertThat(UnicodeNormalization.normalize(""), `is`(nullValue()))
        assertThat(UnicodeNormalization.normalize("Hello, world!"), `is`(nullValue()))
        assertThat(UnicodeNormalization.normalize("héllo wörld"), `is`(nullValue()))
        // Composed text with an astral emoji is normalized too; the fast path
        // must not be confused by surrogate pairs.
        assertThat(UnicodeNormalization.normalize("ab 😀 cd"), `is`(nullValue()))
    }

    @Test
    fun mathematicalAlphanumericSymbols() {
        // The examples from eSpeak NG issue 2485.
        assertThat(normalized("𝖁𝕰𝕹𝖀𝕾").text, `is`("VENUS"))
        assertThat(normalized("𝘛𝘢𝘳𝘢").text, `is`("Tara"))
        assertThat(normalized("𝐂𝐨𝐝𝐞").text, `is`("Code"))
    }

    @Test
    fun fullwidthForms() {
        assertThat(normalized("Ｈｅｌｌｏ").text, `is`("Hello"))
    }

    @Test
    fun negativeCircledLettersUseSupplementaryMapping() {
        // U+1F150.. has no compatibility decomposition, so plain NFKC would
        // leave it alone; the supplementary mapping must kick in.
        assertThat(normalized("🅐🅑🅒").text, `is`("ABC"))
    }

    @Test
    fun negativeSquaredLettersPreserveEmoji() {
        // 🅾 has emoji semantics (blood type O) and must survive, while the
        // surrounding squared letters normalize.
        assertThat(normalized("🅲🅾🅳🅴").text, `is`("C🅾DE"))
        // Text consisting only of the excluded emoji needs no normalization.
        assertThat(UnicodeNormalization.normalize("🅰🅱🅾🅿"), `is`(nullValue()))
    }

    @Test
    fun combiningMarksCompose() {
        // The input is decomposed ("e" + combining acute); NFKC composes it
        // to a single "é", so the two literals differ despite looking alike.
        assertThat(normalized("café").text, `is`("café"))
    }

    @Test
    fun offsetsForStyledWord() {
        // "ab 𝖁𝕰𝕹𝖀𝕾 cd": each styled letter is one code point but two
        // UTF-16 units, so the original is 16 units long and the normalized
        // form ("ab VENUS cd") is 11.
        val result = normalized("ab 𝖁𝕰𝕹𝖀𝕾 cd")
        assertThat(result.text, `is`("ab VENUS cd"))

        // "ab " is untouched.
        assertThat(result.toOriginalOffset(0), `is`(0))
        assertThat(result.toOriginalOffset(3), `is`(3))
        // The word "VENUS" spans [3, 8) in the normalized text and must map
        // back to the full styled span [3, 13) in the original.
        assertThat(result.toOriginalOffset(8), `is`(13))
        // "cd" after the styled word.
        assertThat(result.toOriginalOffset(9), `is`(14))
        assertThat(result.toOriginalOffset(11), `is`(16))
    }

    @Test
    fun offsetsForExpandingLigature() {
        // "ĳ" normalizes to "ij": the text gets longer, and offsets inside
        // the expansion must not run past the original character.
        val result = normalized("ĳs")
        assertThat(result.text, `is`("ijs"))

        assertThat(result.toOriginalOffset(0), `is`(0))
        // Both the interior of the expansion and its end map to the end of
        // the ligature, so a word ending there covers it exactly.
        assertThat(result.toOriginalOffset(1), `is`(1))
        assertThat(result.toOriginalOffset(2), `is`(1))
        assertThat(result.toOriginalOffset(3), `is`(2))
    }

    @Test
    fun offsetsAreMonotonic() {
        val text = "a Ｗｉｄｅ ĳ 𝖁 b"
        val result = normalized(text)

        var previous = 0
        for (i in 0..result.text.length) {
            val offset = result.toOriginalOffset(i)
            assertThat(offset, `is`(greaterThanOrEqualTo(previous)))
            assertThat(offset, `is`(lessThanOrEqualTo(text.length)))
            previous = offset
        }
        assertThat(result.toOriginalOffset(result.text.length), `is`(text.length))
    }

    @Test
    fun outOfRangeOffsetsAreClamped() {
        val result = normalized("𝖁")
        assertThat(result.text, `is`("V"))
        assertThat(result.toOriginalOffset(-1), `is`(0))
        assertThat(result.toOriginalOffset(100), `is`(2))
    }
}
