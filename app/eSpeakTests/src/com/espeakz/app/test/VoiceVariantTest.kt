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

package com.espeakz.app.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.espeakz.app.SpeechSynthesis
import com.espeakz.app.VoiceVariant
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceVariantTest {

    private fun check(value: String, variant: String?, gender: Int, age: Int) {
        val parsed = VoiceVariant.parseVoiceVariant(value)!!
        if (variant == null) {
            assertThat(parsed.variant, `is`(nullValue()))
        } else {
            assertThat(parsed.variant, `is`(variant))
        }
        assertThat(parsed.gender, `is`(gender))
        assertThat(parsed.age, `is`(age))
        assertThat(parsed.toString(), `is`(value))
    }

    @Test
    fun testMaleVoiceVariant() = check("male", null, SpeechSynthesis.GENDER_MALE, SpeechSynthesis.AGE_ANY)

    @Test
    fun testFemaleVoiceVariant() = check("female", null, SpeechSynthesis.GENDER_FEMALE, SpeechSynthesis.AGE_ANY)

    @Test
    fun testNamedVoiceVariant() = check("klatt1", "klatt1", SpeechSynthesis.GENDER_UNSPECIFIED, SpeechSynthesis.AGE_ANY)

    @Test
    fun testDefaultVoiceVariantIsKlatt6() {
        assertThat(VoiceVariant.DEFAULT, `is`("klatt6"))
        check(VoiceVariant.DEFAULT, "klatt6", SpeechSynthesis.GENDER_UNSPECIFIED, SpeechSynthesis.AGE_ANY)
    }

    @Test
    fun testMaleVoiceVariantYoung() = check("male-young", null, SpeechSynthesis.GENDER_MALE, SpeechSynthesis.AGE_YOUNG)

    @Test
    fun testFemaleVoiceVariantYoung() = check("female-young", null, SpeechSynthesis.GENDER_FEMALE, SpeechSynthesis.AGE_YOUNG)

    @Test
    fun testNamedVoiceVariantYoung() = check("klatt2-young", "klatt2", SpeechSynthesis.GENDER_UNSPECIFIED, SpeechSynthesis.AGE_YOUNG)

    @Test
    fun testMaleVoiceVariantOld() = check("male-old", null, SpeechSynthesis.GENDER_MALE, SpeechSynthesis.AGE_OLD)

    @Test
    fun testFemaleVoiceVariantOld() = check("female-old", null, SpeechSynthesis.GENDER_FEMALE, SpeechSynthesis.AGE_OLD)

    @Test
    fun testNamedVoiceVariantOld() = check("klatt3-old", "klatt3", SpeechSynthesis.GENDER_UNSPECIFIED, SpeechSynthesis.AGE_OLD)
}
