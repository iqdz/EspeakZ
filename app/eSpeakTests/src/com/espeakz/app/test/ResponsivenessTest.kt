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

package com.espeakz.app.test

import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.lessThan
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * How quickly EspeakZ answers short requests like a screen reader's, at the
 * normal speech rate and at high ones, through the TextToSpeech client API.
 * Synthesizes to a file, so nothing is played and the default engine is not
 * used. Logs under ResponsivenessTest; a debug build's EspeakZTiming lines
 * break each request down further.
 */
@RunWith(AndroidJUnit4::class)
class ResponsivenessTest {

    private var tts: TextToSpeech? = null
    private val firstAudio = AtomicLong()
    private val done = AtomicLong()
    private val bytes = AtomicInteger()
    private var finished = CountDownLatch(1)

    @Before
    fun setUp() {
        val ready = CountDownLatch(1)
        tts = TextToSpeech(TestLanguages.targetContext, { ready.countDown() }, "com.espeakz.app")
        assertThat(ready.await(10, TimeUnit.SECONDS), `is`(true))
        tts!!.language = Locale.US
        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}

            override fun onAudioAvailable(id: String, audio: ByteArray) {
                firstAudio.compareAndSet(0, SystemClock.elapsedRealtime())
                bytes.addAndGet(audio.size)
            }

            override fun onDone(id: String) {
                done.set(SystemClock.elapsedRealtime())
                finished.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String) {
                finished.countDown()
            }
        })
    }

    @After
    fun tearDown() {
        tts?.shutdown()
    }

    @Test
    fun answersQuicklyAtEveryRate() {
        val out = File(TestLanguages.targetContext.cacheDir, "responsiveness.wav")
        for (rate in listOf(1f, 2f, 3f, 4f)) {
            tts!!.setSpeechRate(rate)
            val firsts = ArrayList<Long>()
            var totalMs = 0L
            var audioMs = 0L
            for ((i, text) in PHRASES.withIndex()) {
                firstAudio.set(0)
                bytes.set(0)
                finished = CountDownLatch(1)
                val started = SystemClock.elapsedRealtime()
                tts!!.synthesizeToFile(text, Bundle(), out, "r$rate-$i")
                assertThat("utterance finished", finished.await(20, TimeUnit.SECONDS), `is`(true))
                if (i == 0) continue // the first at each rate also warms the voice
                firsts.add(firstAudio.get() - started)
                totalMs += done.get() - started
                audioMs += bytes.get() / 2 * 1000L / SAMPLE_RATE
            }
            firsts.sort()
            Log.i(
                TAG, "rate ${rate}x: first audio median ${firsts[firsts.size / 2]} ms, slowest ${firsts.last()} ms; " +
                    "$totalMs ms to make $audioMs ms of speech",
            )
            // Generous, so that only a real slowdown fails it.
            assertThat(firsts.last(), lessThan(500L))
        }
        out.delete()
    }

    private companion object {
        const val TAG = "ResponsivenessTest"
        const val SAMPLE_RATE = 22050
        val PHRASES = listOf(
            "Warming up.", "Settings", "Voice languages, button", "Speech rate, 175 words per minute",
            "Double tap to activate", "Not checked, Unicode normalization", "Back", "Home screen, 3 of 5",
            "Messages, 2 new", "Battery 80 percent", "Navigate up",
        )
    }
}
