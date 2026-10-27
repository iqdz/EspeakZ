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

/*
 * The About EspeakZ screen: the author, the fork, its changes, the credits
 * and the license notices the GPL asks an interactive program to show.
 */

package com.espeakz.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)

        findViewById<TextView>(R.id.about_version).text = getString(R.string.about_version, BuildConfig.VERSION_NAME)

        addEntries(R.id.about_privacy, R.array.about_privacy)
        addEntries(R.id.about_goals, R.array.about_goals)
        addEntries(R.id.about_changes, R.array.about_changes)
        addEntries(R.id.about_credits, R.array.about_credits)
        addEntries(R.id.about_license, R.array.about_license)
    }

    /**
     * Adds one text per entry, so that a screen reader stops on each entry
     * instead of reading a whole section as one block.
     */
    private fun addEntries(containerId: Int, entriesId: Int) {
        val container = findViewById<LinearLayout>(containerId)
        for (entry in resources.getStringArray(entriesId)) {
            val view = layoutInflater.inflate(R.layout.about_entry, container, false) as TextView
            view.text = entry
            container.addView(view)
        }
    }
}
