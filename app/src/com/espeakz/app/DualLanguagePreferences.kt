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

package com.espeakz.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.preference.PreferenceManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.espeakz.app.preference.ChoiceListPreference
import com.espeakz.app.preference.LanguagePickerPreference
import com.espeakz.app.preference.SeekBarPreference
import java.io.File
import java.util.Locale

/**
 * The Dual languages page and its entry in the settings. On the page: the
 * switch; one list of every language that EspeakZ or another installed
 * engine speaks, with the two in use checked, one Latin and one in another
 * alphabet; the voices of every engine for the language chosen there, a
 * test, and that voice's own speed, pitch and volume; and which voice reads
 * numbers and emojis. Every language keeps the voice set for it, so that
 * many can be set up and two of them used. What it saves is read by
 * [DualLanguage.read].
 */
internal object DualLanguagePreferences {
    private const val PREF_ENTRY = "espeakz_dual_languages"
    private const val PREF_LANGUAGE = "espeakz_dual_language_choice"
    private const val PREF_VOICE_CHOICE = "espeakz_dual_voice_choice"
    private const val PREF_TEST = "espeakz_dual_test"
    private const val PREF_VOICE_SETTINGS = "espeakz_dual_voice_settings"
    private const val PREF_BACK = "espeakz_dual_back"
    private const val SCAN_TIMEOUT_MS = 10000L
    private const val TEST_TIMEOUT_MS = 20000L

    /** Where the voice settings rows sit on the page. */
    private const val ORDER_SETTINGS = 4

    /** Separates the engine from the voice name in the voice list's values. */
    private const val SEPARATOR = '\u001f'

    /** One voice of one engine, as the voice list shows it. */
    private class VoiceChoice(val engine: String, val name: String, val locale: Locale, val label: String, val online: Boolean) {
        val value: String
            get() = engine + SEPARATOR + name

        val language: String
            get() = locale.language.lowercase(Locale.ROOT)
    }

    private class Engine(val packageName: String, val label: String)

    private val mainHandler = Handler(Looper.getMainLooper())

    /** The other engines' voices from the last look in this process, shown at once while the next one runs. */
    @Volatile private var scanned: List<VoiceChoice>? = null

    /** The settings row that opens the Dual languages page and sums it up. */
    class Entry(context: Context) : Preference(context) {
        init {
            key = PREF_ENTRY
            isPersistent = false
            setTitle(R.string.dual_title)
            intent = Intent(context, DualLanguageActivity::class.java)
        }

