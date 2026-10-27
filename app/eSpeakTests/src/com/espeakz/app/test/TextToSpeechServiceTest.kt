/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2015 Reece H. Dunn
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
import android.preference.PreferenceManager
import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.LanguageSettings
import com.espeakz.app.TtsService
import com.espeakz.app.Voice
import com.espeakz.app.test.TtsMatcher.isTtsLangCode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class TextToSpeechServiceTest {

    inner class TtsServiceTest(context: Context) : TtsService() {
        init {
            attachBaseContext(context)
        }

        public override fun onGetLanguage(): Array<String> = super.onGetLanguage()

        val activeVoice: Voice?
            get() = matchingVoice

        public override fun selectLanguageWithFallback(language: String?, country: String?, variant: String?): Int =
            super.selectLanguageWithFallback(language, country, variant)

        fun rebuildAvailableVoicesNow() {
            rebuildAvailableVoices()
        }

        fun getVoice(name: String): android.speech.tts.Voice? = onGetVoices().firstOrNull { it.name == name }
    }

    private lateinit var service: TtsServiceTest
    private lateinit var context: Context

    @Before
    fun setUp() {
        TestLanguages.installAll()
        context = TestLanguages.storage
        service = TtsServiceTest(context)
        service.onCreate()
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(LanguageSettings.PREF_SUPPORTED_LANGUAGES)
            .commit()
        service.onDestroy()
    }

    private fun checkLanguage(locale: Array<String>, language: String, country: String, variant: String) {
        assertThat(locale.size, `is`(3))
        assertThat(locale[0], `is`(language))
        assertThat(locale[1], `is`(country))
        assertThat(locale[2], `is`(variant))
    }

    private fun checkActiveVoice(name: String) {
        assertThat(service.activeVoice, `is`(notNullValue()))
        assertThat(service.activeVoice!!.name, `is`(name))
    }

    @Test
    fun testOnLoadLanguage() {
        // English with no country is American English.
        assertThat(service.onLoadLanguage("eng", "", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(service.activeVoice, `is`(notNullValue()))
        assertThat(service.activeVoice!!.name, `is`("en-us"))
        val defaultEnName = service.activeVoice!!.name

        assertThat(service.onLoadLanguage("eng", "USA", ""), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        checkLanguage(service.onGetLanguage(), "eng", "USA", "")
        checkActiveVoice("en-us")

        assertThat(service.onLoadLanguage("eng", "GBR", "scotland"), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        checkLanguage(service.onGetLanguage(), "eng", "GBR", "scotland")
        checkActiveVoice("en-gb-scotland")

        assertThat(service.onLoadLanguage("eng", "USA", "rp"), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))
        checkLanguage(service.onGetLanguage(), "eng", "USA", "")
        checkActiveVoice("en-us")

        assertThat(service.onLoadLanguage("eng", "", "scotland"), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkActiveVoice(defaultEnName)

        assertThat(service.onLoadLanguage("eng", "FRA", "rp"), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkActiveVoice(defaultEnName)

        assertThat(service.onLoadLanguage("eng", "FRA", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkActiveVoice(defaultEnName)

        // Genuinely unsupported languages fall back to the previously loaded
        // voice and report LANG_AVAILABLE so screen readers don't skip the
        // engine.
        assertThat(service.onLoadLanguage("ine", "", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkActiveVoice(defaultEnName)
    }

    /** Checks a query leaves the loaded Vietnamese (central) voice in place. */
    private fun checkVietnameseKept() {
        checkLanguage(service.onGetLanguage(), "vie", "VNM", "central")
        checkActiveVoice("vi-vn-x-central")
    }

    @Test
    fun testOnIsLanguageAvailable() {
        assertThat(service.onLoadLanguage("vie", "VNM", "central"), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))

        assertThat(service.onIsLanguageAvailable("eng", "", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkVietnameseKept()
        assertThat(service.onIsLanguageAvailable("eng", "USA", ""), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        checkVietnameseKept()
        assertThat(service.onIsLanguageAvailable("eng", "GBR", "scotland"), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        checkVietnameseKept()
        assertThat(service.onIsLanguageAvailable("eng", "USA", "rp"), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))
        checkVietnameseKept()
        assertThat(service.onIsLanguageAvailable("eng", "", "scotland"), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkVietnameseKept()
        assertThat(service.onIsLanguageAvailable("eng", "FRA", "rp"), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkVietnameseKept()
        assertThat(service.onIsLanguageAvailable("eng", "FRA", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkVietnameseKept()

        // Genuinely unsupported languages report LANG_AVAILABLE so that
        // screen readers don't skip the engine when other voices are loaded.
        assertThat(service.onIsLanguageAvailable("ine", "", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkVietnameseKept()
    }

    @Test
    fun testOnGetDefaultVoiceNameFor() {
        assertThat(service.onLoadLanguage("vie", "VNM", "central"), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))

        // English with no country is American English.
        assertThat(service.onGetDefaultVoiceNameFor("eng", "", ""), `is`("en-us"))
        checkVietnameseKept()

        assertThat(service.onGetDefaultVoiceNameFor("eng", "USA", ""), `is`("en-us"))
        checkVietnameseKept()
        assertThat(service.onGetDefaultVoiceNameFor("eng", "GBR", "scotland"), `is`("en-gb-scotland"))
        checkVietnameseKept()
        assertThat(service.onGetDefaultVoiceNameFor("eng", "USA", "rp"), `is`("en-us"))
        checkVietnameseKept()
        assertThat(service.onGetDefaultVoiceNameFor("eng", "", "scotland"), `is`("en-us"))
        checkVietnameseKept()
        assertThat(service.onGetDefaultVoiceNameFor("eng", "FRA", "rp"), `is`("en-us"))
        checkVietnameseKept()
        assertThat(service.onGetDefaultVoiceNameFor("eng", "FRA", ""), `is`("en-us"))
        checkVietnameseKept()
        assertThat(service.onGetDefaultVoiceNameFor("ine", "", ""), `is`(nullValue()))
        checkVietnameseKept()
    }

    @Test
    fun testLanguages() {
        for (data in VoiceData.voices) {
            assertThat(
                service.onIsLanguageAvailable(data.javaLanguage, data.javaCountry, data.variant),
                isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE),
            )
            assertThat(service.onGetDefaultVoiceNameFor(data.javaLanguage, data.javaCountry, data.variant), `is`(data.name))
            assertThat(service.onLoadVoice(data.name), `is`(TextToSpeech.SUCCESS))

            val voice = service.getVoice(data.name)
            assertThat(voice, `is`(notNullValue()))

            val locale = voice!!.locale
            assertThat(locale, `is`(notNullValue()))
            assertThat(locale.isO3Language, `is`(data.javaLanguage))
            assertThat(locale.isO3Country, `is`(data.javaCountry))
            assertThat(locale.variant, `is`(data.variant))

            val features = service.onGetFeaturesForLanguage(data.javaLanguage, data.javaCountry, data.variant)
            assertThat(features, hasItem(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS))
            assertThat(features.size, `is`(1))
        }
    }

    private fun setFilteredLanguages(vararg voiceIds: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putStringSet(LanguageSettings.PREF_SUPPORTED_LANGUAGES, voiceIds.toSet())
            .commit()
        // The SharedPreferences listener is dispatched on the main thread when
        // commit() is called from a non-main thread, so the service's filter
        // would otherwise rebuild asynchronously after our assertions run.
        service.rebuildAvailableVoicesNow()
    }

    @Test
    fun testOnIsLanguageAvailable_filteredFallback() {
        // Filter to Russian only.
        setFilteredLanguages("rus")

        // English is not in the filtered set, but Russian is available. Should
        // report LANG_AVAILABLE so screen readers don't skip this engine.
        assertThat(service.onIsLanguageAvailable("eng", "", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
    }

    @Test
    fun testOnLoadLanguage_filteredFallback_freshStart() {
        // Filter to Russian only.
        setFilteredLanguages("rus")

        // Fresh start: no voice loaded yet, requesting English. Should fall
        // back to Russian and report LANG_AVAILABLE.
        assertThat(service.onLoadLanguage("eng", "", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkActiveVoice("ru")
    }

    @Test
    fun testOnLoadLanguage_filteredFallback_reusesExistingVoice() {
        // Load Russian first.
        assertThat(service.onLoadLanguage("rus", "", ""), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        checkActiveVoice("ru")

        // Now filter to Russian only.
        setFilteredLanguages("rus")

        // Request English: should keep the previously loaded Russian voice.
        assertThat(service.onLoadLanguage("eng", "", ""), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        checkActiveVoice("ru")
    }

    @Test
    fun testSelectLanguageWithFallback_filteredToRussianOnly() {
        // Filter to Russian only.
        setFilteredLanguages("rus")

        // Fresh start: requesting English should fall back to Russian.
        assertThat(service.selectLanguageWithFallback("eng", "", ""), `is`(TextToSpeech.SUCCESS))
        checkActiveVoice("ru")
    }

    @Test
    fun testSelectLanguageWithFallback_supportedLanguageStillWorks() {
        // With all languages available, requesting English should work normally.
        assertThat(service.selectLanguageWithFallback("eng", "", ""), `is`(TextToSpeech.SUCCESS))
        assertThat(service.activeVoice, `is`(notNullValue()))
        assertThat(service.activeVoice!!.name, `is`("en-us"))
    }
}
