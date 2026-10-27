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
import android.preference.DialogPreference

/**
 * The Dual languages language list: every language, with the ones in use
 * checked. Choosing one closes the list and hands it to [onPick]; checking
 * is up to the caller, which then sets [checked] anew. The list opens on
 * [current], the language whose voice the page shows.
 */
class LanguagePickerPreference(context: Context) : DialogPreference(context, null) {

    var labels: List<String> = emptyList()
    var codes: List<String> = emptyList()
    var checked: Set<String> = emptySet()
    var current: String? = null
    var onPick: ((String) -> Unit)? = null

    init {
        isPersistent = false
        positiveButtonText = null
        setNegativeButtonText(android.R.string.cancel)
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        val states = BooleanArray(codes.size) { codes[it] in checked }
        builder.setMultiChoiceItems(labels.toTypedArray(), states) { dialog, which, _ ->
            dialog.dismiss()
            onPick?.invoke(codes[which])
        }
    }

    override fun showDialog(state: Bundle?) {
        super.showDialog(state)
        val list = (dialog as? AlertDialog)?.listView ?: return
        list.leaveOnTab()
        val index = codes.indexOf(current)
        if (index < 0) return
        list.post {
            list.setSelection(index)
            list.requestFocus()
        }
    }
}
