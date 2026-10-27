/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2026 Alexandr Epaneshnikov
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
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.EspeakApp
import com.espeakz.app.TtsSettingsActivity
import com.espeakz.app.VoiceSettings
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Settings live in device-protected storage, where TtsService reads them
 * before the device is unlocked. Regression tests for eSpeak NG issue 2536: a
 * Preference persisted through a plain Context created a credential-encrypted
 * file, and the startup migration then copied it over the real settings every
 * time a screen reader restarted the engine.
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class PreferenceStorageTest : TextToSpeechTestCase() {
    private var appContext: Context? = null
    private lateinit var storageContext: Context
    private lateinit var name: String

    @Before
    fun setUpStorage() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        val app = TestLanguages.targetContext
        appContext = app
        storageContext = app.createDeviceProtectedStorageContext()
        name = PreferenceManager.getDefaultSharedPreferencesName(app)
    }

    @After
    fun resetStorage() {
        val app = appContext ?: return
        deviceProtected().edit().clear().commit()
        app.deleteSharedPreferences(name)
    }

    /** The file a plain Context writes to. It must stay empty. */
    private fun credentialEncrypted(): SharedPreferences = appContext!!.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * The file TtsService reads. Fetched anew on every call, because a
     * migration evicts the cached instance.
     */
    private fun deviceProtected(): SharedPreferences = storageContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * The settings screen loads the engine's voices on a worker thread and
     * adds its preferences afterwards, so wait until the one under test is
     * attached before looking at what it persisted.
     */
    private fun waitForPreference(scenario: ActivityScenario<TtsSettingsActivity>, key: String) {
        val attached = AtomicBoolean()
        var i = 0
        while (i < 100 && !attached.get()) {
            scenario.onActivity { activity ->
                val fragment = activity.fragmentManager.findFragmentById(android.R.id.content) as PreferenceFragment?
                attached.set(fragment?.findPreference(key) != null)
            }
            if (!attached.get()) {
                Thread.sleep(100)
            }
            ++i
        }
        assertThat("preference $key attached within 10 s", attached.get(), `is`(true))
    }

    @Test
    fun settingsScreenWritesOnlyToDeviceProtectedStorage() {
        appContext!!.deleteSharedPreferences(name)

        // Attaching the preferences persists their defaults; that is how
        // eSpeak NG issue 2536 created the stray file without the user
        // touching anything.
        ActivityScenario.launch(TtsSettingsActivity::class.java).use { scenario ->
            waitForPreference(scenario, VoiceSettings.PREF_UNICODE_NORMALIZATION)
            assertThat(credentialEncrypted().all.keys, `is`(empty()))
            assertThat(deviceProtected().contains(VoiceSettings.PREF_UNICODE_NORMALIZATION), `is`(true))
        }
    }

    @Test
    fun legacyPreferencesMoveIntoDeviceProtectedStorage() {
        // An install from before device-protected storage: nothing there yet,
        // the settings still sit in the credential-encrypted file.
        deviceProtected().edit().clear().commit()
        appContext!!.deleteSharedPreferences(name)
        credentialEncrypted().edit().putString(VoiceSettings.PREF_RATE, "123").commit()

        EspeakApp.migrateLegacyPreferences(appContext!!, storageContext)

        assertThat(deviceProtected().getString(VoiceSettings.PREF_RATE, null), `is`("123"))
        assertThat(deviceProtected().getBoolean(EspeakApp.PREF_PREFERENCES_MIGRATED, false), `is`(true))
        assertThat(credentialEncrypted().all.keys, `is`(empty()))
    }

    @Test
    fun firstStartMarksDeviceProtectedStorageAsLive() {
        // A fresh install: neither file exists. The first unlocked start must
        // still leave a mark, so that a credential-encrypted file appearing
        // before the user ever opens the settings is discarded, not adopted.
        deviceProtected().edit().clear().commit()
        appContext!!.deleteSharedPreferences(name)

        EspeakApp.migrateLegacyPreferences(appContext!!, storageContext)
        assertThat(deviceProtected().getBoolean(EspeakApp.PREF_PREFERENCES_MIGRATED, false), `is`(true))

        credentialEncrypted().edit().putString(VoiceSettings.PREF_RATE, "123").commit()
        EspeakApp.migrateLegacyPreferences(appContext!!, storageContext)

        assertThat(deviceProtected().contains(VoiceSettings.PREF_RATE), `is`(false))
        assertThat(credentialEncrypted().all.keys, `is`(empty()))
    }

    @Test
    fun strayCredentialEncryptedFileNeverReplacesSettings() {
        deviceProtected().edit().clear().putString(VoiceSettings.PREF_RATE, "123").commit()
        appContext!!.deleteSharedPreferences(name)
        // The exact file eSpeak NG issue 2536 left behind: one default,
        // persisted through a plain Context.
        credentialEncrypted().edit().putBoolean(VoiceSettings.PREF_UNICODE_NORMALIZATION, true).commit()

        EspeakApp.migrateLegacyPreferences(appContext!!, storageContext)

        assertThat(deviceProtected().getString(VoiceSettings.PREF_RATE, null), `is`("123"))
        assertThat(deviceProtected().contains(VoiceSettings.PREF_UNICODE_NORMALIZATION), `is`(false))
        assertThat(credentialEncrypted().all.keys, `is`(empty()))
    }
}
