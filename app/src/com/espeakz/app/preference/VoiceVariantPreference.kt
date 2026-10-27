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

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import com.espeakz.app.R
import com.espeakz.app.ResourceIdListAdapter
import com.espeakz.app.VoiceSettings
import com.espeakz.app.VoiceVariant

class VoiceVariantPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : DialogPreference(context, attrs, defStyle) {

    private lateinit var category: Spinner
    private lateinit var variant: Spinner

    private var categoryIndex = 0
    private var variantIndex = 0

    private class VariantData(private val name: Int, private val arg: Any?, value: String) {
        val variant: VoiceVariant = VoiceVariant.parseVoiceVariant(value)!!

        fun getDisplayName(context: Context): String {
            val text = context.getText(name).toString()
            return if (arg == null) text else String.format(text, arg)
        }
    }

    private class VariantDataListAdapter(context: Activity, resources: Array<VariantData>) :
        ArrayAdapter<VariantData>(context, android.R.layout.simple_list_item_1, resources) {

        private val inflater: LayoutInflater = context.layoutInflater

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
            view.findViewById<TextView>(android.R.id.text1).text = getItem(position)!!.getDisplayName(context)
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            getView(position, convertView, parent)
    }

    private val categories = arrayOf(
        R.string.variant_male,
        R.string.variant_female,
        R.string.variant_klatt,
        R.string.variant_nvda,
        R.string.variant_young,
        R.string.variant_old,
        R.string.variant_croak,
        R.string.variant_whisper,
    )

    // Every name matches a file in espeak-ng-data/voices/!v; a name eSpeak
    // cannot find would leave the engine silent.
    private val variants = arrayOf(
        arrayOf( // Male
            VariantData(R.string.variant_default, null, "male"),
            VariantData(R.string.variant_n, 1, "m1"),
            VariantData(R.string.variant_n, 2, "m2"),
            VariantData(R.string.variant_n, 3, "m3"),
            VariantData(R.string.variant_n, 4, "m4"),
            VariantData(R.string.variant_n, 5, "m5"),
            VariantData(R.string.variant_n, 6, "m6"),
            VariantData(R.string.variant_n, 7, "m7"),
            VariantData(R.string.variant_n, 8, "m8"),
        ),
        arrayOf( // Female
            VariantData(R.string.variant_default, null, "female"),
            VariantData(R.string.variant_n, 1, "f1"),
            VariantData(R.string.variant_n, 2, "f2"),
            VariantData(R.string.variant_n, 3, "f3"),
            VariantData(R.string.variant_n, 4, "f4"),
            VariantData(R.string.variant_n, 5, "f5"),
        ),
        arrayOf( // Klatt
            VariantData(R.string.variant_n, 1, "klatt"),
            VariantData(R.string.variant_n, 2, "klatt2"),
            VariantData(R.string.variant_n, 3, "klatt3"),
            VariantData(R.string.variant_n, 4, "klatt4"),
            VariantData(R.string.variant_n, 5, "klatt5"),
            VariantData(R.string.variant_n, 6, "klatt6"),
        ),
        arrayOf( // NVDA
            VariantData(R.string.variant_adam, null, "adam"),
            VariantData(R.string.variant_alex, null, "Alex"),
            VariantData(R.string.variant_alicia, null, "Alicia"),
            VariantData(R.string.variant_andy, null, "Andy"),
            VariantData(R.string.variant_andrea, null, "Andrea"),
            VariantData(R.string.variant_anika, null, "anika"),
            VariantData(R.string.variant_anika_robot, null, "anikaRobot"),
            VariantData(R.string.variant_annie, null, "Annie"),
            VariantData(R.string.variant_anouncer, null, "announcer"),
            VariantData(R.string.variant_antonio, null, "antonio"),
            VariantData(R.string.variant_anxious_andy, null, "AnxiousAndy"),
            VariantData(R.string.variant_aunty, null, "aunty"),
            VariantData(R.string.variant_belinda, null, "belinda"),
            VariantData(R.string.variant_benjamin, null, "benjamin"),
            VariantData(R.string.variant_boris, null, "boris"),
            VariantData(R.string.variant_caleb, null, "caleb"),
            VariantData(R.string.variant_david, null, "david"),
            VariantData(R.string.variant_demonic, null, "Demonic"),
            VariantData(R.string.variant_denis, null, "Denis"),
            VariantData(R.string.variant_diogo, null, "Diogo"),
            VariantData(R.string.variant_ed, null, "ed"),
            VariantData(R.string.variant_edward, null, "edward"),
            VariantData(R.string.variant_edward2, null, "edward2"),
            VariantData(R.string.variant_gene, null, "Gene"),
            VariantData(R.string.variant_gene2, null, "Gene2"),
            VariantData(R.string.variant_grandma, null, "grandma"),
            VariantData(R.string.variant_grandpa, null, "grandpa"),
            VariantData(R.string.variant_gustave, null, "gustave"),
            VariantData(R.string.variant_henrique, null, "Henrique"),
            VariantData(R.string.variant_hugo, null, "Hugo"),
            VariantData(R.string.variant_iven, null, "iven"),
            VariantData(R.string.variant_iven2, null, "iven2"),
            VariantData(R.string.variant_iven3, null, "iven3"),
            VariantData(R.string.variant_iven4, null, "iven4"),
            VariantData(R.string.variant_jacky, null, "Jacky"),
            VariantData(R.string.variant_john, null, "john"),
            VariantData(R.string.variant_kaukovalta, null, "kaukovalta"),
            VariantData(R.string.variant_lee, null, "Lee"),
            VariantData(R.string.variant_linda, null, "linda"),
            VariantData(R.string.variant_marcelo, null, "marcelo"),
            VariantData(R.string.variant_marco, null, "Marco"),
            VariantData(R.string.variant_mario, null, "Mario"),
            VariantData(R.string.variant_max, null, "max"),
            VariantData(R.string.variant_miguel, null, "miguel"),
            VariantData(R.string.variant_michael, null, "Michael"),
            VariantData(R.string.variant_michel, null, "michel"),
            VariantData(R.string.variant_mike, null, "Mike"),
            VariantData(R.string.variant_mr_serious, null, "Mr serious"),
            VariantData(R.string.variant_nguyen, null, "Nguyen"),
            VariantData(R.string.variant_norbert, null, "norbert"),
            VariantData(R.string.variant_pablo, null, "pablo"),
            VariantData(R.string.variant_paul, null, "paul"),
            VariantData(R.string.variant_pedro, null, "pedro"),
            VariantData(R.string.variant_quincy, null, "quincy"),
            VariantData(R.string.variant_ricishay_max, null, "RicishayMax"),
            VariantData(R.string.variant_ricishay_max2, null, "RicishayMax2"),
            VariantData(R.string.variant_ricishay_max3, null, "RicishayMax3"),
            VariantData(R.string.variant_rob, null, "rob"),
            VariantData(R.string.variant_robert, null, "robert"),
            VariantData(R.string.variant_robosoft, null, "robosoft"),
            VariantData(R.string.variant_robosoft2, null, "robosoft2"),
            VariantData(R.string.variant_robosoft3, null, "robosoft3"),
            VariantData(R.string.variant_robosoft4, null, "robosoft4"),
            VariantData(R.string.variant_robosoft5, null, "robosoft5"),
            VariantData(R.string.variant_robosoft6, null, "robosoft6"),
            VariantData(R.string.variant_robosoft7, null, "robosoft7"),
            VariantData(R.string.variant_robosoft8, null, "robosoft8"),
            VariantData(R.string.variant_sandro, null, "sandro"),
            VariantData(R.string.variant_shelby, null, "shelby"),
            VariantData(R.string.variant_steph, null, "steph"),
            VariantData(R.string.variant_steph2, null, "steph2"),
            VariantData(R.string.variant_steph3, null, "steph3"),
            VariantData(R.string.variant_storm, null, "Storm"),
            VariantData(R.string.variant_travis, null, "travis"),
            VariantData(R.string.variant_tweaky, null, "Tweaky"),
            VariantData(R.string.variant_unirobot, null, "UniRobot"),
            VariantData(R.string.variant_victor, null, "victor"),
            VariantData(R.string.variant_zac, null, "zac"),
        ),
        arrayOf( // Young
            VariantData(R.string.variant_male, null, "male-young"),
            VariantData(R.string.variant_female, null, "female-young"),
        ),
        arrayOf( // Old
            VariantData(R.string.variant_male, null, "male-old"),
            VariantData(R.string.variant_female, null, "female-old"),
        ),
        arrayOf( // Croak
            VariantData(R.string.variant_male, null, "croak"),
        ),
        arrayOf( // Whisper
            VariantData(R.string.variant_male, null, "whisper"),
            VariantData(R.string.variant_female, null, "whisperf"),
        ),
    )

