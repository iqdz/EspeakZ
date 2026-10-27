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

@file:Suppress("DEPRECATION")

package com.espeakz.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment

/** The Dual languages page; its content is built by [DualLanguagePreferences]. */
class DualLanguageActivity : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction().replace(android.R.id.content, DualLanguageFragment()).commit()
    }

    class DualLanguageFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            // The same device-protected settings file TtsService reads; see
            // TtsSettingsActivity.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                preferenceManager.setStorageDeviceProtected()
            }
            addPreferencesFromResource(R.xml.preferences)
            val context = activity
            val storage = EspeakData.storage(context)
            // Reading the engine waits for a synthesis in progress, so it
            // happens off the main thread, as on the settings screen.
            Thread({
                val isWatch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
                val state = TtsSettingsActivity.loadEngineState(context, storage)
                TtsSettingsActivity.mainHandler.post {
                    if (context.isFinishing || context.isDestroyed || !isAdded) return@post
                    DualLanguagePreferences.build(this, state, isWatch)
                }
            }, "espeakz-dual-load").start()
        }
    }
}
