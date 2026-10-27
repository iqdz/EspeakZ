/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2025
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
import android.preference.MultiSelectListPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import com.espeakz.app.LanguageSettings
import com.espeakz.app.R

/**
 * Multi-select preference with a custom dialog that includes explicit
 * Select All / Deselect All buttons alongside OK/Cancel.
 */
open class SupportedLanguagesPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MultiSelectListPreference(context, attrs) {

    private var dialogEntryValues: Array<CharSequence> = emptyArray()
    private var listView: ListView? = null
    private var entryCount = 0

    init {
        key = LanguageSettings.PREF_SUPPORTED_LANGUAGES
        isPersistent = true
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        builder.setPositiveButton(android.R.string.ok, null)
        builder.setNegativeButton(android.R.string.cancel, null)

        val entries = entries
        val values = entryValues
        check(entries != null && values != null) { "SupportedLanguagesPreference requires entries and entryValues." }

        dialogEntryValues = values
        entryCount = values.size

        val selected = getValues()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.supported_languages_dialog, null)

        val list = dialogView.findViewById<ListView>(R.id.supported_languages_list)
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, entries)
        for (i in values.indices) {
            list.setItemChecked(i, values[i].toString() in selected)
        }
        list.reportCheckedStateOnClick()
        list.leaveOnTab()
        listView = list

        dialogView.findViewById<Button>(R.id.button_select_all).setOnClickListener { setAll(true) }
        dialogView.findViewById<Button>(R.id.button_deselect_all).setOnClickListener { setAll(false) }

        builder.setView(dialogView)
        builder.setTitle(dialogTitle)
        builder.setIcon(dialogIcon)
        builder.setCancelable(true)
    }

    private fun setAll(checked: Boolean) {
        val list = listView ?: return
        for (i in dialogEntryValues.indices) {
            list.setItemChecked(i, checked)
        }
    }

    private fun collectSelections(): Set<String> {
        val list = listView ?: return emptySet()
        return dialogEntryValues.indices.filter { list.isItemChecked(it) }.map { dialogEntryValues[it].toString() }.toSet()
    }

    override fun showDialog(state: Bundle?) {
        super.showDialog(state)

        val dialog = dialog as? AlertDialog ?: return

        // Wire default dialog buttons.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val selections = collectSelections()
            if (selections.isEmpty()) {
                Toast.makeText(context, R.string.espeak_supported_languages_guard, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (callChangeListener(selections)) {
                setValues(selections)
            }
            dialog.dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { dialog.cancel() }
    }

    // "All languages" is stored as the absence of the key, so that TtsService
    // exposes every voice, including ones added by a later update.
    override fun persistStringSet(values: Set<String>?): Boolean {
        if (!shouldPersist()) return false
        val total = if (entryCount > 0) entryCount else (entryValues?.size ?: 0)

        val editor = sharedPreferences.edit()
        if (values == null || (total > 0 && values.size >= total)) {
            // Treat as "all": remove the preference key so TtsService exposes everything.
            editor.remove(key)
        } else {
            editor.putStringSet(key, HashSet(values))
        }
        editor.apply()
        return true
    }

    override fun getPersistedStringSet(defaultReturnValue: Set<String>?): Set<String>? {
        if (!shouldPersist()) return defaultReturnValue
        val stored = sharedPreferences.getStringSet(key, defaultReturnValue)
        return if (stored == null) defaultReturnValue else HashSet(stored)
    }
}
