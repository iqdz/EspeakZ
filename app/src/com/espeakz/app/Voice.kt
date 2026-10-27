/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2012-2013 Reece H. Dunn
 * Copyright (C) 2011 Google Inc.
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

import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.MissingResourceException

class Voice(
    @JvmField val name: String,
    @JvmField val identifier: String,
    @JvmField val gender: Int,
    @JvmField val age: Int,
    @JvmField val locale: Locale,
) {
    /**
     * Attempts a partial match against a query locale.
     *
     * @return A text-to-speech availability code: one of
     *         [TextToSpeech.LANG_NOT_SUPPORTED], [TextToSpeech.LANG_AVAILABLE],
     *         [TextToSpeech.LANG_COUNTRY_AVAILABLE] or
     *         [TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE].
     */
    fun match(query: Locale): Int {
        if (locale.isO3Language != query.isO3Language) {
            return TextToSpeech.LANG_NOT_SUPPORTED
        }
        try {
            if (locale.isO3Country != query.isO3Country) {
                return TextToSpeech.LANG_AVAILABLE
            }
        } catch (e: MissingResourceException) {
            return TextToSpeech.LANG_AVAILABLE
        }
        if (locale.variant != query.variant) {
            return TextToSpeech.LANG_COUNTRY_AVAILABLE
        }
        return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    override fun toString(): String {
        val ret = StringBuilder(locale.isO3Language)
        if (!locale.isO3Country.isNullOrEmpty()) {
            ret.append('-').append(locale.isO3Country)
        }
        if (!locale.variant.isNullOrEmpty()) {
            ret.append('-').append(locale.variant)
        }
        return ret.toString()
    }
}
