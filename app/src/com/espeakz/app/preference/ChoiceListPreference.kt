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

package com.espeakz.app.preference

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.preference.ListPreference

/**
 * A single-choice list whose dialog opens with focus on the current choice.
 *
 * The framework's dialog scrolls the checked entry into view but leaves the
 * keyboard and D-pad on the first entry, so in a long list such as the
 * languages the user starts far from where they were.
 */
class ChoiceListPreference(context: Context) : ListPreference(context) {

    /** Shows the chosen entry as the summary, from the saved value on. */
    var entryAsSummary = false

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        super.onSetInitialValue(restoreValue, defaultValue)
        if (entryAsSummary) summary = entry
    }

    override fun setValue(value: String?) {
        super.setValue(value)
        if (entryAsSummary) summary = entry
    }

    override fun showDialog(state: Bundle?) {
        super.showDialog(state)
        val list = (dialog as? AlertDialog)?.listView ?: return
        list.leaveOnTab()
        val index = findIndexOfValue(value)
        if (index < 0) return
        list.post {
            list.setSelection(index)
            list.requestFocus()
        }
    }
}
