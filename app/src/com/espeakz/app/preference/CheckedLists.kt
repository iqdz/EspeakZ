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

package com.espeakz.app.preference

import android.view.KeyEvent
import android.view.View
import android.widget.Checkable
import android.widget.ListView

/**
 * Makes a screen reader say the new state when an entry of a multiple-choice
 * list is checked or unchecked.
 *
 * ListView records the change at once but redraws the entry only at its next
 * layout, after the click has already been reported to accessibility with the
 * old state; TalkBack on Wear OS then says nothing. The item click listener
 * runs before that report, so setting the entry's state there makes the
 * report carry the new one.
 */
internal fun ListView.reportCheckedStateOnClick() {
    setOnItemClickListener { _, view, position, _ ->
        (view as? Checkable)?.isChecked = isItemChecked(position)
    }
}

/**
 * Lets Tab and Shift+Tab leave a list for the controls around it, such as a
 * dialog's buttons. A ListView otherwise moves through its entries on Tab,
 * so a keyboard user only got out with End and then Down. Arrows still move
 * through the entries.
 */
internal fun ListView.leaveOnTab() {
    setOnKeyListener { list, keyCode, event ->
        if (keyCode != KeyEvent.KEYCODE_TAB) return@setOnKeyListener false
        if (event.action == KeyEvent.ACTION_DOWN) {
            val direction = if (event.isShiftPressed) View.FOCUS_BACKWARD else View.FOCUS_FORWARD
            list.focusSearch(direction)?.requestFocus()
        }
        true
    }
}
