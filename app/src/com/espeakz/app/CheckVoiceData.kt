/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2022 Beka Gozalishvili
 * Copyright (C) 2012-2013 Reece H. Dunn
 * Copyright (C) 2009 The Android Open Source Project
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
 * This Activity is used by Android to get the list of languages to display
 * to the user when selecting the text-to-speech language. This is by locale,
 * not voice name.
 */

package com.espeakz.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.speech.tts.TextToSpeech.Engine
import android.util.Log
import java.util.Locale

/**
 * Answers Android's voice data check. It waits for the voice data install
 * instead of reporting the data as missing, so Android never needs to send the
 * user to an install step for the installed languages. The languages EspeakZ
 * ships but has not installed are reported as unavailable, so Android can
 * offer to install them, which opens Voice languages; see DownloadVoiceData.
 */
class CheckVoiceData : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = EspeakData.storage(this)
        Thread({
            val available = ArrayList<String>()
            val unavailable = ArrayList<String>()
            val result = if (EspeakData.ensureInstalled(storage)) {
                @Suppress("DEPRECATION")
                val prefs = PreferenceManager.getDefaultSharedPreferences(storage)
                val synth = SpeechSynthesis(storage, null)
                val voices = LanguageSettings.filterVoices(synth.getAvailableVoices(), prefs)
                if (BuildConfig.DEBUG) {
                    val selected = LanguageSettings.getSelectedLanguages(prefs)
                    Log.i(TAG, "selected=${selected?.size ?: "ALL"}, exposing=${voices.size}")
                }
                voices.mapTo(available) { it.toString() }
                synth.getUnavailableVoices().map { it.toString() }.distinct().filterTo(unavailable) { it !in available }
                Engine.CHECK_VOICE_DATA_PASS
            } else {
                unavailable.add(Locale.ENGLISH.toString())
                Engine.CHECK_VOICE_DATA_FAIL
            }
            runOnUiThread { returnResults(result, available, unavailable) }
        }, "espeakz-check-voice-data").start()
    }

    private fun returnResults(result: Int, available: ArrayList<String>, unavailable: ArrayList<String>) {
        val returnData = Intent()
        returnData.putStringArrayListExtra(Engine.EXTRA_AVAILABLE_VOICES, available)
        returnData.putStringArrayListExtra(Engine.EXTRA_UNAVAILABLE_VOICES, unavailable)
        setResult(result, returnData)
        finish()
    }

    private companion object {
        const val TAG = "CheckVoiceData"
    }
}