    init {
        dialogLayoutResource = R.layout.voice_variant_preference
        layoutResource = R.layout.information_view
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
    }

    fun setVoiceVariant(voiceVariant: VoiceVariant) {
        for (i in variants.indices) {
            val j = variants[i].indexOfFirst { it.variant == voiceVariant }
            if (j >= 0) {
                categoryIndex = i
                variantIndex = j
                break
            }
        }
        onDataChanged()
    }

    override fun onCreateDialogView(): View {
        val root = super.onCreateDialogView()
        category = root.findViewById(R.id.category)
        variant = root.findViewById(R.id.variant)
        return root
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        // Cache the indices so they don't get overwritten by the OnItemSelectedListener handlers.
        val initialCategory = categoryIndex
        val initialVariant = variantIndex
        val activity = context as Activity

        category.adapter = ResourceIdListAdapter(activity, categories)
        category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var initializing = true

            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
                variant.adapter = VariantDataListAdapter(activity, variants[position])
                if (initializing) {
                    variant.setSelection(initialVariant)
                    initializing = false
                }
                categoryIndex = position
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {
            }
        }
        variant.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
                variantIndex = position
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {
            }
        }

        category.setSelection(initialCategory)
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            onDataChanged()
            if (shouldCommit()) {
                editor?.let {
                    it.putString(VoiceSettings.PREF_VARIANT, variants[categoryIndex][variantIndex].variant.toString())
                    it.commit()
                }
            }
        }
        super.onClick(dialog, which)
    }

    private fun onDataChanged() {
        val categoryName = context.getText(categories[categoryIndex])
        val variantName = variants[categoryIndex][variantIndex].getDisplayName(context)
        callChangeListener(String.format("%s (%s)", categoryName, variantName))
    }
}
