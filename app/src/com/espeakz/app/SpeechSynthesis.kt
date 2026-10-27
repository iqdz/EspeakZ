/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2012-2015 Reece H. Dunn
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

/*
 * The Kotlin API to eSpeak, using the JNI bindings in eSpeakService.c.
 */

package com.espeakz.app

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.Locale
import java.util.MissingResourceException

/**
 * One handle on the native eSpeak engine.
 *
 * eSpeak keeps a single global state for the whole process, so every instance
 * shares it: the engine is initialised once per process, never again, and all
 * calls into it are serialised on one lock. The one exception is [stop], which
 * only raises a flag that the running synthesis checks, so that a stop never
 * waits for the utterance it is meant to end. Earlier, each instance started
 * the engine again, which could reset it under a synthesis running on another
 * thread.
 *
 * The constructor makes sure the voice data is installed first and blocks
 * until it is, so it must not run on the main thread.
 */
class SpeechSynthesis(context: Context, private val callback: SynthReadyCallback?) {

    /** Receives audio and word boundaries as eSpeak produces them. */
    interface SynthReadyCallback {
        fun onSynthDataReady(audioData: ByteArray)

        fun onSynthDataComplete()

        /**
         * Reports the start of a spoken word.
         *
         * @param textPosition 1-based index of the word in the synthesized
         *                     text, counted in Unicode code points (not UTF-16
         *                     units).
         * @param textLength Word length in code points. eSpeak packs this into
         *                   a single byte, so words longer than 255 report a
         *                   truncated length.
         * @param markerInFrames Absolute frame offset of the word within the
         *                       audio generated for this request.
         */
        fun onSynthWordBoundary(textPosition: Int, textLength: Int, markerInFrames: Int)
    }

    enum class UnitType {
        Percentage,
        WordsPerMinute,
        /** One of the PUNCT_* constants. */
        Punctuation,
    }

    inner class Parameter internal constructor(
        private val id: Int,
        val minValue: Int,
        val maxValue: Int,
        val unitType: UnitType,
    ) {
        val defaultValue: Int
            get() = synchronized(engineLock) { if (isReady) nativeGetParameter(id, 0) else 0 }

        val value: Int
            get() = synchronized(engineLock) { if (isReady) nativeGetParameter(id, 1) else 0 }

        fun setValue(value: Int, scale: Int) {
            setValue((value * scale) / 100)
        }

        fun setValue(value: Int) {
            synchronized(engineLock) { if (isReady) nativeSetParameter(id, value) }
        }
    }

    private val storage: Context = EspeakData.storage(context)

    /** Speech rate. */
    @JvmField val Rate = Parameter(1, 80, 450, UnitType.WordsPerMinute)

    /** Audio volume. */
    @JvmField val Volume = Parameter(2, 0, 200, UnitType.Percentage)

    /** Base pitch. */
    @JvmField val Pitch = Parameter(3, 0, 100, UnitType.Percentage)

    /** Pitch range (monotone = 0). */
    @JvmField val PitchRange = Parameter(4, 0, 100, UnitType.Percentage)

    /** Which punctuation characters to announce. */
    @JvmField val Punctuation = Parameter(5, 0, 2, UnitType.Punctuation)

    init {
        EspeakData.ensureInstalled(storage)
        initializeEngine(storage)
    }

    val sampleRate: Int
        get() = engineSampleRate

    val channelCount: Int
        get() = CHANNEL_COUNT_MONO

    val audioFormat: Int
        get() = FORMAT_PCM_S16

    /**
     * The voices whose dictionaries are installed. The engine lists every
     * voice it has a language file for, but a voice without its dictionary
     * cannot speak, so those are left out.
     */
    fun getAvailableVoices(): List<Voice> {
        val installed = EspeakData.installedDictionaries(storage)
        return allVoices().filter { voice ->
            val dictionary = EspeakData.dictionaryForVoice(storage, voice.identifier)
            dictionary == null || dictionary in installed
        }
    }

