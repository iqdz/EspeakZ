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

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.EspeakData
import com.espeakz.app.SpeechSynthesis
import com.espeakz.app.VoiceVariant
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.isIn
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Checks the voice data installer: the engine data and the device's
 * languages are in place on first use, languages can be added and removed,
 * and the engine speaks with EspeakZ's default variant, Klatt 6.
 */
@RunWith(AndroidJUnit4::class)
class EspeakDataTest {
    private val storage = TestLanguages.storage

    @After
    fun restoreAllLanguages() {
        TestLanguages.installAll()
    }

    @Test
    fun installsTheEngineData() {
        assertThat(EspeakData.ensureInstalled(storage), `is`(true))
        assertThat(EspeakData.isInstalled(storage), `is`(true))
        assertThat(EspeakData.hasBaseResources(storage), `is`(true))
        assertThat(EspeakData.installedDictionaries(storage), hasItem(EspeakData.ENGLISH))
    }

    @Test
    fun installingAgainChangesNothing() {
        assertThat(EspeakData.ensureInstalled(storage), `is`(true))
        val phondata = File(EspeakData.dataPath(storage), "phondata")
        val modified = phondata.lastModified()
        assertThat(EspeakData.ensureInstalled(storage), `is`(true))
        assertThat(phondata.lastModified(), `is`(modified))
    }

    @Test
    fun noTemporaryFoldersAreLeftBehind() {
        assertThat(EspeakData.ensureInstalled(storage), `is`(true))
        val parent = EspeakData.dataPath(storage).parentFile!!
        assertThat(File(parent, "${EspeakData.DATA_DIR}.new").exists(), `is`(false))
        assertThat(File(parent, "${EspeakData.DATA_DIR}.old").exists(), `is`(false))
    }

    @Test
    fun defaultsAreTheDeviceLanguagesAndEnglish() {
        val defaults = EspeakData.defaultDictionaries(storage)
        assertThat(defaults, hasItem(EspeakData.ENGLISH))
        assertThat(defaults, everyItem(isIn(EspeakData.availableDictionaries(storage) + EspeakData.ENGLISH)))
        // The device's own language is among them whenever eSpeak speaks it.
        val device = Locale.getDefault()
        val speaksDeviceLanguage = EspeakData.availableLanguages(storage).isNotEmpty() &&
            SpeechSynthesis(storage, null).getAvailableVoices().any { it.locale.isO3Language == device.isO3Language }
        if (speaksDeviceLanguage) {
            val voice = SpeechSynthesis(storage, null).getAvailableVoices().first { it.locale.isO3Language == device.isO3Language }
            assertThat(EspeakData.dictionaryForVoice(storage, voice.identifier), isIn(defaults))
        }
    }

    @Test
    fun languageFilesNameTheirDictionaries() {
        assertThat(EspeakData.dictionaryForVoice(storage, "gmw/en-US"), `is`("en"))
        assertThat(EspeakData.dictionaryForVoice(storage, "sit/cmn"), `is`("cmn"))
        assertThat(EspeakData.dictionaryForVoice(storage, "gmq/nb"), `is`("no"))
        assertThat(EspeakData.dictionaryForVoice(storage, "sem/ar"), `is`("ar"))

        val parsed = EspeakData.parseLanguageFile(
            "test/xx",
            listOf("name Test language", "language xx-yy 5", "language xx 2", "// dictionary zz"),
        )
        assertThat(parsed, `is`(notNullValue()))
        assertThat(parsed!!.name, `is`("Test language"))
        assertThat(parsed.language, `is`("xx-yy"))
        assertThat(parsed.dictionary, `is`("xx"))
    }

    @Test
    fun addsAndRemovesLanguages() {
        assertThat(EspeakData.setInstalledDictionaries(storage, setOf("fr")), `is`(true))
        assertThat(EspeakData.installedDictionaries(storage), `is`(setOf("en", "fr")))

        var voices = SpeechSynthesis(storage, null).getAvailableVoices()
        assertThat(voices.any { it.locale.language == "fr" }, `is`(true))
        assertThat(voices.any { it.locale.language == "de" }, `is`(false))

        assertThat(EspeakData.setInstalledDictionaries(storage, setOf("de")), `is`(true))
        assertThat(EspeakData.installedDictionaries(storage), `is`(setOf("en", "de")))

        voices = SpeechSynthesis(storage, null).getAvailableVoices()
        assertThat(voices.any { it.locale.language == "de" }, `is`(true))
        assertThat(voices.any { it.locale.language == "fr" }, `is`(false))
    }

    @Test
    fun anUpdateKeepsTheChosenLanguages() {
        assertThat(EspeakData.setInstalledDictionaries(storage, setOf("fr")), `is`(true))
        // What an app update with new voice data looks like: the installed
        // version no longer matches the one the app ships.
        File(EspeakData.dataPath(storage), "version").writeText("an older version")
        assertThat(EspeakData.isInstalled(storage), `is`(false))

        assertThat(EspeakData.ensureInstalled(storage), `is`(true))
        assertThat(EspeakData.isInstalled(storage), `is`(true))
        assertThat(EspeakData.installedDictionaries(storage), `is`(setOf("en", "fr")))
    }

