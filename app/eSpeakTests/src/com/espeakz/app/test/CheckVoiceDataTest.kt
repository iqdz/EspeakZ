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

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.CheckVoiceData
import com.espeakz.app.DownloadVoiceData
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks Android's voice data check and install actions.
 *
 * Each activity is opened once for the whole class: ActivityScenario waits 45
 * seconds when it closes an activity that already finished itself, as both of
 * these do.
 */
@RunWith(AndroidJUnit4::class)
class CheckVoiceDataTest {

    @Test
    fun testInstallActionSucceeds() {
        assertThat(installResult.resultCode, `is`(Activity.RESULT_OK))
    }

    @Test
    fun testUnavailableVoices() {
        val unavailable = checkResult().getStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES)
        assertThat(unavailable, `is`(notNullValue()))
        assertThat(unavailable.toString(), `is`("[]"))
    }

    @Test
    fun testAvailableVoicesAdded() {
        // Not asserting on specific voices since new languages are regularly
        // added to eSpeak NG. The test verifies the activity succeeds and
        // returns an available voices list.
        assertThat(checkResult().getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES), `is`(notNullValue()))
    }

    @Test
    fun testAvailableVoicesRemoved() {
        val available = checkResult().getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES)
        assertThat(available, `is`(notNullValue()))

        val removed = VoiceData.voices.map { it.locale }.toSet().filter { it !in available!! }
        assertThat(removed.toString(), `is`("[]"))
    }

    private fun checkResult(): Intent {
        val result = checkActivityResult
        assertThat(result.resultCode, `is`(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS))
        assertThat(result.resultData, `is`(notNullValue()))
        return result.resultData
    }

    private companion object {
        val installResult: Instrumentation.ActivityResult by lazy {
            val intent = Intent(TestLanguages.targetContext, DownloadVoiceData::class.java)
            ActivityScenario.launchActivityForResult<DownloadVoiceData>(intent).use { it.result }
        }

        /** The check after every language is installed, so it must report every voice. */
        val checkActivityResult: Instrumentation.ActivityResult by lazy {
            installResult
            TestLanguages.installAll()
            val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
                .setClass(TestLanguages.targetContext, CheckVoiceData::class.java)
            ActivityScenario.launchActivityForResult<CheckVoiceData>(intent).use { it.result }
        }
    }
}
