/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2022 Beka Gozalishvili
 * Copyright (C) 2013 Reece H. Dunn
 * Copyright (C) 2011 The Android Open Source Project
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

package com.espeakz.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.MultiSelectListPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import com.espeakz.app.preference.ChoiceListPreference
import com.espeakz.app.preference.SeekBarPreference
import com.espeakz.app.preference.SpeakPunctuationPreference
import com.espeakz.app.preference.SupportedLanguagesPreference
import com.espeakz.app.preference.VoiceLanguagesPreference
import com.espeakz.app.preference.VoiceVariantPreference
import java.util.Locale

class TtsSettingsActivity : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction().replace(android.R.id.content, PrefsEspeakFragment()).commit()
    }

    class PrefsEspeakFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // The fragment has its own PreferenceManager, separate from the
            // activity's. Everything on this screen persists through it, so it
            // must use the device-protected file that TtsService reads. A
            // Preference left on the default credential-encrypted storage has
            // no effect on speech, and its stray file is what eSpeak NG issue
            // 2536 copied over the real settings on every screen reader
            // restart.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                preferenceManager.setStorageDeviceProtected()
            }
            addPreferencesFromResource(R.xml.preferences)
            createPreferences(this)
        }

        override fun onResume() {
            super.onResume()
            // Back from the Dual languages page: its row sums up what was chosen there.
            (findPreference("espeakz_dual_languages") as? DualLanguagePreferences.Entry)?.refresh()
            // Back from Android's battery dialog or list.
            findPreference(PREF_KEEP_RUNNING)?.let { updateKeepRunningSummary(it) }
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            val uri = data?.data
            if (requestCode == REQUEST_IMPORT_DICTIONARY && resultCode == Activity.RESULT_OK && uri != null) {
                importDictionary(this, uri)
            }
        }
    }

    /** What the screen needs from the engine, gathered off the main thread. */
    internal class EngineState(
        val voices: List<Voice>,
        val labels: Map<Voice, String>,
        val languages: List<EspeakData.Language>,
        val installed: Set<String>,
        val variant: VoiceVariant,
        val punctuationLevel: Int,
        val punctuationCharacters: String?,
        val rate: SeekBarPreference.Parameter,
        val pitch: SeekBarPreference.Parameter,
        val pitchRange: SeekBarPreference.Parameter,
        val volume: SeekBarPreference.Parameter,
    )

    internal companion object {
        /**
         * Identifies the combined voice-parameters preference. Nothing is
         * stored under it -- the preference writes the individual
         * VoiceSettings keys -- but a Preference without a key cannot save its
         * instance state, so its dialog would not survive a rotation.
         */
        const val PREF_VOICE_PARAMETERS = "espeak_voice_parameters"
        const val PREF_VOICE_LANGUAGES = "espeakz_voice_languages"
        const val PREF_IMPORT_DICTIONARY = "espeakz_import_dictionary"
        const val PREF_KEEP_RUNNING = "espeakz_keep_running"

        /** eSpeak's default speech rate, which another engine's normal speed is taken as. */
        const val DEFAULT_RATE_WPM = 175

        /** Opens Voice languages as soon as the screen is built; see DownloadVoiceData. */
        const val EXTRA_VOICE_LANGUAGES = "com.espeakz.app.extra.VOICE_LANGUAGES"
        const val REQUEST_IMPORT_DICTIONARY = 1

        val mainHandler = Handler(Looper.getMainLooper())

        /**
         * Gathers what the preference screen needs from the engine on a
         * worker thread, then builds the screen on the main thread.
         *
         * Constructing the engine waits for the voice data install, and every
         * call into the engine waits for a synthesis in progress, such as a
         * screen reader reading this very screen. None of that may happen on
         * the thread that has to stay responsive. The Preference objects
         * themselves are built on the main thread, which is required: they
         * bind to the hosting PreferenceGroup.
         */
        fun createPreferences(fragment: PreferenceFragment) {
            val context = fragment.activity
            val storage = EspeakData.storage(context)
            Thread({
                val isWatch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
                val state = loadEngineState(context, storage)
                mainHandler.post {
                    if (isGone(context) || !fragment.isAdded) return@post
                    addPreferences(fragment, state, isWatch)
                }
            }, "espeakz-settings-load").start()
        }

        fun loadEngineState(context: Context, storage: Context): EngineState {
            val engine = SpeechSynthesis(storage, null)
            val prefs = PreferenceManager.getDefaultSharedPreferences(storage)
            migrateSettings(prefs, engine)
            val settings = VoiceSettings(prefs, engine)
            val voices = engine.getAvailableVoices()
            return EngineState(
                voices = voices,
                labels = voices.associateWith { voiceLabel(storage, it) },
                languages = EspeakData.availableLanguages(storage),
                installed = EspeakData.installedDictionaries(storage),
                variant = settings.voiceVariant,
                punctuationLevel = settings.punctuationLevel,
                punctuationCharacters = settings.punctuationCharacters,
                rate = voiceParameter(context, prefs, engine.Rate, VoiceSettings.PREF_RATE, R.string.setting_default_rate),
                pitch = voiceParameter(context, prefs, engine.Pitch, VoiceSettings.PREF_PITCH, R.string.setting_default_pitch),
                pitchRange = voiceParameter(context, prefs, engine.PitchRange, VoiceSettings.PREF_PITCH_RANGE, R.string.espeak_pitch_range),
                volume = voiceParameter(context, prefs, engine.Volume, VoiceSettings.PREF_VOLUME, R.string.espeak_volume),
            )
        }

        /** Moves the old eyes-free settings to the current ones, and sets the defaults. */
        fun migrateSettings(prefs: SharedPreferences, engine: SpeechSynthesis) {
            val editor = prefs.edit()

            if (prefs.getString(VoiceSettings.PREF_PITCH, null) == null) {
                // Try the old eyes-free setting:
                val pitch = prefs.getString(VoiceSettings.PREF_DEFAULT_PITCH, "100")!!.toIntOrNull() ?: 100
                editor.putString(VoiceSettings.PREF_PITCH, (pitch / 2).toString())
            }

            if (prefs.getString(VoiceSettings.PREF_RATE, null) == null) {
                // Try the old eyes-free setting:
                val defaultValue = engine.Rate.defaultValue
                val rate = prefs.getString(VoiceSettings.PREF_DEFAULT_RATE, "100")!!.toIntOrNull() ?: 100
                val rateValue = ((rate / 100) * defaultValue).coerceIn(defaultValue, engine.Rate.maxValue)
                editor.putString(VoiceSettings.PREF_RATE, rateValue.toString())
            }

            if (prefs.getString(VoiceSettings.PREF_VARIANT, null) == null) {
                // The old eyes-free gender setting when there is one, and
                // otherwise EspeakZ's default variant, Klatt 6.
                val variant = when (prefs.getString(VoiceSettings.PREF_DEFAULT_GENDER, null)) {
                    null -> VoiceVariant.DEFAULT
                    "2" -> VoiceVariant.FEMALE
                    else -> VoiceVariant.MALE
                }
                editor.putString(VoiceSettings.PREF_VARIANT, variant)
            }

            editor.commit()
        }

        /**
         * True once the hosting activity can no longer accept preference
         * updates. The load outlives a screen the user backed straight out of,
         * and adding preferences to a dead activity's group would leak it.
         */
        fun isGone(context: Context): Boolean {
            if (context !is Activity) return false
            return context.isFinishing || context.isDestroyed
        }

        fun addPreferences(fragment: PreferenceFragment, state: EngineState, isWatch: Boolean) {
            val context = fragment.activity
            val group = fragment.preferenceScreen

            val voiceLanguages = createVoiceLanguagesPreference(fragment, state)
            group.addPreference(voiceLanguages)
            group.addPreference(createEspeakZLanguagePreference(context, state))
            group.addPreference(createForceLanguagePreference(context))
            // The supported-languages multi-select and the file-picker-driven
            // voice import don't fit on a watch screen and have no meaningful
            // input affordance there, so omit them on Wear.
            if (!isWatch) {
                group.addPreference(createSupportedLanguagesPreference(context, state.voices, state.labels))
                group.addPreference(createImportVoicePreference(fragment))
            }
            group.addPreference(createVoiceVariantPreference(context, state.variant))
            group.addPreference(createSpeakPunctuationPreference(context, state.punctuationLevel, state.punctuationCharacters))
            group.addPreference(createLongNumbersPreference(context))
            group.addPreference(createUnicodeNormalizationPreference(context))
            group.addPreference(DualLanguagePreferences.entry(context))

            if (isWatch) {
                // One parameter per dialog on Wear. The rotating crown only
                // delivers scroll events to the focused view and the watch has
                // no way to move focus between sliders, so a combined dialog
                // would leave every slider but one unreachable by the crown.
                for (parameter in listOf(state.rate, state.pitch, state.pitchRange, state.volume)) {
                    val pref = createSeekBarPreference(context, parameter)
                    pref.onTest = { testEspeakZ(context, state.voices) }
                    group.addPreference(pref)
                }
            } else {
                val pref = newSeekBarPreference(context, PREF_VOICE_PARAMETERS, context.getString(R.string.espeak_voice_settings))
                pref.addParameter(state.rate)
                pref.addParameter(state.pitch)
                pref.addParameter(state.pitchRange)
                pref.addParameter(state.volume)
                pref.summary = pref.buildSummary()
                pref.onTest = { testEspeakZ(context, state.voices) }
                group.addPreference(pref)
            }
            // Wear OS has no screen to leave an app out of battery saving.
            if (!isWatch) createKeepRunningPreference(context)?.let { group.addPreference(it) }
            group.addPreference(createGitHubPreference(context))
            group.addPreference(createAboutPreference(context))

            val intent = context.intent
            if (intent.getBooleanExtra(EXTRA_VOICE_LANGUAGES, false)) {
                // Only once: not again after a rotation rebuilds the screen.
                intent.removeExtra(EXTRA_VOICE_LANGUAGES)
                (voiceLanguages as? VoiceLanguagesPreference)?.open()
            }
        }

        fun createVoiceLanguagesPreference(fragment: PreferenceFragment, state: EngineState): Preference {
            val context = fragment.activity
            val pref = VoiceLanguagesPreference(context)
            pref.key = PREF_VOICE_LANGUAGES
            pref.setTitle(R.string.voice_languages_title)
            pref.setDialogTitle(R.string.voice_languages_title)
            pref.languages = state.languages.filter { it.dictionary != EspeakData.ENGLISH }
            pref.installed = state.installed
            pref.summary = installedSummary(context, state.installed)
            pref.onLanguagesChosen = { chosen -> applyVoiceLanguages(fragment, pref, chosen) }
            return pref
        }

        fun installedSummary(context: Context, installed: Set<String>): String {
            val names = installed.map { EspeakData.displayName(context, it) }.sortedWith(String.CASE_INSENSITIVE_ORDER)
            return context.getString(R.string.voice_languages_summary, names.joinToString(", "))
        }

        /** Installs and removes dictionaries, then updates the screen and says so. */
        fun applyVoiceLanguages(fragment: PreferenceFragment, pref: VoiceLanguagesPreference, chosen: Set<String>) {
            val context = fragment.activity
            val storage = EspeakData.storage(context)
            pref.setSummary(R.string.voice_languages_installing)
            Thread({
                val ok = EspeakData.setInstalledDictionaries(storage, chosen)
                val state = loadEngineState(context, storage)
                mainHandler.post {
                    if (isGone(context) || !fragment.isAdded) return@post
                    pref.installed = state.installed
                    pref.summary = installedSummary(context, state.installed)
                    refreshSupportedLanguages(fragment, state)
                    Toast.makeText(
                        context,
                        if (ok) R.string.voice_languages_updated else R.string.voice_languages_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }, "espeakz-voice-languages").start()
        }

        fun refreshSupportedLanguages(fragment: PreferenceFragment, state: EngineState) {
            (fragment.findPreference(PREF_VOICE_LANGUAGES) as? VoiceLanguagesPreference)?.let {
                it.installed = state.installed
                it.summary = installedSummary(fragment.activity, state.installed)
            }
            val pref = fragment.findPreference(LanguageSettings.PREF_SUPPORTED_LANGUAGES) as? SupportedLanguagesPreference
                ?: return
            bindSupportedLanguages(fragment.activity, pref, state.voices, state.labels)
        }

        /** Picks a dictionary file with Android's own file picker. */
        fun createImportVoicePreference(fragment: PreferenceFragment): Preference {
            val context = fragment.activity
            val pref = Preference(context)
            pref.key = PREF_IMPORT_DICTIONARY
            pref.isPersistent = false
            pref.layoutResource = R.layout.information_view
            pref.setTitle(R.string.import_voice_title)
            pref.setSummary(R.string.import_voice_summary)
            pref.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                try {
                    fragment.startActivityForResult(intent, REQUEST_IMPORT_DICTIONARY)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.import_voice_no_picker, Toast.LENGTH_LONG).show()
                }
                true
            }
            return pref
        }

        fun importDictionary(fragment: PreferenceFragment, uri: Uri) {
            val context = fragment.activity
            val storage = EspeakData.storage(context)
            val resolver = context.contentResolver
            Thread({
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment.orEmpty()
                val ok = try {
                    resolver.openInputStream(uri)?.use { EspeakData.importDictionary(storage, name, it) } ?: false
                } catch (e: Exception) {
                    false
                }
                val state = loadEngineState(context, storage)
                mainHandler.post {
                    if (isGone(context) || !fragment.isAdded) return@post
                    refreshSupportedLanguages(fragment, state)
                    Toast.makeText(
                        context,
                        context.getString(if (ok) R.string.import_voice_done else R.string.import_voice_failed, name),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }, "espeakz-import-dictionary").start()
        }

        fun createVoiceVariantPreference(context: Context, variant: VoiceVariant): Preference {
            val title = context.getString(R.string.espeak_variant)
            val pref = VoiceVariantPreference(context)
            pref.title = title
            pref.dialogTitle = title
            pref.onPreferenceChangeListener = onPreferenceChanged
            pref.isPersistent = true
            pref.setVoiceVariant(variant)
            return pref
        }

        fun createSpeakPunctuationPreference(context: Context, level: Int, characters: String?): Preference {
            val title = context.getString(R.string.espeak_speak_punctuation)
            val pref = SpeakPunctuationPreference(context)
            pref.title = title
            pref.dialogTitle = title
            pref.onPreferenceChangeListener = onPreferenceChanged
            pref.isPersistent = true
            pref.setPunctuation(level, characters)
            return pref
        }

        /** EspeakZ's own language: the device's unless the user picks a voice. */
        fun createEspeakZLanguagePreference(context: Context, state: EngineState): Preference {
            val voices = state.voices.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { state.labels[it] ?: it.name })
            val device = EspeakZLanguage.deviceVoice(state.voices)
            val deviceLabel = context.getString(
                R.string.espeakz_language_device,
                device?.let { state.labels[it] ?: it.name }.orEmpty(),
            )
            val pref = ChoiceListPreference(context)
            pref.key = EspeakZLanguage.PREF_VOICE
            pref.setTitle(R.string.espeakz_language_title)
            pref.setDialogTitle(R.string.espeakz_language_title)
            pref.entries = (listOf(deviceLabel) + voices.map { state.labels[it] ?: it.name }).toTypedArray()
            pref.entryValues = (listOf(EspeakZLanguage.DEVICE) + voices.map { it.name }).toTypedArray()
            pref.setDefaultValue(EspeakZLanguage.DEVICE)
            pref.isPersistent = true
            pref.entryAsSummary = true
            return pref
        }

        fun createForceLanguagePreference(context: Context): Preference {
            val pref = CheckBoxPreference(context)
            pref.key = EspeakZLanguage.PREF_FORCE
            pref.setTitle(R.string.espeakz_language_force_title)
            pref.setSummary(R.string.espeakz_language_force_summary)
            pref.setDefaultValue(false)
            pref.isPersistent = true
            return pref
        }

        /** How numbers longer than six digits are read: as they are, or in groups. */
        fun createLongNumbersPreference(context: Context): Preference {
            val pref = ChoiceListPreference(context)
            pref.key = VoiceSettings.PREF_LONG_NUMBERS
            pref.setTitle(R.string.long_numbers_title)
            pref.setDialogTitle(R.string.long_numbers_title)
            pref.setEntries(R.array.long_numbers_entries)
            pref.entryValues = arrayOf("0", "1", "2", "3")
            pref.setDefaultValue("0")
            pref.isPersistent = true
            pref.entryAsSummary = true
            return pref
        }

        fun createUnicodeNormalizationPreference(context: Context): Preference {
            val pref = CheckBoxPreference(context)
            pref.setTitle(R.string.setting_unicode_normalization)
            pref.setSummary(R.string.setting_unicode_normalization_summary)
            pref.key = VoiceSettings.PREF_UNICODE_NORMALIZATION
            pref.setDefaultValue(true)
            pref.isPersistent = true
            return pref
        }

        /**
         * Describes one voice parameter to [SeekBarPreference]: where its
         * value lives, what it is called and how it reads.
         */
        fun voiceParameter(
            context: Context,
            prefs: SharedPreferences,
            parameter: SpeechSynthesis.Parameter,
            key: String,
            titleRes: Int,
        ): SeekBarPreference.Parameter {
            val formatter = when (parameter.unitType) {
                SpeechSynthesis.UnitType.Percentage -> context.getString(R.string.formatter_percentage)
                SpeechSynthesis.UnitType.WordsPerMinute -> context.getString(R.string.formatter_wpm)
                else -> throw IllegalStateException("Unsupported unit type for the parameter.")
            }

            val defaultValue = parameter.defaultValue
            val current = prefs.getString(key, null)?.toIntOrNull() ?: defaultValue

            val voiceParam = SeekBarPreference.Parameter(
                key,
                context.getString(titleRes),
                parameter.minValue,
                parameter.maxValue,
                defaultValue,
                current,
                formatter,
            )

            if (VoiceSettings.PREF_RATE == key) {
                voiceParam.enableRateBoost(prefs.getBoolean(VoiceSettings.PREF_RATE_BOOST, false))
            }

            return voiceParam
        }

        fun newSeekBarPreference(context: Context, key: String, title: String): SeekBarPreference {
            val pref = SeekBarPreference(context)
            pref.title = title
            pref.dialogTitle = title
            // Without a key, Preference.dispatchSaveInstanceState() skips the
            // preference and an open dialog does not survive a rotation.
            pref.key = key
            pref.onPreferenceChangeListener = onPreferenceChanged
            pref.isPersistent = true
            return pref
        }

        /** A single voice parameter, edited in a dialog of its own. */
        /**
         * Speaks a sample through EspeakZ in its own language, with the voice
         * settings as they are now, then lets the engine go.
         */
        fun testEspeakZ(context: Context, voices: List<Voice>) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(EspeakData.storage(context))
            val locale = EspeakZLanguage.voice(prefs, voices)?.locale ?: Locale.getDefault()
            val text = SpeechSynthesis.getSampleText(context, locale)
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext, { status ->
                val client = tts ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) {
                    client.shutdown()
                    return@TextToSpeech
                }
                client.language = locale
                client.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { client.shutdown() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { client.shutdown() }
                    }
                })
                client.speak(text, TextToSpeech.QUEUE_FLUSH, null, "espeakz-test")
            }, BuildConfig.APPLICATION_ID)
            mainHandler.postDelayed({ tts?.shutdown() }, 20000L)
        }

        fun createSeekBarPreference(context: Context, parameter: SeekBarPreference.Parameter): SeekBarPreference {
            val pref = newSeekBarPreference(context, parameter.key, parameter.title)
            pref.addParameter(parameter)
            pref.summary = pref.buildSummary()
            return pref
        }

        fun createSupportedLanguagesPreference(context: Context, voices: List<Voice>, labels: Map<Voice, String>): Preference {
            val pref = SupportedLanguagesPreference(context)
            pref.setTitle(R.string.espeak_supported_languages)
            pref.setDialogTitle(R.string.espeak_supported_languages)
            pref.onPreferenceChangeListener = onPreferenceChanged
            bindSupportedLanguages(context, pref, voices, labels)
            return pref
        }

        fun bindSupportedLanguages(
            context: Context,
            pref: SupportedLanguagesPreference,
            voices: List<Voice>,
            labels: Map<Voice, String>,
        ) {
            val sortedVoices = voices.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { labels[it] ?: it.name })
            pref.entries = sortedVoices.map { labels[it] ?: it.name }.toTypedArray()
            pref.entryValues = sortedVoices.map { it.toString() }.toTypedArray<CharSequence>()

            val prefs = PreferenceManager.getDefaultSharedPreferences(EspeakData.storage(context))
            val selected = LanguageSettings.getSelectedLanguages(prefs) ?: sortedVoices.map { it.toString() }.toSet()
            pref.values = selected
            pref.summary = getSupportedLanguagesSummary(context, selected, sortedVoices.size)
        }

        fun getSupportedLanguagesSummary(context: Context, selected: Set<String>?, total: Int): String {
            val enabled = if (selected.isNullOrEmpty()) total else selected.size
            if (enabled >= total) {
                return context.getString(R.string.espeak_supported_languages_all)
            }
            return context.getString(R.string.espeak_supported_languages_summary, enabled, total)
        }

        /**
         * How a voice reads in the supported languages list: its name, then
         * its eSpeak language code, such as "English (Great Britain), en-gb".
         */
        fun voiceLabel(storage: Context, voice: Voice): String {
            val file = EspeakData.languageFile(storage, voice.identifier) ?: return voice.name
            return file.name + ", " + file.language
        }

        /**
         * Keep EspeakZ running: asks Android to leave EspeakZ out of battery
         * saving, so that a screen reader's speech is not cut off when the
         * phone has been idle. Once it is left out, the row opens Android's
         * list of apps, where it can be undone and where the second
         * language's engine can be left out too. Before Android 6 there is
         * no battery saving of this kind, so there is no row.
         */
        fun createKeepRunningPreference(context: Context): Preference? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
            val pref = Preference(context)
            pref.key = PREF_KEEP_RUNNING
            pref.isPersistent = false
            pref.setTitle(R.string.keep_running_title)
            updateKeepRunningSummary(pref)
            pref.setOnPreferenceClickListener {
                val exempt = isIgnoringBatteryOptimizations(context)
                val request = if (exempt) {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                } else {
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
                }
                val opened = listOf(request, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)).any { intent ->
                    try {
                        context.startActivity(intent)
                        true
                    } catch (e: ActivityNotFoundException) {
                        false
                    }
                }
                if (!opened) Toast.makeText(context, R.string.keep_running_unavailable, Toast.LENGTH_LONG).show()
                true
            }
            return pref
        }

        fun updateKeepRunningSummary(pref: Preference) {
            pref.setSummary(if (isIgnoringBatteryOptimizations(pref.context)) R.string.keep_running_on else R.string.keep_running_off)
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return power.isIgnoringBatteryOptimizations(context.packageName)
        }

        const val GITHUB_URL = "https://github.com/iqdz/EspeakZ"

        /** Opens EspeakZ's page on GitHub in the browser; EspeakZ itself never goes online. */
        fun createGitHubPreference(context: Context): Preference {
            val pref = Preference(context)
            pref.key = "espeakz_github"
            pref.isPersistent = false
            pref.setTitle(R.string.github_title)
            pref.setSummary(R.string.github_summary)
            pref.setOnPreferenceClickListener {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.github_no_browser, Toast.LENGTH_LONG).show()
                }
                true
            }
            return pref
        }

        /** Opens the About EspeakZ screen. Its summary carries the author and version. */
        fun createAboutPreference(context: Context): Preference {
            val pref = Preference(context)
            pref.key = "espeakz_about"
            pref.isPersistent = false
            pref.setTitle(R.string.about_title)
            pref.summary = context.getString(R.string.about_summary, BuildConfig.VERSION_NAME)
            pref.intent = Intent(context, AboutActivity::class.java)
            return pref
        }

        /**
         * Since the "%s" summary is currently broken, this sets the preference
         * change listener for all ListPreference views to fill in the summary
         * with the current entry value.
         */
        val onPreferenceChanged = Preference.OnPreferenceChangeListener { preference, newValue ->
            if (newValue is CharSequence) {
                var summary = ""
                if (preference is ListPreference) {
                    val index = preference.findIndexOfValue(newValue.toString())
                    val entries = preference.entries
                    if (index >= 0 && index < entries.size) {
                        summary = entries[index].toString()
                    }
                } else {
                    summary = newValue.toString()
                }
                preference.summary = summary
            } else if (newValue is Set<*> && preference is MultiSelectListPreference) {
                val values = newValue.filterIsInstance<String>().toSet()
                preference.summary = getSupportedLanguagesSummary(preference.context, values, preference.entries.size)
            }
            true
        }
    }
}
