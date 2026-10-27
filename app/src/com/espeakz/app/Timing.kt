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

package com.espeakz.app

import android.os.SystemClock
import android.util.Log

/**
 * Runs [block] and, in debug builds, logs how long it took under the tag
 * EspeakZTiming, so start-up can be measured on a device with
 * adb logcat -s EspeakZTiming.
 */
internal inline fun <T> timed(what: String, block: () -> T): T {
    if (!BuildConfig.DEBUG) return block()
    val started = SystemClock.elapsedRealtime()
    try {
        return block()
    } finally {
        Log.i("EspeakZTiming", "$what: ${SystemClock.elapsedRealtime() - started} ms")
    }
}
