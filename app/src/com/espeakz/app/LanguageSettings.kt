/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2025
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

/** Which installed voices are reported to Android, as chosen in Supported languages. */
object LanguageSettings {
    const val PREF_SUPPORTED_LANGUAGES = "espeak_supported_languages"

    /** The selected voices, or null when all are selected. */
    @JvmStatic
    fun getSelectedLanguages(preferences: SharedPreferences): Set<String>? {
        if (!preferences.contains(PREF_SUPPORTED_LANGUAGES)) {
            return null // treat as "all"
        }
        val selected = preferences.getStringSet(PREF_SUPPORTED_LANGUAGES, null)
        if (selected.isNullOrEmpty()) {
            return null // missing or empty means all
        }
        return HashSet(selected)
    }

    @JvmStatic
    fun isVoiceEnabled(voice: Voice, preferences: SharedPreferences): Boolean {
        val selected = getSelectedLanguages(preferences)
        return selected == null || voice.toString() in selected
    }

    @JvmStatic
    fun filterVoices(voices: List<Voice>, preferences: SharedPreferences): List<Voice> {
        val selected = getSelectedLanguages(preferences) ?: return voices
        return voices.filter { it.toString() in selected }
    }
}
