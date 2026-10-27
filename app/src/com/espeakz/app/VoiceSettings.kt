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

package com.espeakz.app

import android.content.SharedPreferences
import org.json.JSONObject

class VoiceSettings(private val preferences: SharedPreferences, private val engine: SpeechSynthesis) {

    /**
     * The saved variant. Without one, the old default_gender setting decides
     * when it is present, and otherwise EspeakZ's default, Klatt 6.
     */
    val voiceVariant: VoiceVariant
        get() {
            val variant = preferences.getString(PREF_VARIANT, null)
            if (variant != null) {
                VoiceVariant.parseVoiceVariant(variant)?.let { return it }
            }
            val name = when {
                !preferences.contains(PREF_DEFAULT_GENDER) -> VoiceVariant.DEFAULT
                getPreferenceValue(PREF_DEFAULT_GENDER, SpeechSynthesis.GENDER_MALE) == SpeechSynthesis.GENDER_FEMALE -> VoiceVariant.FEMALE
                else -> VoiceVariant.MALE
            }
            return VoiceVariant.parseVoiceVariant(name)!!
        }

    val rate: Int
        get() {
            val min = engine.Rate.minValue
            val max = engine.Rate.maxValue

            var rate = getPreferenceValue(PREF_RATE, Int.MIN_VALUE)
            if (rate == Int.MIN_VALUE) {
                rate = (getPreferenceValue(PREF_DEFAULT_RATE, 100).toFloat() / 100 * engine.Rate.defaultValue.toFloat()).toInt()
            }

            if (isRateBoostEnabled) {
                // Allow values beyond the normal espeakRATE_MAXIMUM so the
                // native engine can engage its Sonic fast path for very high
                // rates.
                rate *= RATE_BOOST_MULTIPLIER
                val boostedMax = max * RATE_BOOST_MULTIPLIER // keep within a sensible upper bound
                if (rate > boostedMax) rate = boostedMax
            } else if (rate > max) {
                rate = max
            }

            if (rate < min) rate = min
            return rate
        }

    val pitch: Int
        get() {
            var pitch = getPreferenceValue(PREF_PITCH, Int.MIN_VALUE)
            if (pitch == Int.MIN_VALUE) {
                pitch = getPreferenceValue(PREF_DEFAULT_PITCH, 100) / 2
            }
            return pitch.coerceIn(engine.Pitch.minValue, engine.Pitch.maxValue)
        }

    val pitchRange: Int
        get() = getPreferenceValue(PREF_PITCH_RANGE, engine.PitchRange.defaultValue)
            .coerceIn(engine.PitchRange.minValue, engine.PitchRange.maxValue)

    val volume: Int
        get() = getPreferenceValue(PREF_VOLUME, engine.Volume.defaultValue)
            .coerceIn(engine.Volume.minValue, engine.Volume.maxValue)

    val punctuationLevel: Int
        get() = getPreferenceValue(PREF_PUNCTUATION_LEVEL, engine.Punctuation.defaultValue)
            .coerceIn(engine.Punctuation.minValue, engine.Punctuation.maxValue)

    val punctuationCharacters: String?
        get() = preferences.getString(PREF_PUNCTUATION_CHARACTERS, null)

    val isRateBoostEnabled: Boolean
        get() = preferences.getBoolean(PREF_RATE_BOOST, false)

    /**
     * Whether to NFKC-normalize text before synthesis, so stylized Unicode
     * (e.g. 𝖇𝖔𝖑𝖉 social media "fonts") is read as words instead of being
     * spelled out codepoint by codepoint. On by default, matching NVDA's
     * speech setting and speech-dispatcher's always-on server behaviour.
     */
    val isUnicodeNormalizationEnabled: Boolean
        get() = preferences.getBoolean(PREF_UNICODE_NORMALIZATION, true)

    /** Digits per group for numbers longer than [NumberGrouping.MIN_DIGITS]; 0 reads them as they are. */
    val longNumberGroup: Int
        get() = getPreferenceValue(PREF_LONG_NUMBERS, 0).coerceIn(0, 3)

    private fun getPreferenceValue(preference: String, defaultValue: Int): Int {
        val value = preferences.getString(preference, null) ?: return defaultValue
        return value.toIntOrNull() ?: defaultValue
    }

    fun toJSON(): JSONObject {
        val settings = JSONObject()
        settings.put(PRESET_VARIANT, voiceVariant.toString())
        settings.put(PRESET_RATE, rate)
        settings.put(PRESET_PITCH, pitch)
        settings.put(PRESET_PITCH_RANGE, pitchRange)
        settings.put(PRESET_VOLUME, volume)
        settings.put(PRESET_PUNCTUATION_CHARACTERS, punctuationCharacters)
        when (punctuationLevel) {
            SpeechSynthesis.PUNCT_NONE -> settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_NONE)
            SpeechSynthesis.PUNCT_SOME -> settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_SOME)
            SpeechSynthesis.PUNCT_ALL -> settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_ALL)
        }
        return settings
    }

    companion object {
        const val PREF_DEFAULT_GENDER = "default_gender"
        const val PREF_VARIANT = "espeak_variant"
        const val PREF_DEFAULT_RATE = "default_rate"
        const val PREF_RATE = "espeak_rate"
        const val PREF_DEFAULT_PITCH = "default_pitch"
        const val PREF_PITCH = "espeak_pitch"
        const val PREF_PITCH_RANGE = "espeak_pitch_range"
        const val PREF_VOLUME = "espeak_volume"
        const val PREF_PUNCTUATION_LEVEL = "espeak_punctuation_level"
        const val PREF_PUNCTUATION_CHARACTERS = "espeak_punctuation_characters"
        const val PREF_RATE_BOOST = "espeak_rate_boost"
        const val PREF_UNICODE_NORMALIZATION = "espeak_unicode_normalization"
        const val PREF_LONG_NUMBERS = "espeakz_long_numbers"
        const val RATE_BOOST_MULTIPLIER = 3

        const val PRESET_VARIANT = "variant"
        const val PRESET_RATE = "rate"
        const val PRESET_PITCH = "pitch"
        const val PRESET_PITCH_RANGE = "pitch-range"
        const val PRESET_VOLUME = "volume"
        const val PRESET_PUNCTUATION_LEVEL = "punctuation-level"
        const val PRESET_PUNCTUATION_CHARACTERS = "punctuation-characters"

        const val PUNCTUATION_NONE = "none"
        const val PUNCTUATION_SOME = "some"
        const val PUNCTUATION_ALL = "all"
    }
}
