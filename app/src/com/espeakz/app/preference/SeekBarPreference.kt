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

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import com.espeakz.app.R
import com.espeakz.app.VoiceSettings

/**
 * A dialog preference that edits one or more of eSpeak's numeric voice
 * parameters. One section -- name, value, slider, reset button -- is added to
 * the dialog per parameter.
 *
 * A phone hosts all four parameters in a single dialog. Wear gives each
 * parameter its own preference, and therefore its own dialog, because the
 * rotating crown only ever delivers its scroll events to the focused view and
 * a watch has no D-pad to move focus with. A second slider in the same dialog
 * would be unreachable by the crown, so the split is a requirement of the
 * input model rather than a matter of screen size. See TtsSettingsActivity.
 */
class SeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : DialogPreference(context, attrs, defStyle) {

    /**
     * One editable parameter: where its value is stored, how it is presented,
     * and -- while the dialog is open -- the views bound to it.
     */
    class Parameter(
        internal val key: String,
        internal val title: String,
        internal val min: Int,
        internal val max: Int,
        internal val defaultValue: Int,
        current: Int,
        internal val formatter: String,
    ) {
        /** The value shown by the slider right now. */
        internal var current: Int = current
        /** The value to fall back to if the dialog is cancelled. */
        internal var saved: Int = current

        /** Only the speech rate carries the rate-boost checkbox. */
        internal var hasRateBoost = false
        internal var boost = false
        internal var savedBoost = false

        internal var seekBar: SeekBar? = null
        internal var valueText: TextView? = null
        internal var rateBoost: CheckBox? = null

        /**
         * True between onStartTrackingTouch and onStopTrackingTouch. A drag
         * emits a progress change per pixel, so persisting is deferred to the
         * end of the gesture; see [persist].
         */
        internal var dragging = false

        fun enableRateBoost(enabled: Boolean) {
            hasRateBoost = true
            boost = enabled
            savedBoost = enabled
        }
    }

    private val parameters = ArrayList<Parameter>()

    /**
     * Speaks a sample with the values in the dialog, which are saved as they
     * change. When set, the dialog has a Test voice button that does this
     * and leaves the dialog open.
     */
    var onTest: (() -> Unit)? = null

    init {
        dialogLayoutResource = R.layout.seekbar_preference
        layoutResource = R.layout.information_view
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    fun addParameter(parameter: Parameter) {
        parameters.add(parameter)
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        if (onTest != null) builder.setNeutralButton(R.string.dual_test, null)
    }

    override fun showDialog(state: Bundle?) {
        super.showDialog(state)
        val test = onTest ?: return
        // Set after the dialog shows, so that the button does not close it.
        (dialog as? AlertDialog)?.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener { test() }
    }

    override fun onCreateDialogView(): View {
        val root = super.onCreateDialogView()
        val container = root.findViewById<ViewGroup>(R.id.parameters)
        val inflater = LayoutInflater.from(context)

        for (parameter in parameters) {
            val section = inflater.inflate(R.layout.seekbar_preference_section, container, false)
            bindSection(parameter, section)
            container.addView(section)
        }

        return root
    }

    private fun bindSection(parameter: Parameter, section: View) {
        val seekBar = section.findViewById<SeekBar>(R.id.seekBar)
        parameter.seekBar = seekBar
        parameter.valueText = section.findViewById(R.id.valueText)
        val rateBoost = section.findViewById<CheckBox>(R.id.rateBoost)
        parameter.rateBoost = rateBoost

        section.findViewById<TextView>(R.id.parameterTitle).text = parameter.title

        val reset = section.findViewById<Button>(R.id.resetToDefault)
        // Every section carries an identically labelled button, so name the
        // parameter for anyone who reaches it with a screen reader.
        reset.contentDescription = parameter.title + ", " + reset.text
        reset.setOnClickListener {
            seekBar.progress = parameter.defaultValue - parameter.min
            // setProgress() reports fromUser == false, so persist here rather
            // than leaving it to the progress listener.
            persist(parameter)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                parameter.current = progress + parameter.min
                updateValueText(parameter)

                // TalkBack gestures and D-pad presses arrive here with fromUser
                // set and no surrounding touch gesture, and they are the only
                // notification we get -- onStopTrackingTouch never fires for
                // them. Persisting now is what makes the change audible on the
                // next thing eSpeak speaks.
                if (fromUser && !parameter.dragging) {
                    persist(parameter)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                parameter.dragging = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                parameter.dragging = false
                persist(parameter)
            }
        })

        if (parameter.hasRateBoost) {
            // Checked before the listener is attached, so restoring the stored
            // state does not count as toggling it and write it straight back.
            rateBoost.isChecked = parameter.boost
            rateBoost.setOnCheckedChangeListener { _, isChecked ->
                parameter.boost = isChecked
                updateValueText(parameter)
                persist(parameter)
            }
        } else {
            rateBoost.visibility = View.GONE
        }

        attachRotaryEncoder(parameter, seekBar)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        for (parameter in parameters) {
            // Read before touching the SeekBar: setMax() notifies the progress
            // listener, which writes the slider's position -- still zero at
            // that point -- back over parameter.current.
            val value = parameter.current

            parameter.saved = value
            parameter.savedBoost = parameter.boost

            // Neither call persists anything, because neither reports itself
            // as a change made by the user. That is what keeps opening the
            // dialog from publishing a half-initialised set of values; an
            // earlier revision announced rate = 80 WPM and volume = 0 here.
            parameter.seekBar?.max = parameter.max - parameter.min
            parameter.seekBar?.progress = value - parameter.min
            parameter.current = value

            updateValueText(parameter)
        }

        // Only meaningful for the one-parameter (Wear) dialog, where it hands
        // the crown to the single slider. With four sliders there is nothing
        // sensible to focus, and taking focus would just misdirect the crown.
        if (parameters.size == 1) {
            parameters[0].seekBar?.requestFocus()
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // Update the last saved values so these will be persisted when the
            // dialog is dismissed.
            for (parameter in parameters) {
                parameter.saved = parameter.current
                parameter.savedBoost = parameter.boost
            }
        }
        super.onClick(dialog, which)
    }

