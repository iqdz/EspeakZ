/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2013 Reece H. Dunn
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

import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.SpeechSynthesis
import com.espeakz.app.VoiceSettings
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class VoiceSettingsTest : TextToSpeechTestCase() {

    private val callback = object : SpeechSynthesis.SynthReadyCallback {
        override fun onSynthDataReady(audioData: ByteArray) {
        }

        override fun onSynthDataComplete() {
        }

        override fun onSynthWordBoundary(textPosition: Int, textLength: Int, markerInFrames: Int) {
        }
    }

    private val synth: SpeechSynthesis by lazy { SpeechSynthesis(context, callback) }

    /** Replaces every setting with [values], all stored as strings as the settings screen stores them. */
    private fun preferences(vararg values: Pair<String, String>): SharedPreferences {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit().clear()
        for ((key, value) in values) {
            editor.putString(key, value)
        }
        editor.commit()
        return prefs
    }

    /**
     * Checks every setting and its JSON form. A null expectation means the
     * engine's default value.
     */
    private fun check(
        prefs: SharedPreferences,
        variant: String = "klatt6",
        rate: Int? = null,
        pitch: Int? = null,
        pitchRange: Int? = null,
        volume: Int? = null,
        punctuationLevel: Int = SpeechSynthesis.PUNCT_NONE,
        punctuationJson: String = "none",
        punctuationCharacters: String? = null,
    ) {
        val settings = VoiceSettings(prefs, synth)
        val expectedRate = rate ?: synth.Rate.defaultValue
        val expectedPitch = pitch ?: synth.Pitch.defaultValue
        val expectedPitchRange = pitchRange ?: synth.PitchRange.defaultValue
        val expectedVolume = volume ?: synth.Volume.defaultValue

        assertThat(settings.voiceVariant.toString(), `is`(variant))
        assertThat(settings.rate, `is`(expectedRate))
        assertThat(settings.pitch, `is`(expectedPitch))
        assertThat(settings.pitchRange, `is`(expectedPitchRange))
        assertThat(settings.volume, `is`(expectedVolume))
        assertThat(settings.punctuationLevel, `is`(punctuationLevel))
        if (punctuationCharacters == null) {
            assertThat(settings.punctuationCharacters, `is`(nullValue()))
        } else {
            assertThat(settings.punctuationCharacters, `is`(punctuationCharacters))
        }

        val json = settings.toJSON()
        assertThat(json.opt(VoiceSettings.PRESET_VARIANT), `is`(instanceOf(String::class.java)))
        assertThat(json.opt(VoiceSettings.PRESET_VARIANT) as String, `is`(variant))
        assertThat(json.opt(VoiceSettings.PRESET_RATE), `is`(instanceOf(Integer::class.java)))
        assertThat(json.opt(VoiceSettings.PRESET_RATE) as Int, `is`(expectedRate))
        assertThat(json.opt(VoiceSettings.PRESET_PITCH), `is`(instanceOf(Integer::class.java)))
        assertThat(json.opt(VoiceSettings.PRESET_PITCH) as Int, `is`(expectedPitch))
        assertThat(json.opt(VoiceSettings.PRESET_PITCH_RANGE), `is`(instanceOf(Integer::class.java)))
        assertThat(json.opt(VoiceSettings.PRESET_PITCH_RANGE) as Int, `is`(expectedPitchRange))
        assertThat(json.opt(VoiceSettings.PRESET_VOLUME), `is`(instanceOf(Integer::class.java)))
        assertThat(json.opt(VoiceSettings.PRESET_VOLUME) as Int, `is`(expectedVolume))
        assertThat(json.opt(VoiceSettings.PRESET_PUNCTUATION_LEVEL), `is`(instanceOf(String::class.java)))
        assertThat(json.opt(VoiceSettings.PRESET_PUNCTUATION_LEVEL) as String, `is`(punctuationJson))
        if (punctuationCharacters == null) {
            assertThat(json.opt(VoiceSettings.PRESET_PUNCTUATION_CHARACTERS), `is`(nullValue()))
        } else {
            assertThat(json.opt(VoiceSettings.PRESET_PUNCTUATION_CHARACTERS) as String, `is`(punctuationCharacters))
        }
    }

    // No Settings (New Install)

    @Test
    fun testNoPreferences() {
        val prefs = preferences()
        check(prefs) // Klatt 6, EspeakZ's default variant
        assertThat(VoiceSettings(prefs, synth).isUnicodeNormalizationEnabled, `is`(true))
    }

    // Old Settings

    @Test
    fun testDefaultGenderMale() {
        check(preferences("default_gender" to SpeechSynthesis.GENDER_MALE.toString()), variant = "male")
    }

    @Test
    fun testDefaultGenderFemale() {
        check(preferences("default_gender" to SpeechSynthesis.GENDER_FEMALE.toString()), variant = "female")
    }

    @Test
    fun testDefaultRate() {
        val cases = listOf(
            300 to 450, // clamped to maximum value
            200 to 350,
            100 to 175, // default value
            50 to 87,
            25 to 80, // clamped to minimum value
        )
        for ((prefValue, settingValue) in cases) {
            check(preferences("default_rate" to prefValue.toString()), rate = settingValue)
        }
    }

    @Test
    fun testDefaultPitch() {
        val cases = listOf(
            250 to 100, // clamped to maximum value
            200 to 100,
            100 to 50, // default value
            50 to 25,
            0 to 0,
            -5 to 0, // clamped to minimum value
        )
        for ((prefValue, settingValue) in cases) {
            check(preferences("default_pitch" to prefValue.toString()), pitch = settingValue)
        }
    }

    // New Settings

    @Test
    fun testEspeakVariant() {
        check(preferences("espeak_variant" to "klatt2-old"), variant = "klatt2-old")
    }

    @Test
    fun testEspeakRate() {
        val cases = listOf(
            500 to 450, // clamped to maximum value
            400 to 400,
            200 to 200,
            175 to 175, // default value
            150 to 150,
            70 to 80, // clamped to minimum value
        )
        for ((prefValue, settingValue) in cases) {
            check(preferences("espeak_rate" to prefValue.toString()), rate = settingValue)
        }
    }

    @Test
    fun testEspeakPitch() {
        val cases = listOf(
            110 to 100, // clamped to maximum value
            100 to 100,
            50 to 50, // default value
            10 to 10,
            -5 to 0, // clamped to minimum value
        )
        for ((prefValue, settingValue) in cases) {
            check(preferences("espeak_pitch" to prefValue.toString()), pitch = settingValue)
        }
    }

    @Test
    fun testEspeakPitchRange() {
        val cases = listOf(
            110 to 100, // clamped to maximum value
            100 to 100,
            50 to 50, // default value
            10 to 10,
            -5 to 0, // clamped to minimum value
        )
        for ((prefValue, settingValue) in cases) {
            check(preferences("espeak_pitch_range" to prefValue.toString()), pitchRange = settingValue)
        }
    }

    @Test
    fun testEspeakVolume() {
        val cases = listOf(
            210 to 200, // clamped to maximum value
            150 to 150,
            100 to 100, // default value
            50 to 50,
            -5 to 0, // clamped to minimum value
        )
        for ((prefValue, settingValue) in cases) {
            check(preferences("espeak_volume" to prefValue.toString()), volume = settingValue)
        }
    }

    @Test
    fun testEspeakPunctuationLevel() {
        val cases = listOf(
            Triple(3, SpeechSynthesis.PUNCT_SOME, "some"), // clamped to maximum value
            Triple(2, SpeechSynthesis.PUNCT_SOME, "some"),
            Triple(1, SpeechSynthesis.PUNCT_ALL, "all"),
            Triple(0, SpeechSynthesis.PUNCT_NONE, "none"),
            Triple(-1, SpeechSynthesis.PUNCT_NONE, "none"), // clamped to minimum value
        )
        for ((prefValue, level, json) in cases) {
            check(preferences("espeak_punctuation_level" to prefValue.toString()), punctuationLevel = level, punctuationJson = json)
        }
    }

    @Test
    fun testEspeakPunctuationCharacters() {
        check(preferences("espeak_punctuation_characters" to ".?!"), punctuationCharacters = ".?!")
    }

    // Mixed (Old and New) Settings

    @Test
    fun testEspeakVariantWithDefaultGenderFemale() {
        check(
            preferences(
                "default_gender" to SpeechSynthesis.GENDER_FEMALE.toString(),
                "espeak_variant" to "klatt4",
            ),
            variant = "klatt4",
        )
    }

    @Test
    fun testEspeakRateWithDefaultRate() {
        check(preferences("default_rate" to "50", "espeak_rate" to "200"), rate = 200)
    }

    @Test
    fun testEspeakPitchWithDefaultPitch() {
        check(preferences("default_pitch" to "50", "espeak_pitch" to "75"), pitch = 75)
    }
}
