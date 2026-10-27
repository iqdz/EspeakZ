/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2012-2015 Reece H. Dunn
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
import android.speech.tts.TextToSpeech
import android.util.Log
import com.espeakz.app.test.TtsMatcher.isTtsLangCode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import java.util.Locale

/**
 * Base for the tests that talk to EspeakZ through Android's TextToSpeech
 * client. They need EspeakZ to be the device's default speech engine.
 */
open class TextToSpeechTestCase {
    private var engine: TextToSpeech? = null

    @Volatile private var initialised = false
    @Volatile private var status = TextToSpeech.ERROR

    protected val context: Context
        get() = TestLanguages.storage

    @Before
    open fun setUp() {
        try {
            TestLanguages.installAll()

            // Wait until the text-to-speech engine is initialised (max: 20 seconds):
            engine = TextToSpeech(context) { s ->
                status = s
                initialised = true
            }
            var count = 0
            while (!initialised && count < 4 * 20) {
                Thread.sleep(250)
                ++count
            }

            assertThat(initialised, `is`(true))
            assertThat(status, `is`(TextToSpeech.SUCCESS))
            assertThat(getEngine().defaultEngine, `is`("com.espeakz.app"))

            // Ensure that the voice data is installed (max: 20 seconds):
            val en = Locale("en")
            var available = getEngine().isLanguageAvailable(en)
            count = 0
            while (available < TextToSpeech.LANG_AVAILABLE && count < 4 * 20) {
                Thread.sleep(250)
                available = getEngine().isLanguageAvailable(en)
                Log.d("TextToSpeechTestCase", "setUp: available = $available")
                ++count
            }

            assertThat(available, isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        } catch (e: Throwable) {
            tearDown()
            throw e
        }
    }

    @After
    open fun tearDown() {
        engine?.shutdown()
        engine = null
    }

    fun getEngine(): TextToSpeech = engine!!

    companion object {
        @JvmStatic
        @Suppress("DEPRECATION")
        fun getLanguage(engine: TextToSpeech?): Locale? = engine?.language
    }
}
