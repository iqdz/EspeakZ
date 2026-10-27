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

package com.espeakz.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Android's "install voice data" action. The voice data installs by itself
 * when the app starts, so this waits for that install, which is done or
 * nearly done by the time this opens, and closes. When it was opened from
 * Android's text-to-speech settings, which start it without waiting for an
 * answer, it then opens Voice languages, where more languages are added. A
 * caller waiting for the answer gets it at once instead, as a screen opened
 * over it would hold the answer back until that screen closed.
 */
class DownloadVoiceData : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.download_voice_data)
        val storage = EspeakData.storage(this)
        Thread({
            val installed = EspeakData.ensureInstalled(storage)
            runOnUiThread {
                if (installed && callingActivity == null) {
                    startActivity(
                        Intent(this, TtsSettingsActivity::class.java)
                            .putExtra(TtsSettingsActivity.EXTRA_VOICE_LANGUAGES, true),
                    )
                }
                setResult(if (installed) RESULT_OK else RESULT_CANCELED)
                finish()
            }
        }, "espeakz-install-voice-data").start()
    }
}