        /** Updates the summary from what is saved; called whenever the settings screen shows. */
        fun refresh() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(EspeakData.storage(context))
            summary = summary(context, prefs)
        }
    }

    fun entry(context: Context): Entry = Entry(context).also { it.refresh() }

    private fun summary(context: Context, prefs: SharedPreferences): String {
        if (!prefs.getBoolean(DualLanguage.PREF_ENABLED, false)) return context.getString(R.string.dual_off)
        val dual = DualLanguage.read(prefs) ?: return context.getString(R.string.dual_needs_voice)
        val engines = otherEngines(context)
        fun describe(voice: DualLanguage.Voice?): String = voice?.let {
            context.getString(R.string.dual_voice_by, languageName(it.language), engineLabel(context, engines, it.engine))
        } ?: context.getString(R.string.dual_language_not_chosen)
        return context.getString(R.string.dual_summary, describe(dual.latin), describe(dual.nonLatin))
    }

    private fun engineLabel(context: Context, engines: List<Engine>, packageName: String): String {
        if (packageName == BuildConfig.APPLICATION_ID) return context.getString(R.string.app_name)
        return engines.firstOrNull { it.packageName == packageName }?.label ?: packageName
    }

    /** Builds the page into [fragment]'s screen. */
    fun build(fragment: PreferenceFragment, state: TtsSettingsActivity.EngineState, isWatch: Boolean) {
        val context = fragment.activity
        val prefs = fragment.preferenceManager.sharedPreferences
        val screen = fragment.preferenceScreen
        val appName = context.getString(R.string.app_name)
        val engines = otherEngines(context)
        val espeakVoices = state.voices.map {
            VoiceChoice(BuildConfig.APPLICATION_ID, it.name, it.locale, (state.labels[it] ?: it.name) + ", " + appName, false)
        }
        var allVoices: List<VoiceChoice> = espeakVoices + scanned.orEmpty()
        var scanning = true

        DualLanguage.migrate(prefs)

        fun inUse(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotEmpty() }

        fun savedChoice(code: String): String? {
            val keys = DualLanguage.keysFor(code)
            val name = prefs.getString(keys.voice, null)?.takeIf { it.isNotEmpty() } ?: return null
            val engine = prefs.getString(keys.engine, null)?.takeIf { it.isNotEmpty() } ?: BuildConfig.APPLICATION_ID
            return engine + SEPARATOR + name
        }

        fun save(code: String, choice: VoiceChoice) {
            val keys = DualLanguage.keysFor(code)
            prefs.edit()
                .putString(keys.engine, choice.engine)
                .putString(keys.voice, choice.name)
                .putString(keys.locale, choice.locale.toLanguageTag())
                .apply()
        }

        /**
         * A voice for a language that has none yet: EspeakZ's own where it
         * has one, as it is always there and answers at once, otherwise the
         * first voice that needs no internet.
         */
        fun pickVoice(code: String) {
            if (savedChoice(code) != null) return
            val espeak = EspeakZLanguage.bestVoice(state.voices, Locale(code))
                ?.let { best -> espeakVoices.firstOrNull { it.name == best.name } }
            val voices = allVoices.filter { it.language == code }
            (espeak ?: voices.firstOrNull { !it.online } ?: voices.firstOrNull())?.let { save(code, it) }
        }

        /** Puts [code] in use for its alphabet and shows its voice. */
        fun use(code: String) {
            val key = if (DualLanguage.isLatin(Locale(code))) DualLanguage.PREF_LATIN else DualLanguage.PREF_NON_LATIN
            prefs.edit().putString(key, code).putString(DualLanguage.PREF_SHOWN, code).apply()
            pickVoice(code)
        }

        // Until the user picks one, EspeakZ's own language is in use.
        if (inUse(DualLanguage.PREF_LATIN) == null && inUse(DualLanguage.PREF_NON_LATIN) == null) {
            EspeakZLanguage.voice(prefs, state.voices)?.let { use(it.locale.language.lowercase(Locale.ROOT)) }
        }

        fun shown(): String? = inUse(DualLanguage.PREF_SHOWN) ?: inUse(DualLanguage.PREF_LATIN) ?: inUse(DualLanguage.PREF_NON_LATIN)

        val enabled = CheckBoxPreference(context)
        enabled.key = DualLanguage.PREF_ENABLED
        enabled.order = 0
        enabled.setTitle(R.string.dual_enabled_title)
        enabled.setDefaultValue(false)
        screen.addPreference(enabled)

        fun dependent(pref: Preference, order: Int) {
            pref.order = order
            screen.addPreference(pref)
            pref.dependency = DualLanguage.PREF_ENABLED
        }

        val language = LanguagePickerPreference(context)
        language.key = PREF_LANGUAGE
        language.setTitle(R.string.dual_language)
        language.setDialogTitle(R.string.dual_language)
        dependent(language, 1)

        val voice = ChoiceListPreference(context)
        voice.key = PREF_VOICE_CHOICE
        voice.isPersistent = false
        voice.setTitle(R.string.dual_voice_title)
        voice.setDialogTitle(R.string.dual_voice_title)
        dependent(voice, 2)

        val test = Preference(context)
        test.key = PREF_TEST
        test.isPersistent = false
        test.setTitle(R.string.dual_test)
        dependent(test, 3)

        addVoiceChoice(context, screen, DualLanguage.PREF_NUMBERS, R.string.dual_numbers_title, 20)
        addVoiceChoice(context, screen, DualLanguage.PREF_EMOJIS, R.string.dual_emojis_title, 21)

        val back = Preference(context)
        back.key = PREF_BACK
        back.order = 22
        back.isPersistent = false
        back.setTitle(R.string.dual_back)
        back.setOnPreferenceClickListener {
            context.finish()
            true
        }
        screen.addPreference(back)

        fun testShown() {
            val code = shown() ?: return
            allVoices.firstOrNull { it.value == savedChoice(code) }
                ?.let { testVoice(context, it, prefs, DualLanguage.keysFor(code), state.rate.current) }
        }

        var settingsRows: List<Preference> = emptyList()

        /** The rate, pitch and volume rows for [code]'s voice: one dialog on a phone, one each on a watch. */
        fun showVoiceSettings(code: String?) {
            settingsRows.forEach { screen.removePreference(it) }
            settingsRows = emptyList()
            if (code == null) return
            val keys = DualLanguage.keysFor(code)
            fun saved(key: String, default: Int) = prefs.getString(key, null)?.toIntOrNull() ?: default
            val mainRate = state.rate.current
            val rate = SeekBarPreference.Parameter(
                keys.rate, context.getString(R.string.setting_default_rate),
                state.rate.min, state.rate.max, mainRate, saved(keys.rate, mainRate),
                context.getString(R.string.formatter_wpm),
            )
            val pitch = SeekBarPreference.Parameter(
                keys.pitch, context.getString(R.string.setting_default_pitch),
                0, 100, DualLanguage.DEFAULT_PITCH, saved(keys.pitch, DualLanguage.DEFAULT_PITCH),
                context.getString(R.string.formatter_percentage),
            )
            val volume = SeekBarPreference.Parameter(
                keys.volume, context.getString(R.string.espeak_volume),
                0, 200, DualLanguage.DEFAULT_VOLUME, saved(keys.volume, DualLanguage.DEFAULT_VOLUME),
                context.getString(R.string.formatter_percentage),
            )
            val rows = if (isWatch) {
                // One dialog per slider, for the crown; see TtsSettingsActivity.
                listOf(rate, pitch, volume).map { parameter ->
                    TtsSettingsActivity.newSeekBarPreference(context, parameter.key, parameter.title).also { it.addParameter(parameter) }
                }
            } else {
                val pref = TtsSettingsActivity.newSeekBarPreference(context, PREF_VOICE_SETTINGS, context.getString(R.string.dual_voice_settings))
                pref.addParameter(rate)
                pref.addParameter(pitch)
                pref.addParameter(volume)
                listOf(pref)
            }
            rows.forEachIndexed { i, pref ->
                pref.summary = pref.buildSummary()
                pref.onTest = { testShown() }
                dependent(pref, ORDER_SETTINGS + i)
            }
            settingsRows = rows
        }

        fun refreshAll() {
            val latin = inUse(DualLanguage.PREF_LATIN)
            val nonLatin = inUse(DualLanguage.PREF_NON_LATIN)
            val code = shown()

            val codes = allVoices.map { it.language }.filter { it.isNotEmpty() }.toSortedSet()
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { languageName(it) })
            language.codes = codes
            // A language with a voice set says which engine it comes from.
            language.labels = codes.map { c ->
                val engine = savedChoice(c)?.substringBefore(SEPARATOR)
                if (engine == null) languageName(c) else context.getString(R.string.dual_language_entry, languageName(c), engineLabel(context, engines, engine))
            }
            language.checked = setOfNotNull(latin, nonLatin)
            language.current = code
            val pair = listOfNotNull(latin, nonLatin).joinToString(context.getString(R.string.dual_and)) { languageName(it) }
            language.summary = if (code == null) {
                context.getString(if (scanning) R.string.dual_language_loading else R.string.dual_language_choose)
            } else {
                context.getString(R.string.dual_language_summary, languageName(code), pair)
            }

            val shownVoices = allVoices.filter { it.language == code }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            voice.entries = shownVoices.map { it.label }.toTypedArray()
            voice.entryValues = shownVoices.map { it.value }.toTypedArray()
            voice.isEnabled = shownVoices.isNotEmpty()
            voice.value = code?.let { savedChoice(it) }?.takeIf { saved -> shownVoices.any { it.value == saved } }
            val chosen = shownVoices.firstOrNull { it.value == voice.value }
            voice.summary = chosen?.label ?: context.getString(
                when {
                    code == null -> R.string.dual_voice_language_first
                    scanning && shownVoices.isEmpty() -> R.string.dual_voice_loading
                    shownVoices.isEmpty() -> R.string.dual_voice_none
                    else -> R.string.dual_voice_choose
                },
            )
            test.isEnabled = chosen != null
            test.summary = chosen?.label
        }

        language.onPick = { code ->
            use(code)
            showVoiceSettings(code)
            refreshAll()
        }
        voice.setOnPreferenceChangeListener { _, newValue ->
            val code = shown()
            val choice = allVoices.firstOrNull { it.value == newValue }
            if (code != null && choice != null) save(code, choice)
            refreshAll()
            false
        }
        test.setOnPreferenceClickListener {
            testShown()
            true
        }

        showVoiceSettings(shown())
        refreshAll()
        scanEngines(context) { found ->
            if (isGone(context) || !fragment.isAdded) return@scanEngines
            scanned = found
            scanning = false
            allVoices = espeakVoices + found
            refreshAll()
        }
    }

    /**
     * Speaks a sample in [choice] with the saved speed, pitch and volume. The
     * engine makes the audio and EspeakZ plays it, on the accessibility
     * stream, as it does in dual languages: some phones, such as OnePlus,
     * mute an engine that plays from the background on its own.
     */
    private fun testVoice(context: Context, choice: VoiceChoice, prefs: SharedPreferences, keys: DualLanguage.Keys, mainRate: Int) {
        val wpm = prefs.getString(keys.rate, null)?.toIntOrNull() ?: mainRate
        val pitch = prefs.getString(keys.pitch, null)?.toIntOrNull() ?: DualLanguage.DEFAULT_PITCH
        val volume = prefs.getString(keys.volume, null)?.toIntOrNull() ?: DualLanguage.DEFAULT_VOLUME
        val text = SpeechSynthesis.getSampleText(context, choice.locale)
        val file = File(context.cacheDir, "test-voice.wav")
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext, { status ->
            val client = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                client.shutdown()
                return@TextToSpeech
            }
            Thread({
                val named = try {
                    client.voices.orEmpty().firstOrNull { it.name == choice.name }
                } catch (e: Exception) {
                    null
                }
                if (named != null) client.voice = named else client.language = choice.locale
                client.setSpeechRate(wpm / TtsSettingsActivity.DEFAULT_RATE_WPM.toFloat())
                client.setPitch(pitch / DualLanguage.DEFAULT_PITCH.toFloat())
                client.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            client.shutdown()
                            play(file, volume / 100f)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { client.shutdown() }
                    }
                })
                client.synthesizeToFile(text, Bundle(), file, "espeakz-test")
            }, "espeakz-test-voice").start()
        }, choice.engine)
        mainHandler.postDelayed({ tts?.shutdown() }, TEST_TIMEOUT_MS)
    }

    /** Plays a test sample once, then deletes it. A volume above 100 percent plays at full volume. */
    private fun play(file: File, volume: Float) {
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setDataSource(file.path)
            player.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
            player.setOnCompletionListener {
                it.release()
                file.delete()
            }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            player.release()
            file.delete()
        }
    }

    /** A list choosing whether the Latin or the other alphabet's voice reads something, the Latin one by default. */
    private fun addVoiceChoice(context: Context, screen: PreferenceGroup, key: String, titleRes: Int, order: Int) {
        val pref = ChoiceListPreference(context)
        pref.key = key
        pref.order = order
        pref.setTitle(titleRes)
        pref.setDialogTitle(titleRes)
        pref.entries = arrayOf(context.getString(R.string.dual_latin_voice), context.getString(R.string.dual_non_latin_voice))
        pref.entryValues = arrayOf(DualLanguage.LATIN, DualLanguage.NON_LATIN)
        pref.setDefaultValue(DualLanguage.LATIN)
        pref.entryAsSummary = true
        screen.addPreference(pref)
        pref.dependency = DualLanguage.PREF_ENABLED
    }

    /** A language's name in the device's language, such as "Arabic". */
    private fun languageName(code: String): String =
        Locale(code).displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    /** The installed engines EspeakZ can hand text to, by name. */
    private fun otherEngines(context: Context): List<Engine> {
        val pm = context.packageManager
        return pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo }
            .filter { it.packageName !in DualLanguage.EXCLUDED_ENGINES }
            .distinctBy { it.packageName }
            .map { Engine(it.packageName, it.loadLabel(pm).toString()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /**
     * Connects to every other engine at once for its installed voices, then
     * lets them go. [done] runs on the main thread with what was found, once
     * every engine has answered or [SCAN_TIMEOUT_MS] has passed.
     */
    private fun scanEngines(context: Context, done: (List<VoiceChoice>) -> Unit) {
        val engines = otherEngines(context)
        val found = ArrayList<VoiceChoice>()
        var pending = engines.size
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            done(found.toList())
        }
        if (pending == 0) {
            mainHandler.post { finish() }
            return
        }
        val clients = ArrayList<TextToSpeech>()
        fun answered(voices: List<VoiceChoice>) {
            found.addAll(voices)
            pending--
            if (pending == 0) finish()
        }
        for (engine in engines) {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context.applicationContext, { status ->
                val client = tts
                if (status != TextToSpeech.SUCCESS || client == null) {
                    mainHandler.post {
                        answered(emptyList())
                        client?.shutdown()
                    }
                    return@TextToSpeech
                }
                // Asking the engine is a call into its process: off the main thread.
                Thread({
                    val voices = try {
                        client.voices.orEmpty()
                            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
                            // MultiTTS lists "NOT_SET" for a language it has no
                            // voice set up for; it speaks nothing.
                            .filter { it.name != "NOT_SET" }
                            .map { v ->
                                val locale = DualLanguage.normalize(v.locale)
                                // The voice's name says which voice it is; a variant in its
                                // locale, such as Acapela's "Leila", would only repeat it.
                                var label = Locale(locale.language, locale.country).displayName + ", " + v.name
                                if (v.isNetworkConnectionRequired) label = context.getString(R.string.dual_voice_online, label)
                                VoiceChoice(engine.packageName, v.name, locale, label + ", " + engine.label, v.isNetworkConnectionRequired)
                            }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    mainHandler.post {
                        answered(voices)
                        client.shutdown()
                    }
                }, "espeakz-engine-voices").start()
            }, engine.packageName)
            clients.add(tts)
        }
        mainHandler.postDelayed({
            if (!finished) {
                finish()
                clients.forEach { it.shutdown() }
            }
        }, SCAN_TIMEOUT_MS)
    }

    private fun isGone(context: Context): Boolean =
        context is Activity && (context.isFinishing || context.isDestroyed)
}