    /**
     * The voices whose dictionary is not installed: languages EspeakZ ships
     * that can be added in Voice languages.
     */
    fun getUnavailableVoices(): List<Voice> {
        val installed = EspeakData.installedDictionaries(storage)
        return allVoices().filter { voice ->
            val dictionary = EspeakData.dictionaryForVoice(storage, voice.identifier)
            dictionary != null && dictionary !in installed
        }
    }

    /**
     * Selects the voice and variant for the next synthesis. A variant eSpeak
     * cannot load falls back to the plain voice, so that a bad variant name is
     * never silence.
     */
    fun setVoice(voice: Voice, variant: VoiceVariant) {
        synchronized(engineLock) {
            if (!isReady) return
            // Selecting a voice makes eSpeak read its voice and dictionary
            // files again, which a screen reader's every request would wait
            // for, so the voice already selected is not selected again,
            // unless the voice data changed since.
            val key = voice.identifier + "+" + variant + "@" + EspeakData.generation
            if (key == selectedVoice) return
            selectedVoice = key
            // espeak_SetVoiceByProperties does not support specifying the voice
            // variant (e.g. klatt), but espeak_SetVoiceByName does.
            val selected = if (variant.variant == null) {
                nativeSetVoiceByProperties(voice.name, variant.gender, variant.age)
            } else {
                nativeSetVoiceByName(voice.identifier + "+" + variant.variant)
            }
            if (!selected) {
                Log.w(TAG, "Could not select variant $variant of ${voice.identifier}; using the plain voice")
                nativeSetVoiceByName(voice.identifier)
            }
        }
    }

    fun setPunctuationCharacters(characters: String?) {
        synchronized(engineLock) { if (isReady) nativeSetPunctuationCharacters(characters) }
    }

    /** Synthesizes [text], blocking until eSpeak has delivered all of it. */
    fun synthesize(text: String, isSsml: Boolean) {
        synchronized(engineLock) {
            if (!isReady) {
                callback?.onSynthDataComplete()
                return
            }
            nativeSynthesize(text, isSsml)
            // SSML can switch voices itself.
            if (isSsml) selectedVoice = null
        }
    }

    /** Ends the synthesis in progress. Deliberately takes no lock. */
    fun stop() {
        if (isReady) nativeStop()
    }

    @Suppress("unused") // called from eSpeakService.c
    private fun nativeSynthCallback(audioData: ByteArray?) {
        val callback = callback ?: return
        if (audioData == null) {
            callback.onSynthDataComplete()
        } else {
            callback.onSynthDataReady(audioData)
        }
    }

    @Suppress("unused") // called from eSpeakService.c
    private fun nativeSynthWordCallback(textPosition: Int, textLength: Int, markerInFrames: Int) {
        callback?.onSynthWordBoundary(textPosition, textLength, markerInFrames)
    }

    private fun initializeEngine(storage: Context) {
        val path = EspeakData.dataPath(storage).parentFile!!.path
        synchronized(engineLock) {
            if (initializedPath == path) return
            if (!EspeakData.hasBaseResources(storage)) {
                Log.e(TAG, "Missing base resources")
                return
            }
            val rate = timed("start the native engine") { nativeCreate(path) }
            if (rate == 0) {
                Log.e(TAG, "Failed to initialize speech synthesis library")
                return
            }
            engineSampleRate = rate
            initializedPath = path
            selectedVoice = null
            cachedVoices = null
            Log.i(TAG, "Initialized synthesis library with sample rate = $rate")
        }
    }

    /**
     * Every voice the engine has, once per process. It comes from the
     * build-time index of the language files, which gives each voice's name
     * and identifier as the engine lists them; every language file leaves
     * gender and age unset, which the engine reports as male, age 0. Asking
     * the engine instead reads all 150 language files, which took a watch
     * half a second at every start, before its first words.
     */
    private fun allVoices(): List<Voice> {
        // Read without the lock once cached, so a caller never waits for a
        // synthesis in progress just to get the voice list.
        cachedVoices?.let { return it }
        synchronized(engineLock) {
            cachedVoices?.let { return it }
            if (!isReady) return emptyList()
            val voices = timed("list the engine's voices") {
                EspeakData.allLanguageFiles(storage)
                    .sortedWith(compareBy({ it.language }, { it.identifier }))
                    .mapNotNull { parseVoice(it.language, it.identifier, GENDER_MALE, AGE_ANY) }
            }
            cachedVoices = voices
            return voices
        }
    }

