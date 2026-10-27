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
import android.icu.util.ULocale
import android.os.Build
import java.util.Locale

/**
 * Dual languages: every language can have a voice of its own, from EspeakZ
 * or from any other installed engine, with its own speech rate, pitch and
 * volume. Two of them are in use at a time, one written in Latin letters,
 * such as English or French, and one in another alphabet, such as Arabic:
 * each reads the text in its alphabet.
 */
object DualLanguage {
    const val PREF_ENABLED = "espeakz_dual_enabled"

    /** The language in use for Latin text, such as "en". */
    const val PREF_LATIN = "espeakz_dual_latin"

    /** The language in use for text in other alphabets, such as "ar". */
    const val PREF_NON_LATIN = "espeakz_dual_non_latin"

    /** The language whose voice the settings page shows. */
    const val PREF_SHOWN = "espeakz_dual_shown"

    /** Where one language's voice is saved. */
    class Keys(
        /** The engine's package; EspeakZ's own for one of its voices. */
        val engine: String,
        /** The voice's name in that engine. */
        val voice: String,
        /** The voice's language tag, used if the engine no longer has the voice. */
        val locale: String,
        /** Words per minute; without one, EspeakZ's speech rate. */
        val rate: String,
        /** 0 to 100, 50 being the voice's own pitch. */
        val pitch: String,
        /** 0 to 200 percent. */
        val volume: String,
    ) {
        val all: List<String>
            get() = listOf(engine, voice, locale, rate, pitch, volume)
    }

    /** The keys of [code]'s voice, such as "espeakz_dual_ar_voice" for Arabic. */
    fun keysFor(code: String): Keys {
        val p = "espeakz_dual_${code}_"
        return Keys(p + "engine", p + "voice", p + "locale", p + "rate", p + "pitch", p + "volume")
    }

    const val DEFAULT_PITCH = 50
    const val DEFAULT_VOLUME = 100

    /** Which voice reads digits, and punctuation and symbols that stand apart from words. */
    const val PREF_NUMBERS = "espeakz_dual_numbers"

    /** Which voice reads emojis. */
    const val PREF_EMOJIS = "espeakz_dual_emojis"

    /** The Latin language's voice, for [PREF_NUMBERS] and [PREF_EMOJIS]. */
    const val LATIN = "first"

    /** The other alphabet's voice, for [PREF_NUMBERS] and [PREF_EMOJIS]. */
    const val NON_LATIN = "second"

    /**
     * Auto TTS hands its text to other engines, which may include EspeakZ.
     * As a dual languages engine it could send EspeakZ's own request back to
     * EspeakZ, which waits for the first one to finish, so it is not offered.
     */
    val EXCLUDED_ENGINES = setOf(BuildConfig.APPLICATION_ID, "com.vnspeak.autotts")

    /** A language's voice. Rate, pitch and volume are null where the user set none. */
    class Voice(
        val engine: String,
        /** The voice's name; empty for EspeakZ's best voice for the language. */
        val voice: String,
        val locale: Locale?,
        /** The language's code, such as "en". */
        val language: String,
        val rate: Int?,
        val pitch: Int?,
        val volume: Int?,
    ) {
        val isEspeakZ: Boolean
            get() = engine == BuildConfig.APPLICATION_ID
    }

    class Settings(
        /** The voice for Latin text, or null to read it as the app asks. */
        val latin: Voice?,
        /** The voice for other alphabets, or null to read them as the app asks. */
        val nonLatin: Voice?,
        val numbersNonLatin: Boolean,
        val emojisNonLatin: Boolean,
    )

    /** [code]'s saved voice; without one, EspeakZ's best voice for it. */
    fun readVoice(prefs: SharedPreferences, code: String): Voice {
        val keys = keysFor(code)
        return Voice(
            engine = prefs.getString(keys.engine, null)?.takeIf { it.isNotEmpty() } ?: BuildConfig.APPLICATION_ID,
            voice = prefs.getString(keys.voice, null).orEmpty(),
            locale = prefs.getString(keys.locale, null)?.takeIf { it.isNotEmpty() }?.let { Locale.forLanguageTag(it) },
            language = code,
            rate = prefs.getString(keys.rate, null)?.toIntOrNull(),
            pitch = prefs.getString(keys.pitch, null)?.toIntOrNull()?.coerceIn(0, 100),
            volume = prefs.getString(keys.volume, null)?.toIntOrNull()?.coerceIn(0, 200),
        )
    }

