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
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Checks that stopping actually ends a synthesis in progress.
 *
 * espeak_Cancel() cannot do this in the Android build -- its body is entirely
 * #if USE_ASYNC, which the build disables -- so the JNI layer has to return
 * SYNTH_ABORT from the synthesis callback instead. Without that, a stop waits
 * for the whole utterance to be synthesized, which is silence for as long as
 * the text the user just moved away from.
 */
@RunWith(AndroidJUnit4::class)
class SynthesisStopTest : TextToSpeechTestCase() {

    private var dataCallbacks = 0
    private var completeCallbacks = 0
    private var synth: SpeechSynthesis? = null
    private var stopOnFirstBuffer = false

    private val callback = object : SpeechSynthesis.SynthReadyCallback {
        override fun onSynthDataReady(audioData: ByteArray) {
            dataCallbacks++
            if (stopOnFirstBuffer && dataCallbacks == 1) {
                // Stopping from inside the callback is what TtsService does
                // when the framework stops accepting audio, and it makes the
                // test deterministic: the flag is set before the next buffer is
                // asked for, so no timing window is involved.
                synth!!.stop()
            }
        }

        override fun onSynthDataComplete() {
            completeCallbacks++
        }

        override fun onSynthWordBoundary(textPosition: Int, textLength: Int, markerInFrames: Int) {
        }
    }

    /** Synthesizes [text] with the en voice, optionally stopping partway. */
    private fun synthesize(text: String, stopOnFirstBuffer: Boolean) {
        dataCallbacks = 0
        completeCallbacks = 0
        this.stopOnFirstBuffer = stopOnFirstBuffer

        val engine = synth ?: SpeechSynthesis(context, callback).also { created ->
            synth = created
            // Match on locale rather than voice name, which varies between
            // eSpeak releases.
            val en = created.getAvailableVoices().firstOrNull { it.match(ENGLISH) != TextToSpeech.LANG_NOT_SUPPORTED }
            assertThat(en, `is`(notNullValue()))
            created.setVoice(en!!, VoiceVariant.parseVoiceVariant(VoiceVariant.MALE)!!)
        }

        // synthesize() blocks until eSpeak has finished with this request.
        engine.synthesize(text, false)
    }

    @Test
    fun runToCompletionTakesManyBuffers() {
        // Establishes what "not stopped" looks like, so the stopped case below
        // is measured against something rather than an arbitrary constant.
        synthesize(LONG_TEXT, false)

        assertThat(dataCallbacks, `is`(greaterThan(5)))
        assertThat(completeCallbacks, `is`(1))
    }

    @Test
    fun stoppingEndsSynthesisImmediately() {
        synthesize(LONG_TEXT, true)

        // The buffer that triggered the stop is the only one delivered; the
        // next callback finds the flag set and aborts.
        assertThat(dataCallbacks, `is`(1))
    }

    @Test
    fun stoppingStillReportsCompletion() {
        synthesize(LONG_TEXT, true)

        // eSpeak returns ENS_SPEECH_STOPPED without a final NULL-buffer
        // callback, so the JNI layer has to report completion itself. Without
        // it TtsService never calls done() and the framework treats the
        // request as still running.
        assertThat(completeCallbacks, `is`(1))
    }

    @Test
    fun stoppingDoesNotAffectTheNextRequest() {
        synthesize(LONG_TEXT, true)
        assertThat(dataCallbacks, `is`(1))

        // The stop flag is cleared per espeak_Synth call, so a later request
        // must run to completion again.
        synthesize(LONG_TEXT, false)

        assertThat(dataCallbacks, `is`(greaterThan(5)))
        assertThat(completeCallbacks, `is`(1))
    }

    private companion object {
        val ENGLISH = Locale("en")

        /** Long enough that a run to completion takes many buffers, not one or two. */
        const val LONG_TEXT =
            "This is a long sentence used to check that stopping works. " +
                "It has to run for long enough that a synthesis carried through " +
                "to the end is clearly distinguishable from one that was cut off " +
                "after the very first buffer of audio was handed over. " +
                "Otherwise the test would pass whether or not the abort happened."
    }
}