    override fun onDismiss(dialog: DialogInterface) {
        // There are 3 ways to dismiss a dialog:
        //   1.  Pressing the OK (positive) button.
        //   2.  Pressing the Cancel (negative) button.
        //   3.  Pressing the Back button.
        //
        // For [1], the new values need to be persisted. For [2] and [3], the
        // old values need to be persisted (so the last saved values are
        // restored). As there is no easy way to override the Dialog's back
        // button pressed handler, the following approach is used:
        //
        // 1.  If the user presses the OK button, the last saved values are
        //     updated to be the new values (see the onClick handler).
        //
        // 2.  In all cases, the last saved values are persisted when the
        //     dialog is closed (in this onDismiss handler).
        for (parameter in parameters) {
            parameter.current = parameter.saved
            parameter.boost = parameter.savedBoost
            persist(parameter)
        }

        val summary = buildSummary()
        callChangeListener(summary)
        setSummary(summary)
    }

    /**
     * The value to show for a parameter, which is not the stored value when
     * the rate boost multiplies it.
     */
    private fun getDisplayValue(parameter: Parameter): Int {
        var value = parameter.current
        if (parameter.hasRateBoost && parameter.boost) {
            value *= VoiceSettings.RATE_BOOST_MULTIPLIER
            val boostedMax = parameter.max * VoiceSettings.RATE_BOOST_MULTIPLIER
            if (value > boostedMax) {
                value = boostedMax
            }
        }
        return value
    }

    private fun updateValueText(parameter: Parameter) {
        val text = String.format(parameter.formatter, getDisplayValue(parameter).toString())
        parameter.valueText?.text = text
        // A bare "50%" is ambiguous once pitch and pitch variation share a
        // dialog, so the announcement carries the parameter name too. This
        // replaces the percentage of the slider's range that a SeekBar would
        // otherwise announce, which means nothing for these values.
        parameter.seekBar?.contentDescription = parameter.title + ", " + text
    }

    /**
     * Writes one parameter through to the settings eSpeak actually reads, so a
     * change is audible immediately rather than at the next dialog dismissal.
     *
     * apply() rather than commit(): the in-memory value is updated before this
     * returns, which is all TtsService needs since it reads the same
     * SharedPreferences instance in this process, and the disk write stays off
     * the main thread. Rotary detents and TalkBack steps arrive one after
     * another, so a synchronous write here would be felt.
     */
    private fun persist(parameter: Parameter) {
        if (!shouldCommit()) {
            return
        }

        val editor = sharedPreferences.edit()
        editor.putString(parameter.key, parameter.current.toString())
        if (parameter.hasRateBoost) {
            editor.putBoolean(VoiceSettings.PREF_RATE_BOOST, parameter.boost)
        }
        editor.apply()
    }

    /**
     * Wire the Wear OS rotating crown to a slider. Rotary input is delivered
     * as ACTION_SCROLL events on SOURCE_ROTARY_ENCODER and only reaches the
     * focused view, so the slider has to be focusable and, in the
     * one-parameter dialog, take focus when it opens. The listener is a no-op
     * on phones (no rotary device ever fires it), so this code path stays
     * harmless on non-watch builds.
     */
    private fun attachRotaryEncoder(parameter: Parameter, seekBar: SeekBar) {
        val range = parameter.max - parameter.min
        val step = maxOf(1, range / 40)

        seekBar.setOnGenericMotionListener { v, ev ->
            if (ev.action != MotionEvent.ACTION_SCROLL || !ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                return@setOnGenericMotionListener false
            }
            val scroll = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (scroll == 0f) {
                return@setOnGenericMotionListener false
            }
            val delta = (if (scroll > 0f) -1 else 1) * step
            val updated = (seekBar.progress + delta).coerceIn(0, range)
            if (updated != seekBar.progress) {
                seekBar.progress = updated
                // As with the reset button: setProgress() is not a user change
                // as far as the listener is concerned, but this is.
                persist(parameter)
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            true
        }
        seekBar.isFocusable = true
        seekBar.isFocusableInTouchMode = true
    }

    /**
     * The preference row's summary: the value on its own when the preference
     * owns a single parameter, otherwise each parameter named and listed.
     */
    fun buildSummary(): String {
        val summary = StringBuilder()
        for (parameter in parameters) {
            if (summary.isNotEmpty()) {
                summary.append(", ")
            }
            if (parameters.size > 1) {
                summary.append(parameter.title).append(": ")
            }
            summary.append(String.format(parameter.formatter, getDisplayValue(parameter).toString()))
        }
        return summary.toString()
    }
}
