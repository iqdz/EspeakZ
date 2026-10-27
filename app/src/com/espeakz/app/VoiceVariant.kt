/*
 * Copyright (C) 2026 Harith Alhamdani
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

package com.espeakz.app

/**
 * A voice variant: male or female, or a named eSpeak variant such as klatt6,
 * each optionally young or old.
 */
class VoiceVariant private constructor(name: String, @JvmField val age: Int) {
    /** The eSpeak variant name, or null for plain male or female. */
    @JvmField val variant: String?

    @JvmField val gender: Int

    init {
        when (name) {
            MALE -> {
                variant = null
                gender = SpeechSynthesis.GENDER_MALE
            }
            FEMALE -> {
                variant = null
                gender = SpeechSynthesis.GENDER_FEMALE
            }
            else -> {
                variant = name
                gender = SpeechSynthesis.GENDER_UNSPECIFIED
            }
        }
    }

    override fun toString(): String {
        val ret = when (gender) {
            SpeechSynthesis.GENDER_MALE -> MALE
            SpeechSynthesis.GENDER_FEMALE -> FEMALE
            else -> variant
        }
        return when (age) {
            SpeechSynthesis.AGE_YOUNG -> "$ret-young"
            SpeechSynthesis.AGE_OLD -> "$ret-old"
            else -> ret.toString()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is VoiceVariant) return false
        return other.variant == variant && other.gender == gender && other.age == age
    }

    override fun hashCode(): Int = (variant?.hashCode() ?: 0) * 31 * 31 + gender * 31 + age

    companion object {
        const val MALE = "male"
        const val FEMALE = "female"

        /** The variant EspeakZ speaks with until the user picks one: Klatt 6. */
        const val DEFAULT = "klatt6"

        /** Parses "variant" or "variant-age", or returns null. */
        @JvmStatic
        fun parseVoiceVariant(value: String): VoiceVariant? {
            val parts = value.split("-")
            val age = when (parts.size) {
                1 -> SpeechSynthesis.AGE_ANY // variant
                2 -> if (parts[1] == "young") SpeechSynthesis.AGE_YOUNG else SpeechSynthesis.AGE_OLD // variant-age
                else -> return null
            }
            return VoiceVariant(parts[0], age)
        }
    }
}
