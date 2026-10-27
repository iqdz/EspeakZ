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

import android.media.AudioFormat
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.SpeechSynthesis
import com.espeakz.app.Voice
import com.espeakz.app.test.TtsMatcher.isTtsLangCode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SpeechSynthesisTest : TextToSpeechTestCase() {

    private val callback = object : SpeechSynthesis.SynthReadyCallback {
        override fun onSynthDataReady(audioData: ByteArray) {
        }

        override fun onSynthDataComplete() {
        }

        override fun onSynthWordBoundary(textPosition: Int, textLength: Int, markerInFrames: Int) {
        }
    }

    private var voices: Map<String, Voice>? = null
    private val added = HashSet<String>()
    private val removed = HashSet<String>()

    private fun getVoices(): Map<String, Voice> {
        voices?.let { return it }
        val synth = SpeechSynthesis(context, callback)
        val map = HashMap<String, Voice>()
        for (voice in synth.getAvailableVoices()) {
            val existing = map[voice.name]
            if (existing != null) {
                Log.w(TAG, "Duplicate voice name: ${voice.name} (identifiers: ${existing.identifier} and ${voice.identifier})")
                continue // keep the first voice
            }
            map[voice.name] = voice
        }

        val names = map.keys
        val expected = VoiceData.voices.map { it.name }.toSet()
        names.filterTo(added) { it !in expected }
        expected.filterTo(removed) { it !in names }
        voices = map
        return map
    }

    private fun getVoice(name: String): Voice? = getVoices()[name]

    @Test
    fun testConstruction() {
        val synth = SpeechSynthesis(context, callback)
        assertThat(synth.sampleRate, `is`(22050))
        assertThat(synth.channelCount, `is`(1))
        assertThat(synth.audioFormat, `is`(AudioFormat.ENCODING_PCM_16BIT))
    }

    @Test
    fun testJavaToIanaLanguageCode() {
        for (data in VoiceData.voices) {
            assertThat(SpeechSynthesis.getIanaLanguageCode(data.javaLanguage), `is`(data.ianaLanguage))
        }
    }

    @Test
    fun testJavaToIanaCountryCode() {
        for (data in VoiceData.voices) {
            assertThat(SpeechSynthesis.getIanaCountryCode(data.javaCountry), `is`(data.ianaCountry))
        }
    }

    @Test
    fun testAddedVoices() {
        getVoices() // Ensure that the voice data has been populated.
        if (added.isNotEmpty()) {
            Log.w(TAG, "New voices not in VoiceData: $added")
        }
    }

    @Test
    fun testRemovedVoices() {
        getVoices() // Ensure that the voice data has been populated.
        assertThat(removed.toString(), `is`("[]"))
    }

    @Test
    fun testVoiceData() {
        getVoices()
        for (data in VoiceData.voices) {
            if (data.name in removed) {
                Log.i(TAG, "Skipping the missing voice '${data.name}'")
                continue
            }

            var context = "[voice]"
            try {
                val voice = getVoice(data.name)
                assertThat(voice, `is`(notNullValue()))
                voice!!

                context = "[name]"
                assertThat(voice.name, `is`(data.name))
                context = "[identifier]"
                assertThat(voice.identifier, `is`(data.identifier))
                context = "[age]"
                assertThat(voice.age, `is`(0))
                context = "[gender]"
                assertThat(voice.gender, `is`(data.gender))
                context = "[locale:language]"
                assertThat(voice.locale.language, `is`(data.ianaLanguage))
                context = "[locale:iso3language]"
                assertThat(voice.locale.isO3Language, `is`(data.javaLanguage))
                context = "[locale:country]"
                assertThat(voice.locale.country, `is`(data.ianaCountry))
                context = "[locale:iso3country]"
                assertThat(voice.locale.isO3Country, `is`(data.javaCountry))
                context = "[locale:variant]"
                assertThat(voice.locale.variant, `is`(data.variant))
                context = "[toString]"
                assertThat(voice.toString(), `is`(data.locale))
            } catch (e: AssertionError) {
                throw VoiceData.Exception(data, context, e)
            }
        }
    }

    @Test
    fun testMatchVoiceWithLanguage() {
        val voice = getVoice("de") // language="de" country="" variant=""
        assertThat(voice, `is`(notNullValue()))
        voice!!

        assertThat(voice.match(fr), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(fr_BE), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(fr_1694acad), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(fr_FR_1694acad), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))

        assertThat(voice.match(de), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        assertThat(voice.match(de_1996), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))
        assertThat(voice.match(de_DE), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(de_CH_1901), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))

        assertThat(voice.match(deu), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        assertThat(voice.match(deu_1996), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))
        assertThat(voice.match(deu_DEU), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(deu_CHE_1901), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
    }

    @Test
    fun testMatchVoiceWithLanguageAndCountry() {
        val voice = getVoice("fr-be") // language="fr" country="BE" variant=""
        assertThat(voice, `is`(notNullValue()))
        voice!!

        assertThat(voice.match(de), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(de_1996), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(de_DE), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(de_CH_1901), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))

        assertThat(voice.match(fr), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(fr_FR), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(fr_BE), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        assertThat(voice.match(fr_1694acad), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(fr_FR_1694acad), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(fr_BE_1694acad), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))

        assertThat(voice.match(fra), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(fra_FRA), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(fra_BEL), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        assertThat(voice.match(fra_1694acad), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(fra_FRA_1694acad), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(fra_BEL_1694acad), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))
    }

    @Test
    fun testMatchVoiceWithLanguageCountryAndVariant() {
        val voice = getVoice("en-gb-scotland") // language="en" country="GB" variant="scotland"
        assertThat(voice, `is`(notNullValue()))
        voice!!

        assertThat(voice.match(de), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(de_1996), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(de_DE), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))
        assertThat(voice.match(de_CH_1901), isTtsLangCode(TextToSpeech.LANG_NOT_SUPPORTED))

        assertThat(voice.match(en), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(en_GB), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))
        assertThat(voice.match(en_US), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(en_scotland), isTtsLangCode(TextToSpeech.LANG_AVAILABLE)) // NOTE: Android does not support LANG_VAR_AVAILABLE.
        assertThat(voice.match(en_GB_scotland), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        assertThat(voice.match(en_GB_north), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))

        assertThat(voice.match(eng), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(eng_GBR), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))
        assertThat(voice.match(eng_USA), isTtsLangCode(TextToSpeech.LANG_AVAILABLE))
        assertThat(voice.match(eng_scotland), isTtsLangCode(TextToSpeech.LANG_AVAILABLE)) // NOTE: Android does not support LANG_VAR_AVAILABLE.
        assertThat(voice.match(eng_GBR_scotland), isTtsLangCode(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
        assertThat(voice.match(eng_GBR_north), isTtsLangCode(TextToSpeech.LANG_COUNTRY_AVAILABLE))
    }

    @Test
    fun testGetSampleText() {
        // Sample text comes from Android locale resources and varies across
        // API levels (different translations, display names, etc.). Verify
        // the API returns non-empty text and doesn't corrupt the locale state.
        getVoices()
        val currentLocales = context.resources.assets.locales
        for (data in VoiceData.voices) {
            if (data.name in removed) {
                Log.i(TAG, "Skipping the missing voice '${data.name}'")
                continue
            }

            var context: String? = null
            try {
                val ianaLocale = Locale(data.ianaLanguage, data.ianaCountry, data.variant)
                context = "[iana:sample-text]"
                var sampleText = SpeechSynthesis.getSampleText(this.context, ianaLocale)
                assertThat(sampleText.length, `is`(greaterThan(0)))
                context = "[iana:resource-locale]"
                assertThat(this.context.resources.assets.locales, `is`(currentLocales))

                if (data.javaLanguage != "") {
                    val javaLocale = Locale(data.javaLanguage, data.javaCountry, data.variant)
                    context = "[java:sample-text]"
                    sampleText = SpeechSynthesis.getSampleText(this.context, javaLocale)
                    assertThat(sampleText.length, `is`(greaterThan(0)))
                    context = "[java:resource-locale]"
                    assertThat(this.context.resources.assets.locales, `is`(currentLocales))
                }
            } catch (e: AssertionError) {
                throw VoiceData.Exception(data, context, e)
            }
        }
    }

    private companion object {
        const val TAG = "SpeechSynthesisTest"

        val de = Locale("de") // German
        val de_DE = Locale("de", "DE") // German (Germany)
        val de_1996 = Locale("de", "", "1996") // German (1996 Orthography)
        val de_CH_1901 = Locale("de", "CH", "1901") // German (Traditional Orthography,Switzerland)

        val deu = Locale("deu") // German
        val deu_DEU = Locale("deu", "DEU") // German (Germany)
        val deu_1996 = Locale("deu", "", "1996") // German (1996 Orthography)
        val deu_CHE_1901 = Locale("deu", "CHE", "1901") // German (Traditional Orthography,Switzerland)

        val fr = Locale("fr") // French
        val fr_FR = Locale("fr", "FR") // French (France)
        val fr_BE = Locale("fr", "BE") // French (Belgium)
        val fr_1694acad = Locale("fr", "", "1694acad") // French (Early Modern French)
        val fr_FR_1694acad = Locale("fr", "FR", "1694acad") // French (Early Modern French,France)
        val fr_BE_1694acad = Locale("fr", "BE", "1694acad") // French (Early Modern French,Belgium)

        val fra = Locale("fra") // French
        val fra_FRA = Locale("fra", "FRA") // French (France)
        val fra_BEL = Locale("fra", "BEL") // French (Belgium)
        val fra_1694acad = Locale("fra", "", "1694acad") // French (Early Modern French)
        val fra_FRA_1694acad = Locale("fra", "FRA", "1694acad") // French (Early Modern French,France)
        val fra_BEL_1694acad = Locale("fra", "BEL", "1694acad") // French (Early Modern French,Belgium)

        val en = Locale("en") // English
        val en_GB = Locale("en", "GB") // English (Great Britain)
        val en_US = Locale("en", "US") // English (USA)
        val en_scotland = Locale("en", "", "scotland") // English (Scottish)
        val en_GB_scotland = Locale("en", "GB", "scotland") // English (Scottish,Great Britain)
        val en_GB_north = Locale("en", "GB", "north") // English (North,Great Britain)

        val eng = Locale("en") // English
        val eng_GBR = Locale("en", "GBR") // English (Great Britain)
        val eng_USA = Locale("en", "USA") // English (USA)
        val eng_scotland = Locale("en", "", "scotland") // English (Scottish)
        val eng_GBR_scotland = Locale("en", "GBR", "scotland") // English (Scottish,Great Britain)
        val eng_GBR_north = Locale("en", "GBR", "north") // English (North,Great Britain)
    }
}
