/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2026 Reece H. Dunn
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

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.SpeechSynthesis
import com.espeakz.app.VoiceVariant
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Checks the word boundary events that back SynthesisCallback.rangeStart.
 *
 * eSpeak reports word positions as 1-based Unicode code point indices, while
 * the Android API expects 0-based UTF-16 indices, so the interesting cases are
 * text outside the Basic Multilingual Plane and text with multi-byte
 * characters.
 */
@RunWith(AndroidJUnit4::class)
class WordBoundaryTest : TextToSpeechTestCase() {

    private class Word(val position: Int, val length: Int, val marker: Int)

    private val words = ArrayList<Word>()

    private val callback = object : SpeechSynthesis.SynthReadyCallback {
        override fun onSynthDataReady(audioData: ByteArray) {
        }

        override fun onSynthDataComplete() {
        }

        override fun onSynthWordBoundary(textPosition: Int, textLength: Int, markerInFrames: Int) {
            words.add(Word(textPosition, textLength, markerInFrames))
        }
    }

    /** Synthesizes [text] with the en voice and returns the words reported. */
    private fun wordsFor(text: String): List<Word> {
        words.clear()

        val synth = SpeechSynthesis(context, callback)
        // Match on locale rather than voice name, which varies between eSpeak
        // releases.
        val en = synth.getAvailableVoices().firstOrNull { it.match(ENGLISH) != TextToSpeech.LANG_NOT_SUPPORTED }
        assertThat(en, `is`(notNullValue()))

        synth.setVoice(en!!, VoiceVariant.parseVoiceVariant(VoiceVariant.MALE)!!)
        // synthesize() blocks until eSpeak has finished, so every event for
        // this request has been delivered by the time it returns.
        synth.synthesize(text, false)
        return ArrayList(words)
    }

    @Test
    fun ascii() {
        val text = "Hello world again"
        val words = wordsFor(text)

        assertThat(words.size, `is`(3))
        assertThat(substringFor(text, words[0]), `is`("Hello"))
        assertThat(substringFor(text, words[1]), `is`("world"))
        assertThat(substringFor(text, words[2]), `is`("again"))
    }

    @Test
    fun multiByteCharactersAreCountedAsOne() {
        // Each accented letter is two UTF-8 bytes but a single code point, so
        // a byte-based reading of text_position would drift on the later
        // words.
        val text = "héllo wörld tëst"
        val words = wordsFor(text)

        assertThat(words.size, `is`(3))
        assertThat(substringFor(text, words[0]), `is`("héllo"))
        assertThat(substringFor(text, words[1]), `is`("wörld"))
        assertThat(substringFor(text, words[2]), `is`("tëst"))
    }

    @Test
    fun surrogatePairsAreNotSplit() {
        // The emoji is one code point but two UTF-16 units. Treating eSpeak's
        // position as a UTF-16 index would yield half a surrogate pair.
        val text = "ab 😀 cd"
        val words = wordsFor(text)

        assertThat(words.size, greaterThanOrEqualTo(2))
        assertThat(substringFor(text, words[0]), `is`("ab"))
        assertThat(substringFor(text, words[1]), `is`("😀"))
        // The last word must still land on "cd" despite the surrogate pair.
        assertThat(substringFor(text, words[words.size - 1]), `is`("cd"))
    }

    @Test
    fun markersAreNonDecreasingAndStartAtZero() {
        val words = wordsFor("Hello world again")

        assertThat(words.isEmpty(), `is`(false))
        assertThat(words[0].marker, `is`(0))
        for (i in 1 until words.size) {
            assertThat(words[i].marker, greaterThanOrEqualTo(words[i - 1].marker))
        }
    }

    @Test
    fun markersRestartForEachRequest() {
        wordsFor("Hello world again")
        // frames_delivered is reset per espeak_Synth call, so a second request
        // must not continue counting from the first.
        val words = wordsFor("Hello world again")

        assertThat(words.isEmpty(), `is`(false))
        assertThat(words[0].marker, `is`(0))
    }

    private companion object {
        val ENGLISH = Locale("en")

        /**
         * Applies the same conversion as TtsService: eSpeak's 1-based code
         * point position and code point length to a UTF-16 substring.
         */
        fun substringFor(text: String, word: Word): String {
            val start = text.offsetByCodePoints(0, word.position - 1)
            val end = text.offsetByCodePoints(start, word.length)
            return text.substring(start, end)
        }
    }
}
