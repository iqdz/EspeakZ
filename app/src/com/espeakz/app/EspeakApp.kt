/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2022 Beka Gozalishvili
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

import android.annotation.TargetApi
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.preference.PreferenceManager

class EspeakApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val appContext = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storage = appContext.createDeviceProtectedStorageContext()
            storageContext = storage
            migrateLegacyPreferences(appContext, storage)
            if (!appContext.getSystemService(UserManager::class.java).isUserUnlocked) {
                // Started at boot, for a direct-boot-aware screen reader. The
                // credential-encrypted file cannot be read yet, so run the
                // migration again when the user unlocks instead of leaving it
                // to the next process restart, which may be days away.
                // ACTION_USER_UNLOCKED is a protected system broadcast, exempt
                // from the exported flag that targetSdk 34 requires otherwise.
                appContext.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        appContext.unregisterReceiver(this)
                        migrateLegacyPreferences(appContext, storage)
                    }
                }, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            }
        } else {
            storageContext = appContext
        }

        // Install the voice data the moment the process starts, whatever
        // started it. TtsService, the voice data check and the settings screen
        // all wait for this same install instead of running their own, so the
        // first request a screen reader sends is spoken, not dropped.
        val storage = storageContext!!
        Thread({ EspeakData.ensureInstalled(storage) }, "espeakz-voice-data").start()
    }

    companion object {
        /**
         * Written to device-protected storage the first time
         * [migrateLegacyPreferences] runs with the credential-encrypted file
         * readable, so that file is never empty again after the first unlocked
         * start and a stray credential-encrypted file can never be adopted.
         */
        const val PREF_PREFERENCES_MIGRATED = "espeak_preferences_migrated"

        @Volatile
        private var storageContext: Context? = null

        /** Device-protected storage, where every setting and the voice data live. */
        @JvmStatic
        fun getStorageContext(): Context? = storageContext

        /**
         * Adopts the preferences of an install that predates device-protected
         * storage and still keeps them in the credential-encrypted file. Runs
         * at process start, before any component reads the settings, and again
         * on unlock when the process started before the user unlocked.
         *
         * Everything in this app reads and writes the device-protected file,
         * and [PREF_PREFERENCES_MIGRATED] lands there the first time the
         * credential-encrypted file is readable, so once the device-protected
         * file holds anything it is the source of truth and there is nothing
         * left to migrate. A credential-encrypted file that shows up after that
         * point can only be a stray write through a plain Context, and
         * moveSharedPreferencesFrom() would copy it over the user's settings:
         * that is how eSpeak NG issue 2536 wiped every setting on each screen
         * reader restart. Such a file is discarded instead, which turns that
         * class of bug into a setting that does not take effect -- visible,
         * and harmless.
         */
        @JvmStatic
        @TargetApi(Build.VERSION_CODES.N)
        @Suppress("DEPRECATION")
        fun migrateLegacyPreferences(appContext: Context, storageContext: Context) {
            val name = PreferenceManager.getDefaultSharedPreferencesName(appContext)
            if (storageContext.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()) {
                appContext.deleteSharedPreferences(name)
                return
            }
            if (!appContext.getSystemService(UserManager::class.java).isUserUnlocked) {
                // Credential-encrypted storage is not readable before the first
                // unlock; onCreate() retries this on ACTION_USER_UNLOCKED.
                return
            }
            storageContext.moveSharedPreferencesFrom(appContext, name)
            // The move evicts the cached instance, so fetch the file anew.
            storageContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_PREFERENCES_MIGRATED, true).apply()
        }
    }
}
