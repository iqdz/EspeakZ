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

package com.espeakz.app

import android.content.SharedPreferences
import java.util.Locale
import java.util.MissingResourceException

/**
 * EspeakZ's own language: the voice it speaks with when an app asks for a
 * language EspeakZ does not have, and for every request when "use in all
 * apps" is on. Unless the user picks one, it is the device's language, with
 * plain English read as American English.
 */
object EspeakZLanguage {
    /** The chosen voice name, or [DEVICE]. */
    const val PREF_VOICE = "espeakz_default_voice"

    /** Use EspeakZ's language for every request, whatever the app asks for. */
    const val PREF_FORCE = "espeakz_force_voice"

    const val DEVICE = ""

    /** The country a language's voice comes from when the request names none, or one EspeakZ lacks. */
    private val PREFERRED_COUNTRY = mapOf("eng" to "USA", "fra" to "FRA", "por" to "PRT")

    fun isForced(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_FORCE, false)

    /** The voice EspeakZ's language setting stands for, among [voices]. */
    fun voice(prefs: SharedPreferences, voices: Collection<Voice>, device: Locale = Locale.getDefault()): Voice? {
        val chosen = prefs.getString(PREF_VOICE, null)
        if (!chosen.isNullOrEmpty()) {
            voices.firstOrNull { it.name == chosen }?.let { return it }
        }
        return deviceVoice(voices, device)
    }

    /** The voice for the device's language, else American English, else any. */
    fun deviceVoice(voices: Collection<Voice>, device: Locale = Locale.getDefault()): Voice? =
        bestVoice(voices, device) ?: bestVoice(voices, Locale.US) ?: voices.minByOrNull { it.name }

    /**
     * The voice that fits [locale] best: its country and variant, its
     * country, the language's usual country, the language with no country,
     * then any voice of the language.
     */
    fun bestVoice(voices: Collection<Voice>, locale: Locale): Voice? {
        val language = iso3Language(locale) ?: return null
        val same = voices.filter { iso3Language(it.locale) == language }.sortedBy { it.name }
        if (same.isEmpty()) return null
        val country = iso3Country(locale)
        if (!country.isNullOrEmpty()) {
            same.firstOrNull { iso3Country(it.locale) == country && it.locale.variant.equals(locale.variant, ignoreCase = true) }
                ?.let { return it }
            same.firstOrNull { iso3Country(it.locale) == country && it.locale.variant.isEmpty() }?.let { return it }
        }
        PREFERRED_COUNTRY[language]?.let { preferred ->
            same.firstOrNull { iso3Country(it.locale) == preferred && it.locale.variant.isEmpty() }?.let { return it }
        }
        return same.firstOrNull { it.locale.country.isEmpty() } ?: same.first()
    }

    private fun iso3Language(locale: Locale): String? = try {
        locale.isO3Language.takeIf { it.isNotEmpty() }
    } catch (e: MissingResourceException) {
        null
    }

    private fun iso3Country(locale: Locale): String? = try {
        locale.isO3Country
    } catch (e: MissingResourceException) {
        null
    }
}
