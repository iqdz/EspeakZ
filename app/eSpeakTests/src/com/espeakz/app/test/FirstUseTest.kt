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
import com.espeakz.app.EspeakData
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The first request EspeakZ ever gets must be spoken, with no install step and
 * no turning the engine off and on.
 *
 * Meaningful when run on its own right after the app's data was cleared, as
 * on a new install: then the voice data does not exist yet when the screen
 * reader's first request arrives. Run in the full suite it still passes,
 * proving less. It binds EspeakZ by name, so the device's default speech
 * engine is left alone, and synthesizes to a file, so nothing is played.
 */
@RunWith(AndroidJUnit4::class)
class FirstUseTest {

    @Test
    fun firstRequestIsSpoken() {
        val context = TestLanguages.targetContext
        val started = SystemClock.elapsedRealtime()
        Log.i(TAG, "voice data installed before the request: ${EspeakData.isInstalled(TestLanguages.storage)}")

        val ready = CountDownLatch(1)
        val status = AtomicReference<Int>()
        val tts = TextToSpeech(context, { s ->
            status.set(s)
            ready.countDown()
        }, "com.espeakz.app")
        try {
            assertThat("TTS init timed out", ready.await(60, TimeUnit.SECONDS), `is`(true))
            assertThat("TTS init failed", status.get(), `is`(TextToSpeech.SUCCESS))
            val initialised = SystemClock.elapsedRealtime()

            val finished = CountDownLatch(1)
            val outcome = AtomicReference("TIMEOUT")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {
                }

                override fun onDone(id: String) {
                    outcome.set("DONE")
                    finished.countDown()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String) {
                    outcome.set("ERROR")
                    finished.countDown()
                }

                override fun onError(id: String, errorCode: Int) {
                    outcome.set("ERROR($errorCode)")
                    finished.countDown()
                }
            })
            val out = File(context.cacheDir, "first-use.wav")
            assertThat(tts.synthesizeToFile("Hello, this is the first thing EspeakZ says", Bundle(), out, "first"), `is`(TextToSpeech.SUCCESS))
            finished.await(30, TimeUnit.SECONDS)
            val done = SystemClock.elapsedRealtime()

            Log.i(TAG, "init ${initialised - started} ms, first utterance ${done - initialised} ms, ${out.length()} bytes, ${outcome.get()}")
            assertThat("first utterance", outcome.get(), `is`("DONE"))
            assertThat("audio was produced", out.length() > 10_000, `is`(true))
            assertThat(EspeakData.isInstalled(TestLanguages.storage), `is`(true))
        } finally {
            tts.shutdown()
        }
    }

    private companion object {
        const val TAG = "FirstUseTest"
    }
}
