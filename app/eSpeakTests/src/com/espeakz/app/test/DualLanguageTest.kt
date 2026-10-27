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

package com.espeakz.app.test

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.os.SystemClock
import android.preference.PreferenceManager
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.DualLanguage
import com.espeakz.app.EspeakZLanguage
import com.espeakz.app.NumberGrouping
import com.espeakz.app.PcmConverter
import com.espeakz.app.SpeechSynthesis
import com.espeakz.app.TtsService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Dual languages: the alphabet split, the audio conversion, and mixed
 * English and Arabic text read through the real TextToSpeech client, with
 * the second voice from EspeakZ and from Google's engine when it has an
 * Arabic voice on the device. Synthesizes to files, so the device's default
 * engine is not used or changed.
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class DualLanguageTest {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(TestLanguages.storage) }

    /** The user's own values of the settings these tests change, put back afterwards. */
    private lateinit var saved: Map<String, Any?>

    @Before
    fun setUp() {
        TestLanguages.installAll()
        saved = prefs.all.filterKeys(::isOurs)
    }

    @After
    fun tearDown() {
        val editor = prefs.edit()
        prefs.all.keys.filter(::isOurs).forEach { editor.remove(it) }
        for ((key, value) in saved) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                else -> editor.putString(key, value.toString())
            }
        }
        editor.commit()
    }

    /** The settings these tests change: every dual languages setting and EspeakZ's language. */
    private fun isOurs(key: String) =
        key.startsWith("espeakz_dual") || key == EspeakZLanguage.PREF_VOICE || key == EspeakZLanguage.PREF_FORCE

    @Test
    fun movesOldSettingsToTheirLanguages() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("espeakz_dual") }.forEach { editor.remove(it) }
        editor.putString("espeakz_dual_main", "en-us")
            .putString("espeakz_dual_language", "ar")
            .putString("espeakz_dual_engine", "org.nobody.multitts")
            .putString("espeakz_dual_voice", "acapela_ara-SAU-Leila")
            .putString("espeakz_dual_voice_locale", "ar-SA")
            .putString("espeakz_dual_volume", "60")
            .commit()
        DualLanguage.migrate(prefs)
        assertThat(prefs.getString(DualLanguage.PREF_LATIN, null), `is`("en"))
        assertThat(prefs.getString(DualLanguage.PREF_NON_LATIN, null), `is`("ar"))
        assertThat(prefs.getString(DualLanguage.keysFor("en").voice, null), `is`("en-us"))
        assertThat(prefs.getString(DualLanguage.keysFor("ar").engine, null), `is`("org.nobody.multitts"))
        assertThat(prefs.getString(DualLanguage.keysFor("ar").volume, null), `is`("60"))
        assertThat(prefs.contains("espeakz_dual_voice"), `is`(false))
    }

    @Test
    fun splitsByAlphabet() {
        val text = "Hello, مرحبا بكم 2 world."
        fun parts(runs: List<DualLanguage.Run>) = runs.map { text.substring(it.start, it.end) to it.second }
        // Numbers go to the first voice unless chosen otherwise; a sentence's
        // punctuation stays with its word.
        assertThat(parts(DualLanguage.split(text, mainIsLatin = true)), `is`(listOf(
            "Hello, " to false,
            "مرحبا بكم " to true,
            "2 world." to false,
        )))
        assertThat(parts(DualLanguage.split(text, mainIsLatin = true, numbersSecond = true)), `is`(listOf(
            "Hello, " to false,
            "مرحبا بكم 2 " to true,
            "world." to false,
        )))
        // The other way round with Arabic as the main language.
        assertThat(DualLanguage.split(text, mainIsLatin = false).map { it.second }, `is`(listOf(true, false, true)))
        assertThat(DualLanguage.split("", mainIsLatin = true), `is`(emptyList()))
    }

    @Test
    fun splitsNumbersSymbolsAndEmojis() {
        fun parts(text: String, numbers: Boolean, emojis: Boolean) =
            DualLanguage.split(text, mainIsLatin = true, numbersSecond = numbers, emojisSecond = emojis)
                .map { text.substring(it.start, it.end) to it.second }
        // A symbol standing apart goes with the numbers; one touching a word does not.
        assertThat(parts("Up 50 % today", numbers = true, emojis = false), `is`(listOf(
            "Up " to false, "50 % " to true, "today" to false,
        )))
        assertThat(parts("Call me, please.", numbers = true, emojis = false), `is`(listOf("Call me, please." to false)))
        // An emoji with its skin tone and joiners stays one run.
        assertThat(parts("Hi 👋🏽 there", numbers = false, emojis = true), `is`(listOf(
            "Hi " to false, "👋🏽 " to true, "there" to false,
        )))
        assertThat(parts("Hi 👋🏽 there", numbers = true, emojis = false), `is`(listOf("Hi 👋🏽 there" to false)))
    }

    @Test
    fun groupsLongNumbers() {
        assertThat(NumberGrouping.group("Call 0551234567 now", 0), `is`(nullValue()))
        assertThat(NumberGrouping.group("Code 123456 only", 2), `is`(nullValue()))
        assertThat(NumberGrouping.group("Call 0551234567 now", 1)!!.text, `is`("Call 0 5 5 1 2 3 4 5 6 7 now"))
        assertThat(NumberGrouping.group("Call 0551234567 now", 2)!!.text, `is`("Call 05 51 23 45 67 now"))
        val grouped = NumberGrouping.group("1234567 x", 3)!!
        assertThat(grouped.text, `is`("123 456 7 x"))
        // Offsets lead back to the original text: the inserted space before
        // "7" and the "7" itself point at 6, " x" at 7 and 8.
        assertThat(grouped.toOriginalOffset(7), `is`(6))
        assertThat(grouped.toOriginalOffset(8), `is`(6))
        assertThat(grouped.toOriginalOffset(9), `is`(7))
        assertThat(grouped.toOriginalOffset(10), `is`(8))
        assertThat(grouped.toOriginalOffset(grouped.text.length), `is`(9))
    }

    @Test
    fun choosesTheDeviceLanguage() {
        val voices = SpeechSynthesis(TestLanguages.storage, null).getAvailableVoices()
        assertThat(EspeakZLanguage.deviceVoice(voices, Locale("en"))!!.name, `is`("en-us"))
        assertThat(EspeakZLanguage.deviceVoice(voices, Locale("en", "DZ"))!!.name, `is`("en-us"))
        assertThat(EspeakZLanguage.deviceVoice(voices, Locale("en", "GB"))!!.name, `is`("en-gb"))
        assertThat(EspeakZLanguage.deviceVoice(voices, Locale("ar", "DZ"))!!.locale.language, `is`("ar"))
        // A language EspeakZ does not have falls back to American English.
        assertThat(EspeakZLanguage.deviceVoice(voices, Locale("xx"))!!.name, `is`("en-us"))
        // The user's choice wins over the device's language.
        prefs.edit().putString(EspeakZLanguage.PREF_VOICE, "fr-fr").commit()
        assertThat(EspeakZLanguage.voice(prefs, voices, Locale.US)!!.name, `is`("fr-fr"))
    }

    @Test
    fun knowsLatinLanguages() {
        for (tag in listOf("en", "fr", "de", "tr", "vi")) {
            assertThat(tag, DualLanguage.isLatin(Locale.forLanguageTag(tag)), `is`(true))
        }
        for (tag in listOf("ar", "fa", "ru", "hi", "zh", "el")) {
            assertThat(tag, DualLanguage.isLatin(Locale.forLanguageTag(tag)), `is`(false))
        }
    }

    @Test
    fun convertsRateAndTrimsSilence() {
        // Half a second of silence, then one second of a 440 Hz tone at 24 kHz.
        val inRate = 24000
        val samples = ShortArray(inRate / 2) + ShortArray(inRate) { (sin(2 * PI * 440 * it / inRate) * 10000).toInt().toShort() }
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            bytes[2 * i] = s.toByte()
            bytes[2 * i + 1] = (s.toInt() shr 8).toByte()
        }
        val out = ArrayList<Short>()
        val converter = PcmConverter(inRate, AudioFormat.ENCODING_PCM_16BIT, 1, 22050, sink = { buffer, count ->
            for (i in 0 until count) out.add(buffer[i])
            true
        })
        // Odd chunk sizes split frames across writes.
        var offset = 0
        while (offset < bytes.size) {
            val n = minOf(1001, bytes.size - offset)
            assertThat(converter.write(bytes, offset, n), `is`(true))
            offset += n
        }
        assertThat(converter.finish(), `is`(true))

        // One second of tone plus at most 40 ms of silence kept on each side.
        assertThat(out.size, greaterThan(22050 - 50))
        assertThat(out.size, lessThan(22050 + 2 * 882 + 50))
        val firstLoud = out.indexOfFirst { abs(it.toInt()) > 100 }
        assertThat(firstLoud, lessThan(882 + 30))
        val peak = out.maxOf { abs(it.toInt()) }
        assertThat(peak, greaterThan(9000))
        assertThat(peak, lessThan(11000))
    }

    @Test
    fun readsArabicWithEspeakZVoice() {
        val arabic = espeakVoice("ar")
        enable(main = espeakVoice("en").name, engine = ENGINE, voice = arabic.name, locale = arabic.locale)
        val mixed = synthesize(MIXED)
        enable(main = espeakVoice("en").name, engine = ENGINE, voice = arabic.name, locale = arabic.locale, on = false)
        val english = synthesize(ENGLISH)
        Log.i(TAG, "EspeakZ second voice: mixed ${mixed.frames} frames, English only ${english.frames}")
        assertThat(mixed.frames, greaterThan(english.frames + SAMPLE_RATE_GUESS / 4))
    }

    @Test
    fun readsArabicWithAnotherEngine() {
        val found = OTHER_ENGINES.firstNotNullOfOrNull { engine -> engineVoice(engine, "ar")?.let { engine to it } }
        assumeTrue("no other engine has an offline Arabic voice here", found != null)
        val (engine, voice) = found!!
        enable(main = espeakVoice("en").name, engine = engine, voice = voice.name, locale = voice.locale)
        val english = synthesize(ENGLISH)
        val mixed = synthesize(MIXED)
        val again = synthesize(MIXED)
        val many = synthesize(MANY)
        Log.i(TAG, "$engine voice ${voice.name}: mixed ${mixed.frames} frames in ${mixed.ms} ms, then ${again.ms} ms; English only ${english.frames} frames in ${english.ms} ms; four Arabic parts ${many.frames} frames in ${many.ms} ms")
        assertThat(mixed.frames, greaterThan(english.frames + SAMPLE_RATE_GUESS / 4))
    }

    @Test
    fun forcedLanguageWinsOverTheRequest() {
        prefs.edit().putBoolean(EspeakZLanguage.PREF_FORCE, true).putString(EspeakZLanguage.PREF_VOICE, "fr-fr").commit()
        val service = Service(TestLanguages.storage)
        service.onCreate()
        try {
            val callback = Callback()
            assertThat(service.onLoadLanguage("eng", "USA", ""), `is`(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
            service.synthesize(SynthesisRequest("hello", Bundle()), callback)
            assertThat(callback.done, `is`(true))
            assertThat(callback.frames, greaterThan(0))
            assertThat(service.voice!!.name, `is`("fr-fr"))
        } finally {
            service.onDestroy()
        }
    }

    private class Service(context: Context) : TtsService() {
        init {
            attachBaseContext(context)
        }

        val voice: com.espeakz.app.Voice?
            get() = matchingVoice

        fun synthesize(request: SynthesisRequest, callback: SynthesisCallback) = onSynthesizeText(request, callback)
    }

    /** Collects what the engine hands the framework for one request. */
    private class Callback : SynthesisCallback {
        var done = false
        var frames = 0

        override fun getMaxBufferSize(): Int = 8192
        override fun start(sampleRateInHz: Int, audioFormat: Int, channelCount: Int): Int = TextToSpeech.SUCCESS
        override fun audioAvailable(buffer: ByteArray, offset: Int, length: Int): Int {
            frames += length / 2
            return TextToSpeech.SUCCESS
        }
        override fun done(): Int {
            done = true
            return TextToSpeech.SUCCESS
        }
        override fun error() {}
        override fun error(errorCode: Int) {}
        override fun hasStarted(): Boolean = true
        override fun hasFinished(): Boolean = done
    }

    @Test
    fun readsFirstLanguageWithAnotherEngine() {
        val found = OTHER_ENGINES.firstNotNullOfOrNull { engine -> engineVoice(engine, "en")?.let { engine to it } }
        assumeTrue("no other engine has an offline English voice here", found != null)
        val (engine, english) = found!!
        val arabic = espeakVoice("ar")
        enable(main = "", engine = ENGINE, voice = arabic.name, locale = arabic.locale)
        val en = DualLanguage.keysFor("en")
        prefs.edit()
            .putString(en.engine, engine)
            .putString(en.voice, english.name)
            .putString(en.locale, english.locale.toLanguageTag())
            .commit()
        val englishOnly = synthesize(ENGLISH)
        val mixed = synthesize(MIXED)
        Log.i(TAG, "$engine first voice ${english.name}: English only ${englishOnly.frames} frames in ${englishOnly.ms} ms, mixed ${mixed.frames} frames in ${mixed.ms} ms")
        assertThat(englishOnly.frames, greaterThan(SAMPLE_RATE_GUESS / 2))
        assertThat(mixed.frames, greaterThan(englishOnly.frames))
    }

    private fun espeakVoice(language: String): com.espeakz.app.Voice {
        val voice = SpeechSynthesis(TestLanguages.storage, null).getAvailableVoices()
            .filter { it.locale.language == Locale(language).language }
            .minByOrNull { it.name.length }
        assertThat("no $language voice", voice, `is`(notNullValue()))
        return voice!!
    }

    /**
     * Dual languages with English in use for Latin text, read by the EspeakZ
     * voice [main] (EspeakZ's best English voice when empty), and Arabic for
     * the other alphabet, read by [voice] of [engine].
     */
    private fun enable(main: String, engine: String, voice: String, locale: Locale, on: Boolean = true) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("espeakz_dual") }.forEach { editor.remove(it) }
        val en = DualLanguage.keysFor("en")
        val ar = DualLanguage.keysFor("ar")
        editor.putBoolean(DualLanguage.PREF_ENABLED, on)
            .putString(DualLanguage.PREF_LATIN, "en")
            .putString(DualLanguage.PREF_NON_LATIN, "ar")
            .putString(ar.engine, engine)
            .putString(ar.voice, voice)
            .putString(ar.locale, locale.toLanguageTag())
        if (main.isNotEmpty()) editor.putString(en.engine, ENGINE).putString(en.voice, main)
        editor.commit()
    }

    private fun connect(engine: String): TextToSpeech? {
        val ready = CountDownLatch(1)
        val status = AtomicReference(TextToSpeech.ERROR)
        val tts = TextToSpeech(TestLanguages.targetContext, { s ->
            status.set(s)
            ready.countDown()
        }, engine)
        if (!ready.await(INIT_TIMEOUT_S, TimeUnit.SECONDS) || status.get() != TextToSpeech.SUCCESS) {
            tts.shutdown()
            return null
        }
        return tts
    }

    /** An installed, offline voice of [engine] for [language], if it has one. */
    private fun engineVoice(engine: String, language: String): android.speech.tts.Voice? {
        val installed = TestLanguages.targetContext.packageManager
            .queryIntentServices(android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .any { it.serviceInfo.packageName == engine }
        if (!installed) return null
        val tts = connect(engine) ?: return null
        try {
            val voices = tts.voices.orEmpty()
            Log.i(TAG, "$engine: ${voices.size} voices")
            return voices.firstOrNull {
                DualLanguage.normalize(it.locale).language == Locale(language).language && !it.isNetworkConnectionRequired &&
                    it.name != "NOT_SET" &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
            }
        } finally {
            tts.shutdown()
        }
    }

    private class Synthesized(val frames: Int, val ms: Long)

    private fun synthesize(text: String): Synthesized {
        val tts = connect(ENGINE)
        assertThat("EspeakZ did not start", tts, `is`(notNullValue()))
        try {
            tts!!.language = Locale.UK
            val finished = CountDownLatch(1)
            val outcome = AtomicReference("TIMEOUT")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}

                override fun onDone(id: String) {
                    outcome.set("DONE")
                    finished.countDown()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String) {
                    outcome.set("ERROR")
                    finished.countDown()
                }

                override fun onError(id: String, errorCode: Int) {
                    outcome.set("ERROR($errorCode)")
                    finished.countDown()
                }
            })
            val out = File(TestLanguages.targetContext.cacheDir, "dual.wav")
            val started = SystemClock.elapsedRealtime()
            assertThat(tts.synthesizeToFile(text, Bundle(), out, "dual"), `is`(TextToSpeech.SUCCESS))
            finished.await(UTTERANCE_TIMEOUT_S, TimeUnit.SECONDS)
            val ms = SystemClock.elapsedRealtime() - started
            assertThat(outcome.get(), `is`("DONE"))
            val frames = RandomAccessFile(out, "r").use { ((it.length() - 44) / 2).toInt() }
            out.delete()
            return Synthesized(frames, ms)
        } finally {
            tts?.shutdown()
        }
    }

    private companion object {
        const val TAG = "DualLanguageTest"
        const val ENGINE = "com.espeakz.app"
        val OTHER_ENGINES = listOf("com.acapelagroup.android.tts", "com.google.android.tts", "org.nobody.multitts")
        const val ENGLISH = "Hello world, this is a test."
        const val MANY = "One مرحبا two شكرا three أهلا four سلام five."
        const val MIXED = "Hello world, مرحبا بكم في الاختبار this is a test."
        const val SAMPLE_RATE_GUESS = 22050
        const val INIT_TIMEOUT_S = 10L
        const val UTTERANCE_TIMEOUT_S = 30L
    }
}
