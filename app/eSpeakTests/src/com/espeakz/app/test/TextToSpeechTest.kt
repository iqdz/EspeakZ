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

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.test.TtsMatcher.isTtsLangCode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TextToSpeechTest : TextToSpeechTestCase() {
    private var voices: Set<android.speech.tts.Voice>? = null
    private val added = HashSet<String>()
    private val removed = HashSet<String>()

    private fun getVoices(): Set<android.speech.tts.Voice> {
        voices?.let { return it }
        val voiceData = getEngine().voices
        assertThat(voiceData, `is`(notNullValue()))

        val names = voiceData.map { it.name }.toSet()
        val expected = VoiceData.voices.map { it.name }.toSet()
        names.filterTo(added) { it !in expected }
        expected.filterTo(removed) { it !in names }
        voices = voiceData
        return voiceData
    }

    private fun getVoiceData(name: String): VoiceData.Voice? = VoiceData.voices.firstOrNull { it.name == name }

    @Test
    fun testAddedVoices() {
        getVoices() // Verify voice data loads without crashing.
        // Not asserting on specific voices since new languages are regularly
        // added to eSpeak NG.
    }

    @Test
    fun testRemovedVoices() {
        getVoices() // Ensure that the voice data has been populated.
        assertThat(removed.toString(), `is`("[]"))
    }

    @Test
    fun testVoices() {
        for (voice in getVoices()) {
            val data = getVoiceData(voice.name) ?: continue // Skip voices not yet in VoiceData
            assertThat(voice.name, `is`(data.name))
            assertThat(voice.locale.language, `is`(data.ianaLanguage))
            assertThat(voice.locale.country, `is`(data.ianaCountry))
            assertThat(voice.locale.variant, `is`(data.variant))
            assertThat(voice.features, hasItem(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS))
            assertThat(voice.features.size, `is`(1))
            assertThat(voice.latency, `is`(android.speech.tts.Voice.LATENCY_VERY_LOW))
            assertThat(voice.quality, `is`(android.speech.tts.Voice.QUALITY_NORMAL))

            getEngine().voice = voice
            val language = getLanguage(getEngine())!!
            assertThat(language.language, `is`(data.javaLanguage))
            assertThat(language.country, `is`(data.javaCountry))
            assertThat(language.variant, `is`(data.variant))

            val voice2 = getEngine().voice
            assertThat(voice2, `is`(notNullValue()))
            assertThat(voice2.name, `is`(data.name))
            assertThat(voice2.locale.language, `is`(data.ianaLanguage))
            assertThat(voice2.locale.country, `is`(data.ianaCountry))
            assertThat(voice2.locale.variant, `is`(data.variant))
            assertThat(voice2.features, hasItem(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS))
            assertThat(voice2.features.size, `is`(1))
            assertThat(voice2.latency, `is`(android.speech.tts.Voice.LATENCY_VERY_LOW))
            assertThat(voice2.quality, `is`(android.speech.tts.Voice.QUALITY_NORMAL))
        }
    }

    @Test
    fun testUnsupportedLanguage() {
        val initialLocale = getLanguage(getEngine())!!
        // The engine reports LANG_AVAILABLE for unsupported languages when any
        // voice is loaded so screen readers don't skip the engine.
        assertThat(getEngine().isLanguageAvailable(Locale("cel")), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))

        val language = getLanguage(getEngine())!!
        assertThat(language.language, `is`(initialLocale.language))
        assertThat(language.country, `is`(initialLocale.country))
        assertThat(language.variant, `is`(initialLocale.variant))
    }

    private fun checkLanguage(data: VoiceData.Voice, locale: Locale, status: Int, language: String, country: String, variant: String?) {
        val langTag = locale.toString().replace('_', '-')
        var context = ""
        try {
            context = "isLanguageAvailable"
            assertThat(getEngine().isLanguageAvailable(locale), isTtsLangCode(status))
            context = "setLanguage"
            assertThat(getEngine().setLanguage(locale), isTtsLangCode(status))
            context = "getLanguage"
            val current = getLanguage(getEngine())!!
            assertThat(current.language, `is`(language))
            assertThat(current.country, `is`(country))
            if (variant != null) {
                assertThat(current.variant, `is`(variant))
            }
        } catch (e: AssertionError) {
            throw VoiceData.Exception(data, "$context|$langTag", e)
        }
    }

    @Test
    fun testLanguages() {
        for (data in VoiceData.voices) {
            val iana1 = Locale(data.ianaLanguage, data.ianaCountry, data.variant)
            val iana2 = Locale(data.ianaLanguage, data.ianaCountry, "test")
            val iana3 = Locale(data.ianaLanguage, "VU", data.variant)

            val java1 = Locale(data.javaLanguage, data.javaCountry, data.variant)
            val java2 = Locale(data.javaLanguage, data.javaCountry, "test")
            val java3 = Locale(data.javaLanguage, "VUT", data.variant)

            checkLanguage(data, iana1, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE, data.javaLanguage, data.javaCountry, data.variant)
            checkLanguage(data, java1, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE, data.javaLanguage, data.javaCountry, data.variant)

            // For LANG_COUNTRY_AVAILABLE (country matches but variant
            // doesn't), the fallback variant depends on HashMap iteration
            // order for languages with multiple regional variants (vi-VN,
            // hy-AM). Only check the availability status and language and
            // country, not the specific variant.
            checkLanguage(data, iana2, TextToSpeech.LANG_COUNTRY_AVAILABLE, data.javaLanguage, data.javaCountry, null)
            checkLanguage(data, java2, TextToSpeech.LANG_COUNTRY_AVAILABLE, data.javaLanguage, data.javaCountry, null)

            // For LANG_AVAILABLE (language-only match), the engine falls back
            // to a default voice for that language. The country and variant of
            // the fallback voice depend on HashMap iteration order, so only
            // check the language and the availability status.
            checkLanguageOnly(data, iana3)
            checkLanguageOnly(data, java3)
        }
    }

    private fun checkLanguageOnly(data: VoiceData.Voice, locale: Locale) {
        var context = ""
        try {
            context = "isLanguageAvailable"
            assertThat(getEngine().isLanguageAvailable(locale), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
            context = "setLanguage"
            assertThat(getEngine().setLanguage(locale), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
            context = "getLanguage:language"
            assertThat(getLanguage(getEngine())!!.language, `is`(data.javaLanguage))
        } catch (e: AssertionError) {
            throw VoiceData.Exception(data, context + "|" + locale.toString().replace('_', '-'), e)
        }
    }
}