    /** The voices as the engine itself lists them, for checking [allVoices] against. */
    internal fun nativeVoices(): List<Voice> = synchronized(engineLock) {
        if (!isReady) return emptyList()
        val results = nativeGetAvailableVoices()
        val voices = ArrayList<Voice>(results.size / 4)
        var i = 0
        while (i + 3 < results.size) {
            parseVoice(results[i], results[i + 1], results[i + 2].toInt(), results[i + 3].toInt())
                ?.let { voices.add(it) }
            i += 4
        }
        voices
    }

    /** Every voice, installed or not, for checking [allVoices] against [nativeVoices]. */
    internal fun indexedVoices(): List<Voice> = allVoices()

    private fun parseVoice(name: String, identifier: String, gender: Int, age: Int): Voice? {
        try {
            if (identifier == "asia/fa-en-us") {
                throw IllegalArgumentException("Voice '$identifier' is a duplicate voice.")
            }
            val locale = localeFromLanguageName(name)
                ?: throw IllegalArgumentException("Locale not supported.")
            if (locale.isO3Language.isEmpty()) {
                throw IllegalArgumentException("Language '${locale.language}' not supported.")
            }
            if (locale.isO3Country.isEmpty() && locale.country.isNotEmpty()) {
                throw IllegalArgumentException("Country '${locale.country}' not supported.")
            }
            return Voice(name, identifier, gender, age, locale)
        } catch (e: MissingResourceException) {
            // Thrown when the 3-letter language (e.g. nci) or country (e.g.
            // 021) code is missing for a locale.
            Log.d(TAG, "getAvailableVoices: skipping $name => ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "getAvailableVoices: skipping $name => ${e.message}")
        }
        return null
    }

    private val isReady: Boolean
        get() = initializedPath != null

    private external fun nativeCreate(path: String): Int

    private external fun nativeGetAvailableVoices(): Array<String>

    private external fun nativeSetVoiceByName(name: String): Boolean

    private external fun nativeSetVoiceByProperties(language: String?, gender: Int, age: Int): Boolean

    private external fun nativeSetParameter(parameter: Int, value: Int): Boolean

    private external fun nativeGetParameter(parameter: Int, current: Int): Int

    private external fun nativeSetPunctuationCharacters(characters: String?): Boolean

    private external fun nativeSynthesize(text: String, isSsml: Boolean): Boolean

    private external fun nativeStop(): Boolean

    companion object {
        private const val TAG = "SpeechSynthesis"

        const val GENDER_UNSPECIFIED = 0
        const val GENDER_MALE = 1
        const val GENDER_FEMALE = 2

        const val AGE_ANY = 0
        const val AGE_YOUNG = 12
        const val AGE_OLD = 60

        const val CHANNEL_COUNT_MONO = 1
        const val FORMAT_PCM_S16 = 2

        /** Don't announce any punctuation characters. */
        const val PUNCT_NONE = 0

        /** Announce every punctuation character. */
        const val PUNCT_ALL = 1

        /** Announce some of the punctuation characters. */
        const val PUNCT_SOME = 2

        /** Guards every native call except stop, and the state below. */
        private val engineLock = Any()
        private var initializedPath: String? = null
        private var engineSampleRate = 0
        /** The voice and variant selected last, as setVoice() names them. */
        private var selectedVoice: String? = null
        @Volatile private var cachedVoices: List<Voice>? = null

        init {
            System.loadLibrary("ttsespeak")
            nativeClassInit()
        }

        @JvmStatic
        private external fun nativeClassInit(): Boolean

        @JvmStatic
        private external fun nativeGetVersion(): String

        @JvmStatic
        fun getVersion(): String = nativeGetVersion()

        /** The Java locale for an eSpeak language name such as "en-gb-x-rp". */
        @JvmStatic
        fun localeFromLanguageName(name: String): Locale? {
            LOCALE_FIXES[name]?.let { return it }
            val parts = name.split("-")
            return when (parts.size) {
                1 -> Locale(parts[0]) // language
                2 -> Locale(parts[0], parts[1]) // language-country
                3 -> Locale(parts[0], parts[1], parts[2]) // language-country-variant
                4 -> Locale(parts[0], parts[1], parts[3]) // language-country-x-privateuse
                else -> null
            }
        }

        /**
         * The sample sentence for [locale], from the app's resources in that
         * language. The app's own configuration is left untouched.
         */
        @JvmStatic
        fun getSampleText(context: Context, locale: Locale): String {
            val language = getIanaLanguageCode(locale.language)
            val country = getIanaCountryCode(locale.country)
            val sampleLocale = Locale(language, country, locale.variant)
            val config = Configuration(context.resources.configuration)
            config.setLocale(sampleLocale)
            val resources = context.createConfigurationContext(config).resources
            return resources.getString(R.string.sample_text, sampleLocale.getDisplayName(sampleLocale))
        }

        @JvmStatic
        fun getIanaLanguageCode(code: String): String = JAVA_TO_IANA_LANGUAGE[code] ?: code

        @JvmStatic
        fun getIanaCountryCode(code: String): String = JAVA_TO_IANA_COUNTRY[code] ?: code

        private val JAVA_TO_IANA_LANGUAGE = mapOf(
            "afr" to "af", "amh" to "am", "ara" to "ar", "arg" to "an", "asm" to "as",
            "aze" to "az", "bul" to "bg", "ben" to "bn", "bos" to "bs", "cat" to "ca",
            "ces" to "cs", "cym" to "cy", "dan" to "da", "deu" to "de", "ell" to "el",
            "eng" to "en", "epo" to "eo", "spa" to "es", "est" to "et", "eus" to "eu",
            "fas" to "fa", "fin" to "fi", "fra" to "fr", "gle" to "ga", "gla" to "gd",
            "grn" to "gn", "guj" to "gu", "hin" to "hi", "hrv" to "hr", "hun" to "hu",
            "hye" to "hy", "ina" to "ia",
            "ind" to "in", // NOTE: The deprecated 'in' code is used by Java/Android.
            "isl" to "is", "ita" to "it", "jpn" to "ja", "kat" to "ka", "kal" to "kl",
            "kan" to "kn", "kir" to "ky", "kor" to "ko", "kur" to "ku", "lat" to "la",
            "lit" to "lt", "lav" to "lv", "mkd" to "mk", "mal" to "ml", "mar" to "mr",
            "mlt" to "mt", "mri" to "mi", "msa" to "ms", "mya" to "my", "nep" to "ne",
            "nld" to "nl", "nob" to "nb", "nor" to "no", "ori" to "or", "orm" to "om",
            "pan" to "pa", "pol" to "pl", "por" to "pt", "ron" to "ro", "rus" to "ru",
            "sin" to "si", "slk" to "sk", "slv" to "sl", "snd" to "sd", "sqi" to "sq",
            "srp" to "sr", "swe" to "sv", "swa" to "sw", "tam" to "ta", "tel" to "te",
            "tat" to "tt", "tsn" to "tn", "tur" to "tr", "urd" to "ur", "vie" to "vi",
            "zho" to "zh",
        )

        private val JAVA_TO_IANA_COUNTRY = mapOf(
            "ARM" to "AM", "BEL" to "BE", "BRA" to "BR", "CHE" to "CH", "FRA" to "FR",
            "GBR" to "GB", "HKG" to "HK", "JAM" to "JM", "MEX" to "MX", "PRT" to "PT",
            "USA" to "US", "VNM" to "VN",
        )

        /** BCP 47 locales that Android does not handle correctly. */
        private val LOCALE_FIXES = mapOf(
            "cmn" to Locale("zh"),
            "en-029" to Locale("en", "JM"),
            "es-419" to Locale("es", "MX"),
            "hy-arevmda" to Locale("hy", "AM", "arevmda"), // hy-arevmda crashes on Android 5.0
            "yue" to Locale("zh", "HK"),
        )
    }
}
