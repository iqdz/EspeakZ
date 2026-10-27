/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2012-2013 Reece H. Dunn
 * Copyright (C) 2011 The Android Open Source Project
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
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Returns the sample text string for the language requested. */
class GetSampleText : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locale = intent?.getStringExtra("language")?.let { Locale(it) } ?: Locale.getDefault()
        val text = SpeechSynthesis.getSampleText(baseContext, locale)

        val returnData = Intent()
        returnData.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, text)
        setResult(TextToSpeech.LANG_AVAILABLE, returnData)
        finish()
    }
}
