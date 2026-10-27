/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2022 Beka Gozalishvili
 * Copyright (C) 2013 Reece H. Dunn
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
import android.content.DialogInterface
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import com.espeakz.app.R
import com.espeakz.app.SpeechSynthesis
import com.espeakz.app.VoiceSettings

class SpeakPunctuationPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : DialogPreference(context, attrs, defStyle) {

    private lateinit var all: RadioButton
    private lateinit var custom: RadioButton
    private lateinit var none: RadioButton
    private lateinit var punctuationCharacters: EditText

    private var level = SpeechSynthesis.PUNCT_NONE
    private var characters: String? = null

    init {
        dialogLayoutResource = R.layout.speak_punctuation_preference
        layoutResource = R.layout.information_view
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    /**
     * The current setting, read by the caller off the main thread: reading the
     * level from the engine can wait for a synthesis in progress.
     */
    fun setPunctuation(level: Int, characters: String?) {
        this.level = level
        this.characters = characters
        onDataChanged(level, characters)
    }

    private fun onDataChanged(level: Int, characters: String?) {
        when (level) {
            SpeechSynthesis.PUNCT_ALL -> callChangeListener(context.getText(R.string.punctuation_all))
            SpeechSynthesis.PUNCT_SOME ->
                if (characters.isNullOrEmpty()) {
                    callChangeListener(context.getText(R.string.punctuation_none))
                } else {
                    callChangeListener(context.getString(R.string.punctuation_custom_fmt, characters))
                }
            SpeechSynthesis.PUNCT_NONE -> callChangeListener(context.getText(R.string.punctuation_none))
        }
    }

    override fun onCreateDialogView(): View {
        val root = super.onCreateDialogView()
        all = root.findViewById(R.id.all)
        custom = root.findViewById(R.id.custom)
        none = root.findViewById(R.id.none)
        punctuationCharacters = root.findViewById(R.id.punctuation_characters)
        return root
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        when (level) {
            SpeechSynthesis.PUNCT_ALL -> all.toggle()
            SpeechSynthesis.PUNCT_SOME -> custom.toggle()
            SpeechSynthesis.PUNCT_NONE -> none.toggle()
        }

        punctuationCharacters.setText(characters)
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            val characters = punctuationCharacters.text?.toString()
            val level = when {
                none.isChecked -> SpeechSynthesis.PUNCT_NONE
                characters.isNullOrEmpty() -> if (all.isChecked) SpeechSynthesis.PUNCT_ALL else SpeechSynthesis.PUNCT_NONE
                else -> if (all.isChecked) SpeechSynthesis.PUNCT_ALL else SpeechSynthesis.PUNCT_SOME
            }

            this.level = level
            this.characters = characters
            onDataChanged(level, characters)

            if (shouldCommit()) {
                editor?.let {
                    it.putString(VoiceSettings.PREF_PUNCTUATION_CHARACTERS, characters)
                    it.putString(VoiceSettings.PREF_PUNCTUATION_LEVEL, level.toString())
                    it.commit()
                }
            }
        }
        super.onClick(dialog, which)
    }
}
