/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2026
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

import android.content.Context
import android.os.Bundle
import android.preference.PreferenceManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.LanguageSettings
import com.espeakz.app.SpeechSynthesis
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Drives the engine through the real TextToSpeech client API rather than
 * calling TtsService methods directly, so that the framework's
 * start/done/error contract is exercised the way a screen reader exercises it.
 *
 * Reproduces eSpeak NG pull request 2480: "if I pressed deselect all langs
 * button, and only checked persian, and then opened my telegram which I have
 * set it to english, reading the content was stopping".
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class LanguageFilterSynthesisTest {

    private lateinit var prefContext: Context
    private lateinit var outDir: File
    private var tts: TextToSpeech? = null

    @Before
    fun setUp() {
        val ctx = TestLanguages.targetContext
        outDir = ctx.cacheDir
        prefContext = TestLanguages.storage
        TestLanguages.installAll()
        clearFilter()

        val ready = CountDownLatch(1)
        val status = AtomicReference<Int>()
        tts = TextToSpeech(ctx, { s ->
            status.set(s)
            ready.countDown()
        }, ENGINE)

        assertThat("TTS init timed out", ready.await(INIT_TIMEOUT_S, TimeUnit.SECONDS), `is`(true))
        assertThat("TTS init failed", status.get(), `is`(TextToSpeech.SUCCESS))
    }

    @After
    fun tearDown() {
        tts?.shutdown()
        tts = null
        clearFilter()
    }

    private fun clearFilter() {
        PreferenceManager.getDefaultSharedPreferences(prefContext)
            .edit()
            .remove(LanguageSettings.PREF_SUPPORTED_LANGUAGES)
            .commit()
    }

    /** The id LanguageSettings filters on is Voice.toString(), e.g. "fas". */
    private fun persianFilterId(): String? {
        val engine = SpeechSynthesis(prefContext, null)
        for (voice in engine.getAvailableVoices()) {
            if (voice.locale.language == Locale("fa").language) {
                Log.i(TAG, "Persian voice: name=${voice.name} id=$voice")
                return voice.toString()
            }
        }
        return null
    }

    private fun filterTo(voiceId: String) {
        PreferenceManager.getDefaultSharedPreferences(prefContext)
            .edit()
            .putStringSet(LanguageSettings.PREF_SUPPORTED_LANGUAGES, setOf(voiceId))
            .commit()

        // onGetVoices() calls rebuildAvailableVoices() before returning, so
        // this forces the service to apply the new filter synchronously
        // instead of waiting on the SharedPreferences listener hop to the main
        // thread.
        val exposed = tts!!.voices.size
        Log.i(TAG, "filtered to $voiceId; engine now exposes $exposed voices")
    }

    private fun findVoiceNamed(name: String): android.speech.tts.Voice? =
        tts!!.voices.firstOrNull { it.name == name }

    /**
     * Synthesizes to a file (rather than speak()) so the result does not
     * depend on the device having a working audio device, and plays nothing.
     * Both paths land in TtsService.onSynthesizeText.
     */
    private fun synthesize(utteranceId: String): String {
        val finished = CountDownLatch(1)
        val outcome = AtomicReference(TIMEOUT)

        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                Log.i(TAG, "onStart $id")
            }

            override fun onDone(id: String) {
                outcome.set(DONE)
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

        val out = File(outDir, "$utteranceId.wav")
        val queued = tts!!.synthesizeToFile("hello world", Bundle(), out, utteranceId)
        if (queued != TextToSpeech.SUCCESS) {
            return "QUEUE_REJECTED($queued)"
        }

        finished.await(UTTERANCE_TIMEOUT_S, TimeUnit.SECONDS)
        Log.i(TAG, "utterance $utteranceId -> ${outcome.get()}")
        return outcome.get()
    }

    /**
     * Path A: the client selected a language (what TextToSpeech.setLanguage
     * does, and what the language fallback covers).
     */
    @Test
    fun testEnglishLanguageRequestAfterFilteringToPersian() {
        assertThat(tts!!.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE, `is`(true))
        assertThat("baseline English utterance failed", synthesize("lang-before"), `is`(DONE))

        val fa = persianFilterId()
        assertThat("no Persian voice found", fa, `is`(notNullValue()))
        filterTo(fa!!)

        assertThat("English utterance never completed after filtering to Persian", synthesize("lang-after"), `is`(DONE))
    }

    /**
     * Path C: the client never called setLanguage() or setVoice(). Determines
     * whether the framework attaches a voice name by default, i.e. whether
     * selectLanguageWithFallback() is reachable at all on API 21+.
     */
    @Test
    fun testDefaultRequestAfterFilteringToPersian() {
        assertThat("baseline default utterance failed", synthesize("default-before"), `is`(DONE))

        val fa = persianFilterId()
        assertThat("no Persian voice found", fa, `is`(notNullValue()))
        filterTo(fa!!)

        assertThat("default utterance never completed after filtering to Persian", synthesize("default-after"), `is`(DONE))
    }

    /**
     * Path B: the client selected a concrete voice, then the user filtered
     * that voice away. TtsService.selectVoice() sends this to onLoadVoice(),
     * which has no fallback.
     */
    @Test
    fun testEnglishVoiceRequestAfterFilteringToPersian() {
        val english = findVoiceNamed("en-us")
        assertThat("no en-us voice exposed", english, `is`(notNullValue()))
        assertThat(tts!!.setVoice(english), `is`(TextToSpeech.SUCCESS))
        assertThat("baseline English utterance failed", synthesize("voice-before"), `is`(DONE))

        val fa = persianFilterId()
        assertThat("no Persian voice found", fa, `is`(notNullValue()))
        filterTo(fa!!)

        assertThat("English voice utterance never completed after filtering to Persian", synthesize("voice-after"), `is`(DONE))
    }

    private companion object {
        const val TAG = "LangFilterTest"
        const val ENGINE = "com.espeakz.app"

        const val INIT_TIMEOUT_S = 60L
        const val UTTERANCE_TIMEOUT_S = 20L

        const val DONE = "DONE"
        const val TIMEOUT = "TIMEOUT (no onDone, no onError)"
    }
}
