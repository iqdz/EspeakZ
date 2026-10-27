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

import android.content.Context
import android.preference.DialogPreference
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import com.espeakz.app.EspeakData
import com.espeakz.app.R

/**
 * Chooses which languages' voice data is installed on the device. English is
 * always installed, so it is not in the list. The choice is applied by
 * [onLanguagesChosen], which installs and removes the dictionaries; nothing is
 * stored in the settings.
 */
class VoiceLanguagesPreference(context: Context) : DialogPreference(context, null) {

    /** The languages the user can choose, without English. */
    var languages: List<EspeakData.Language> = emptyList()

    /** The dictionaries installed now. */
    var installed: Set<String> = emptySet()

    /** Receives the dictionaries the user chose, when they press OK. */
    var onLanguagesChosen: ((Set<String>) -> Unit)? = null

    private var listView: ListView? = null

    init {
        isPersistent = false
        dialogLayoutResource = R.layout.supported_languages_dialog
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    /** Opens the dialog, as selecting the row does. */
    fun open() {
        showDialog(null)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        val list = view.findViewById<ListView>(R.id.supported_languages_list)
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, languages.map { it.displayName })
        languages.forEachIndexed { i, language -> list.setItemChecked(i, language.dictionary in installed) }
        list.reportCheckedStateOnClick()
        list.leaveOnTab()
        listView = list

        view.findViewById<Button>(R.id.button_select_all).setOnClickListener { setAll(true) }
        view.findViewById<Button>(R.id.button_deselect_all).setOnClickListener { setAll(false) }
    }

    private fun setAll(checked: Boolean) {
        val list = listView ?: return
        for (i in languages.indices) {
            list.setItemChecked(i, checked)
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        val list = listView ?: return
        listView = null
        if (!positiveResult) return
        val chosen = languages.indices.filter { list.isItemChecked(it) }.map { languages[it].dictionary }.toSet()
        onLanguagesChosen?.invoke(chosen + EspeakData.ENGLISH)
    }
}