    /** The saved settings, or null when dual languages is off or no language is in use. */
    fun read(prefs: SharedPreferences): Settings? {
        if (!prefs.getBoolean(PREF_ENABLED, false)) return null
        migrate(prefs)
        val latin = prefs.getString(PREF_LATIN, null)?.takeIf { it.isNotEmpty() }
        val nonLatin = prefs.getString(PREF_NON_LATIN, null)?.takeIf { it.isNotEmpty() }
        if (latin == null && nonLatin == null) return null
        return Settings(
            latin?.let { readVoice(prefs, it) },
            nonLatin?.let { readVoice(prefs, it) },
            numbersNonLatin = prefs.getString(PREF_NUMBERS, LATIN) == NON_LATIN,
            emojisNonLatin = prefs.getString(PREF_EMOJIS, LATIN) == NON_LATIN,
        )
    }

    /**
     * Moves a first and second voice saved by an earlier version, under
     * fixed keys, to the keys of their languages, and puts those two
     * languages in use. Does nothing once a language is in use.
     */
    fun migrate(prefs: SharedPreferences) {
        if (prefs.contains(PREF_LATIN) || prefs.contains(PREF_NON_LATIN)) return
        val old = listOf(
            listOf("espeakz_dual_main_language", "espeakz_dual_main_engine", "espeakz_dual_main", "espeakz_dual_main_locale",
                "espeakz_dual_main_rate", "espeakz_dual_main_pitch", "espeakz_dual_main_volume"),
            listOf("espeakz_dual_language", "espeakz_dual_engine", "espeakz_dual_voice", "espeakz_dual_voice_locale",
                "espeakz_dual_rate", "espeakz_dual_pitch", "espeakz_dual_volume"),
        )
        val editor = prefs.edit()
        var moved = false
        for (keys in old) {
            val voice = prefs.getString(keys[2], null)?.takeIf { it.isNotEmpty() }
            val locale = prefs.getString(keys[3], null)?.takeIf { it.isNotEmpty() }?.let { Locale.forLanguageTag(it) }
            // An EspeakZ voice saved without a language is named after it, as "en-us".
            val code = prefs.getString(keys[0], null)?.takeIf { it.isNotEmpty() }
                ?: locale?.let { normalize(it).language }
                ?: voice?.substringBefore('-')?.lowercase(Locale.ROOT)
            if (code != null && voice != null) {
                val target = keysFor(code).all
                for (i in 1..6) prefs.getString(keys[i], null)?.let { editor.putString(target[i - 1], it) }
                editor.putString(if (isLatin(Locale(code))) PREF_LATIN else PREF_NON_LATIN, code)
                moved = true
            }
            keys.forEach { editor.remove(it) }
        }
        if (moved) editor.apply()
    }

    /**
     * [locale] with two-letter language and country codes. Some engines,
     * such as Acapela, name their voices' languages with three-letter codes,
     * "ara_SAU" for Arabic (Saudi Arabia), which would otherwise not match
     * the same language from other engines.
     */
    fun normalize(locale: Locale): Locale {
        val language = ISO3_LANGUAGES[locale.language.lowercase(Locale.ROOT)] ?: locale.language
        val country = ISO3_COUNTRIES[locale.country.uppercase(Locale.ROOT)] ?: locale.country
        return if (language == locale.language && country == locale.country) locale else Locale(language, country, locale.variant)
    }

