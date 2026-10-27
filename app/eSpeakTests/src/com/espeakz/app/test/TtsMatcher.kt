/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2014 Reece H. Dunn
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
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

object TtsMatcher {
    private fun ttsLangCode(value: Int?): String = when (value) {
        null -> "null"
        TextToSpeech.LANG_AVAILABLE -> "TextToSpeech.LANG_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> "TextToSpeech.LANG_COUNTRY_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE"
        TextToSpeech.LANG_MISSING_DATA -> "TextToSpeech.LANG_MISSING_DATA"
        TextToSpeech.LANG_NOT_SUPPORTED -> "TextToSpeech.LANG_NOT_SUPPORTED"
        else -> value.toString()
    }

    /**
     * The expected type is passed explicitly: TypeSafeMatcher otherwise finds
     * it from matchesSafely, which Kotlin compiles with a primitive int, and
     * then refuses every boxed Integer it is given.
     */
    @JvmStatic
    fun isTtsLangCode(value: Int): Matcher<Int> = object : TypeSafeMatcher<Int>(Int::class.javaObjectType) {
        override fun matchesSafely(item: Int): Boolean = item == value

        override fun describeTo(description: Description) {
            description.appendText("expected constant: ")
            description.appendValue(ttsLangCode(value))
        }

        override fun describeMismatchSafely(item: Int, description: Description) {
            description.appendText("was ")
            description.appendValue(ttsLangCode(item))
        }
    }
}