    @Test
    fun englishIsAlwaysInstalled() {
        assertThat(EspeakData.setInstalledDictionaries(storage, emptySet()), `is`(true))
        assertThat(EspeakData.installedDictionaries(storage), `is`(setOf("en")))
        assertThat(EspeakData.isInstalled(storage), `is`(true))
    }

    @Test
    fun everyLanguageHasAName() {
        val languages = EspeakData.availableLanguages(storage)
        assertThat(languages.size, `is`(greaterThan(100)))
        for (language in languages) {
            assertThat(language.displayName.isNotBlank(), `is`(true))
        }
        // Mandarin and Cantonese both read "Chinese" in Java, so they must be
        // told apart.
        val names = languages.map { it.displayName }
        assertThat(names.size, `is`(names.toSet().size))
    }

    @Test
    fun importOnlyAcceptsDictionaryFiles() {
        assertThat(EspeakData.importDictionary(storage, "notes.txt", "x".byteInputStream()), `is`(false))
        assertThat(EspeakData.importDictionary(storage, "../en_dict", "x".byteInputStream()), `is`(false))
        assertThat(File(EspeakData.dataPath(storage), "notes.txt").exists(), `is`(false))
    }

    @Test
    fun importAddsADictionary() {
        val bytes = File(EspeakData.dataPath(storage), "en_dict").readBytes()
        assertThat(EspeakData.importDictionary(storage, "zz_dict", bytes.inputStream()), `is`(true))
        assertThat(EspeakData.installedDictionaries(storage), hasItem("zz"))

        // Not shipped by the app, so choosing languages without it removes it.
        assertThat(EspeakData.setInstalledDictionaries(storage, setOf("fr")), `is`(true))
        assertThat(EspeakData.installedDictionaries(storage), not(hasItem("zz")))
    }

    /** The bass boost must not make loud speech distort. */
    @Test
    fun indexListsTheEngineVoices() {
        val synth = SpeechSynthesis(storage, null)
        fun describe(v: com.espeakz.app.Voice) = "${v.name} ${v.identifier} ${v.gender} ${v.age} ${v.locale}"
        val indexed = synth.indexedVoices().map(::describe).sorted()
        val native = synth.nativeVoices().map(::describe).sorted()
        assertThat(indexed.size, `is`(greaterThan(100)))
        assertThat(indexed, `is`(native))
    }

    @Test
    fun bassBoostKeepsSpeechClean() {
        val samples = ArrayList<Short>()
        val synth = SpeechSynthesis(storage, object : SpeechSynthesis.SynthReadyCallback {
            override fun onSynthDataReady(audioData: ByteArray) {
                for (i in 0 until audioData.size / 2) {
                    samples.add(((audioData[2 * i + 1].toInt() shl 8) or (audioData[2 * i].toInt() and 0xff)).toShort())
                }
            }

            override fun onSynthDataComplete() {
            }

            override fun onSynthWordBoundary(textPosition: Int, textLength: Int, markerInFrames: Int) {
            }
        })
        val en = synth.getAvailableVoices().first { it.match(Locale("en")) != TextToSpeech.LANG_NOT_SUPPORTED }
        synth.setVoice(en, VoiceVariant.parseVoiceVariant(VoiceVariant.DEFAULT)!!)
        synth.Volume.setValue(200) // the loudest the settings allow
        try {
            synth.synthesize("Loud speech, with a lot of low vowels: boom, gloom, doom, room.", false)
        } finally {
            synth.Volume.setValue(100)
        }
        assertThat(samples.size, `is`(greaterThan(1000)))
        val clipped = samples.count { it == Short.MAX_VALUE || it == Short.MIN_VALUE }
        assertThat("clipped samples: $clipped of ${samples.size}", clipped * 200 < samples.size, `is`(true))
    }

    @Test
    fun klatt6IsTheDefaultAndSpeaks() {
        var frames = 0
        val synth = SpeechSynthesis(storage, object : SpeechSynthesis.SynthReadyCallback {
            override fun onSynthDataReady(audioData: ByteArray) {
                frames += audioData.size / 2
            }

            override fun onSynthDataComplete() {
            }

            override fun onSynthWordBoundary(textPosition: Int, textLength: Int, markerInFrames: Int) {
            }
        })
        val en = synth.getAvailableVoices().first { it.match(Locale("en")) != TextToSpeech.LANG_NOT_SUPPORTED }
        synth.setVoice(en, VoiceVariant.parseVoiceVariant(VoiceVariant.DEFAULT)!!)
        synth.synthesize("Klatt variant six is speaking", false)
        assertThat(frames, `is`(greaterThan(1000)))
    }
}