    private val ISO3_LANGUAGES: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { code -> iso3 { Locale(code).isO3Language }?.let { it to code } }.toMap()
    }

    private val ISO3_COUNTRIES: Map<String, String> by lazy {
        Locale.getISOCountries().mapNotNull { code -> iso3 { Locale("", code).isO3Country }?.let { it to code } }.toMap()
    }

    private fun iso3(code: () -> String): String? = try {
        code().takeIf { it.length == 3 }
    } catch (e: java.util.MissingResourceException) {
        null
    }

    /** Whether [locale]'s language is written in the Latin alphabet. */
    fun isLatin(locale: Locale): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val script = ULocale.addLikelySubtags(ULocale.forLocale(locale)).script
            if (script.isNotEmpty()) return script == "Latn"
        }
        // The language's own name for itself is written in its alphabet.
        val name = locale.getDisplayLanguage(locale)
        var i = 0
        while (i < name.length) {
            val codePoint = name.codePointAt(i)
            if (Character.isLetter(codePoint)) return isLatinLetter(codePoint)
            i += Character.charCount(codePoint)
        }
        return true
    }

    private fun isLatinLetter(codePoint: Int): Boolean = Character.UnicodeBlock.of(codePoint) in LATIN_BLOCKS

    private val LATIN_BLOCKS = setOf(
        Character.UnicodeBlock.BASIC_LATIN,
        Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
        Character.UnicodeBlock.LATIN_EXTENDED_A,
        Character.UnicodeBlock.LATIN_EXTENDED_B,
        Character.UnicodeBlock.LATIN_EXTENDED_C,
        Character.UnicodeBlock.LATIN_EXTENDED_D,
        Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL,
        Character.UnicodeBlock.IPA_EXTENSIONS,
        Character.UnicodeBlock.PHONETIC_EXTENSIONS,
        Character.UnicodeBlock.PHONETIC_EXTENSIONS_SUPPLEMENT,
        Character.UnicodeBlock.ALPHABETIC_PRESENTATION_FORMS,
        Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS,
    )

    /** A stretch of the text from [start] to [end], UTF-16 indices; [second] is for the second voice. */
    data class Run(val start: Int, val end: Int, val second: Boolean)

    /**
     * Splits [text] into runs for the main and the second voice. Letters go
     * by their alphabet. Digits go to the second voice when [numbersSecond],
     * and so do punctuation and symbols that stand apart from words, such as
     * the % in "50 %" or a lone "#"; punctuation touching a word, such as a
     * sentence's comma or full stop, stays with that word, so sentences are
     * not broken up. Emojis go to the second voice when [emojisSecond].
     * Spaces and combining marks stay with the run they follow; any before
     * the first run go with it.
     */
    fun split(text: String, mainIsLatin: Boolean, numbersSecond: Boolean = false, emojisSecond: Boolean = false): List<Run> {
        val runs = ArrayList<Run>()
        var runStart = 0
        var runSecond: Boolean? = null
        var previous = -1
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val next = i + Character.charCount(codePoint)
            val following = if (next < text.length) text.codePointAt(next) else -1
            val second: Boolean? = when {
                Character.isLetter(codePoint) -> isLatinLetter(codePoint) != mainIsLatin
                isEmoji(codePoint) -> emojisSecond
                Character.isDigit(codePoint) -> numbersSecond
                isPunctuationOrSymbol(codePoint) && !isLetter(previous) && !isLetter(following) -> numbersSecond
                else -> null
            }
            if (second != null) {
                if (runSecond == null) {
                    runSecond = second
                } else if (second != runSecond) {
                    runs.add(Run(runStart, i, runSecond))
                    runStart = i
                    runSecond = second
                }
            }
            previous = codePoint
            i = next
        }
        if (text.isNotEmpty()) runs.add(Run(runStart, text.length, runSecond ?: false))
        return runs
    }

    private fun isLetter(codePoint: Int): Boolean = codePoint >= 0 && Character.isLetter(codePoint)

    private fun isPunctuationOrSymbol(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL,
        Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true
        else -> false
    }

    /**
     * Emoji and pictographs. The joiners, variation selectors and skin tone
     * modifiers inside an emoji sequence are not listed: as marks and
     * modifiers they stay with the emoji before them.
     */
    private fun isEmoji(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF || codePoint in 0x2B00..0x2BFF
}
