/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2022 Beka Gozalishvili
 * Copyright (C) 2012-2015 Reece H. Dunn
 * Copyright (C) 2011 Google Inc.
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
 * This file implements the Android Text-to-Speech engine for eSpeak.
 */

package com.espeakz.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.preference.PreferenceManager
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Implements the eSpeak engine as a [TextToSpeechService].
 *
 * The engine starts on the first request that needs it, after the voice data
 * install that EspeakApp starts with the process. That request waits for the
 * install rather than failing, so a screen reader's first words after
 * installing EspeakZ, or after selecting it as the engine, are spoken.
 *
 * @author msclrhd@gmail.com (Reece H. Dunn)
 * @author alanv@google.com (Alan Viverette)
 */
@Suppress("DEPRECATION")
open class TtsService : TextToSpeechService() {

    private lateinit var storage: Context
    private var preferences: SharedPreferences? = null

    private val engineLock = Any()
    private var engine: SpeechSynthesis? = null

    private var callback: SynthesisCallback? = null

    /** Text handed to eSpeak for the current request. */
    private var synthText: String? = null
    /** Where [synthText] starts within the text the caller supplied. */
    private var synthTextOffset = 0
    /**
     * Offset map back to the caller's text when [synthText] is a normalized
     * copy of it, or null when they are the same string.
     */
    private var synthNormalization: UnicodeNormalization.Result? = null
    /** Number of code points in [synthText]. */
    private var synthTextCodePoints = 0
    /** Anchor for incremental code point to UTF-16 index conversion. */
    private var anchorCodePoint = 0
    private var anchorOffset = 0

    /** Set by [onStop]; a request read in runs checks it between them. */
    @Volatile private var stopRequested = false
    /** Audio frames handed to [callback] so far in the current request. */
    private var framesDelivered = 0
    /** True while a request is read in runs, so eSpeak finishing one run does not end it. */
    private var inRuns = false
    /** Where the run eSpeak is reading starts: code point in [synthText], and frames before it. */
    private var runCodePoint = 0
    private var runFrames = 0


    private var allVoices: List<Voice> = emptyList()
    private val availableVoices = HashMap<String, Voice>()

    /** The voice the next request speaks with. Guarded by [availableVoices]. */
    protected var matchingVoice: Voice? = null

    /** The [EspeakData.generation] the voice list was built from. */
    private var loadedGeneration = -1

    private val onPreferencesChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == LanguageSettings.PREF_SUPPORTED_LANGUAGES) {
            rebuildAvailableVoices()
        }
    }

    override fun onCreate() {
        storage = EspeakData.storage(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(storage).also {
            it.registerOnSharedPreferenceChangeListener(onPreferencesChanged)
        }
        creating = true
        super.onCreate()
        creating = false
        // Connect to the second language's engine now, so that it is ready
        // by the first text in its language.
        preferences?.let { DualLanguage.read(it) }?.let { connectEngines(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences?.unregisterOnSharedPreferenceChangeListener(onPreferencesChanged)
        otherEngines.values.forEach { it.shutdown() }
        otherEngines.clear()
    }

    /**
     * The engine, started on first use. Waits for the voice data install, so
     * it returns null only when the install failed. Languages added or
     * removed in the settings since the last request are picked up here.
     */
    private fun engine(): SpeechSynthesis? {
        synchronized(engineLock) {
            engine?.let {
                if (loadedGeneration != EspeakData.generation) loadVoices(it)
                return it
            }
            if (!EspeakData.ensureInstalled(storage)) {
                Log.e(TAG, "The voice data could not be installed")
                return null
            }
            return timed("start the speech service's engine") {
                val created = SpeechSynthesis(storage, synthCallback)
                engine = created
                matchingVoice = null
                loadVoices(created)
                created
            }
        }
    }

    private fun loadVoices(engine: SpeechSynthesis) {
        loadedGeneration = EspeakData.generation
        val voices = engine.getAvailableVoices()
        synchronized(availableVoices) {
            allVoices = voices
            if (DEBUG) Log.i(TAG, "loaded voices=${voices.size}")
        }
        rebuildAvailableVoices()
    }

    override fun onGetLanguage(): Array<String> {
        // This is used to specify the language requested from GetSampleText.
        val voice = synchronized(availableVoices) { matchingVoice } ?: espeakZVoice()
            ?: return arrayOf("eng", "USA", "")
        return arrayOf(voice.locale.isO3Language, voice.locale.isO3Country, voice.locale.variant)
    }

    /**
     * The voice of EspeakZ's own language setting. A voice the user chose is
     * used even when it is not among the languages reported to Android; the
     * device's language is looked for among those only.
     */
    private fun espeakZVoice(): Voice? {
        val prefs = preferences ?: return null
        val chosen = prefs.getString(EspeakZLanguage.PREF_VOICE, null)
        return synchronized(availableVoices) {
            allVoices.firstOrNull { !chosen.isNullOrEmpty() && it.name == chosen }
                ?: EspeakZLanguage.deviceVoice(availableVoices.values)
        }
    }

    private fun findVoice(language: String, country: String, variant: String): Pair<Voice?, Int> {
        if (engine() == null) {
            return Pair(null, TextToSpeech.LANG_MISSING_DATA)
        }

        val query = Locale(language, country, variant)

        var languageVoice: Voice? = null
        var countryVoice: Voice? = null

        synchronized(availableVoices) {
            for (voice in availableVoices.values) {
                when (voice.match(query)) {
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> return Pair(voice, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)
                    TextToSpeech.LANG_COUNTRY_AVAILABLE -> {
                        countryVoice = voice
                        languageVoice = voice
                    }
                    TextToSpeech.LANG_AVAILABLE -> languageVoice = voice
                }
            }
        }

        return when {
            languageVoice == null -> Pair(null, TextToSpeech.LANG_NOT_SUPPORTED)
            countryVoice == null -> Pair(languageVoice, TextToSpeech.LANG_AVAILABLE)
            else -> Pair(countryVoice, TextToSpeech.LANG_COUNTRY_AVAILABLE)
        }
    }

    private fun getDefaultVoiceFor(language: String, country: String, variant: String): Pair<Voice?, Int> {
        val match = findVoice(language, country, variant)
        return when (match.second) {
            TextToSpeech.LANG_AVAILABLE -> when (language) {
                // English with no country, or one EspeakZ has no voice for,
                // is American English.
                "en", "eng" -> Pair(findVoice(language, "USA", "").first ?: match.first, match.second)
                "fr", "fra" -> Pair(findVoice(language, "FRA", "").first, match.second)
                "pt", "por" -> Pair(findVoice(language, "PRT", "").first, match.second)
                else -> Pair(findVoice(language, "", "").first, match.second)
            }
            TextToSpeech.LANG_COUNTRY_AVAILABLE ->
                if ((language == "vi" || language == "vie") && (country == "VN" || country == "VNM")) {
                    Pair(findVoice(language, country, "hue").first, match.second)
                } else {
                    Pair(findVoice(language, country, "").first, match.second)
                }
            else -> match
        }
    }

    public override fun onIsLanguageAvailable(language: String?, country: String?, variant: String?): Int {
        val result = findVoice(language.orEmpty(), country.orEmpty(), variant.orEmpty()).second
        if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // When the requested language is filtered out or not installed
            // but other voices are available, report the language as available
            // so that screen readers (e.g. Jieshuo) don't skip this engine.
            synchronized(availableVoices) {
                if (availableVoices.isNotEmpty()) {
                    return TextToSpeech.LANG_AVAILABLE
                }
            }
        }
        return result
    }

    /** True while the framework's onCreate() runs; see onLoadLanguage(). */
    private var creating = false

    public override fun onLoadLanguage(language: String?, country: String?, variant: String?): Int {
        if (creating) {
            // TextToSpeechService.onCreate() loads the default language on the
            // main thread and ignores the answer. Starting the engine there
            // kept a just-booted watch from accepting its first connection
            // for most of a second, so it starts on a thread of its own; the
            // first request waits for it if it must.
            Thread({ engine() }, "espeakz-engine-start").start()
            return TextToSpeech.LANG_AVAILABLE
        }
        val match = getDefaultVoiceFor(language.orEmpty(), country.orEmpty(), variant.orEmpty())
        match.first?.let { voice ->
            synchronized(availableVoices) { matchingVoice = voice }
            return match.second
        }
        if (match.second == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fall back to a previously selected voice, or EspeakZ's own
            // language, so that screen readers requesting the system language
            // still get speech.
            val fallback = espeakZVoice()
            synchronized(availableVoices) {
                if (matchingVoice != null) {
                    return TextToSpeech.LANG_AVAILABLE
                }
                if (availableVoices.isNotEmpty()) {
                    matchingVoice = fallback ?: availableVoices.values.iterator().next()
                    return TextToSpeech.LANG_AVAILABLE
                }
            }
        }
        return match.second
    }

    public override fun onGetFeaturesForLanguage(lang: String?, country: String?, variant: String?): Set<String> {
        // eSpeak synthesizes on the device for every language it offers, and
        // never needs a network connection. Clients read this set -- directly,
        // or through the features of the voices built in onGetVoices() -- to
        // decide whether a language can be spoken offline; leaving it empty
        // makes eSpeak look like an engine that cannot answer the question.
        return hashSetOf(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)
    }

    override fun onGetDefaultVoiceNameFor(language: String?, country: String?, variant: String?): String? =
        getDefaultVoiceFor(language.orEmpty(), country.orEmpty(), variant.orEmpty()).first?.name

    override fun onGetVoices(): List<android.speech.tts.Voice> {
        engine()
        rebuildAvailableVoices()
        val voices = ArrayList<android.speech.tts.Voice>()
        synchronized(availableVoices) {
            for (voice in availableVoices.values) {
                val locale = Locale(voice.locale.isO3Language, voice.locale.isO3Country, voice.locale.variant)
                val features = onGetFeaturesForLanguage(locale.language, locale.country, locale.variant)
                voices.add(
                    android.speech.tts.Voice(
                        voice.name, voice.locale,
                        android.speech.tts.Voice.QUALITY_NORMAL,
                        android.speech.tts.Voice.LATENCY_VERY_LOW,
                        false, features,
                    )
                )
            }
        }
        return voices
    }

    override fun onIsValidVoiceName(name: String?): Int {
        engine()
        synchronized(availableVoices) {
            return if (name != null && availableVoices.containsKey(name)) TextToSpeech.SUCCESS else TextToSpeech.ERROR
        }
    }

    override fun onLoadVoice(name: String?): Int {
        engine()
        synchronized(availableVoices) {
            val voice = name?.let { availableVoices[it] } ?: return TextToSpeech.ERROR
            matchingVoice = voice
            return TextToSpeech.SUCCESS
        }
    }

    override fun onStop() {
        Log.i(TAG, "Received stop request.")
        if (DEBUG) stopAt = SystemClock.elapsedRealtime()
        stopRequested = true
        // No engine yet means nothing is speaking. Starting one here would only
        // delay the stop.
        synchronized(engineLock) { engine }?.stop()
        otherEngines.values.forEach { it.stop() }
    }

    private fun getRequestString(request: SynthesisRequest): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            request.charSequenceText?.toString()
        } else {
            request.text
        }

    protected open fun selectLanguageWithFallback(language: String?, country: String?, variant: String?): Int {
        when (onLoadLanguage(language, country, variant)) {
            TextToSpeech.LANG_MISSING_DATA -> return TextToSpeech.ERROR
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                // Fall back to a previously selected or available voice instead
                // of failing. This allows screen readers that request the
                // system language (e.g. Jieshuo) to still work when the user
                // has selected only other languages.
                val fallback = espeakZVoice()
                synchronized(availableVoices) {
                    // Prefer reusing the last matching voice if one is already selected.
                    if (matchingVoice != null) {
                        return TextToSpeech.SUCCESS
                    }
                    // Otherwise EspeakZ's own language, or any available voice.
                    if (availableVoices.isNotEmpty()) {
                        matchingVoice = fallback ?: availableVoices.values.iterator().next()
                        return TextToSpeech.SUCCESS
                    }
                }
                // No previous voice and no available voices to fall back to.
                return TextToSpeech.ERROR
            }
        }
        return TextToSpeech.SUCCESS
    }

    private fun selectVoice(request: SynthesisRequest): Int {
        if (preferences?.let { EspeakZLanguage.isForced(it) } == true) {
            // The user's choice in EspeakZ wins over the app's and Android's.
            espeakZVoice()?.let { forced ->
                synchronized(availableVoices) { matchingVoice = forced }
                return TextToSpeech.SUCCESS
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val name = request.voiceName
            if (!name.isNullOrEmpty() && onLoadVoice(name) == TextToSpeech.SUCCESS) {
                return TextToSpeech.SUCCESS
            }
            // Deliberately fall through when the named voice is unknown rather
            // than returning its error. The framework attaches a voice name to
            // every request on API 21+ -- including the system default one
            // when the client never picked a voice itself -- so a name that
            // has been filtered out of the user's language selection would
            // otherwise fail every request and make selectLanguageWithFallback()
            // below unreachable. The name is a hint; the language cascade decides.
        }
        return selectLanguageWithFallback(request.language, request.country, request.variant)
    }

    /**
     * Reports a synthesis failure to the caller.
     *
     * The framework only dispatches an error once done() follows, and treats a
     * request that returns without calling either as a successful empty
     * utterance, which hides failures from screen readers.
     */
    private fun reportError(callback: SynthesisCallback, errorCode: Int) {
        // error(int) has been available since API 21, which is minSdk here, so
        // the code always reaches the caller.
        callback.error(errorCode)
        callback.done()
    }

    /**
     * Converts a 0-based code point index within [synthText] into the UTF-16
     * index that [SynthesisCallback.rangeStart] expects.
     *
     * Walks forward from the previous result, since word events arrive in
     * text order; the occasional out-of-order event falls back to a full
     * rescan.
     */
    private fun codePointToOffset(codePointIndex: Int): Int {
        val text = synthText ?: return 0
        if (codePointIndex <= 0) {
            return 0
        }
        if (codePointIndex >= synthTextCodePoints) {
            return text.length
        }
        if (codePointIndex < anchorCodePoint) {
            anchorCodePoint = 0
            anchorOffset = 0
        }
        anchorOffset = text.offsetByCodePoints(anchorOffset, codePointIndex - anchorCodePoint)
        anchorCodePoint = codePointIndex
        return anchorOffset
    }

    @Synchronized
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        if (!DEBUG) {
            synthesizeRequest(request, callback)
            return
        }
        // Debug builds log how quickly each request is answered, under
        // EspeakZTiming, for measuring with a screen reader: see deliver().
        requestCount++
        requestStarted = SystemClock.elapsedRealtime()
        firstAudioLogged = false
        stopAt = 0L
        try {
            synthesizeRequest(request, callback)
        } finally {
            val now = SystemClock.elapsedRealtime()
            val rate = synchronized(engineLock) { engine }?.sampleRate?.takeIf { it > 0 } ?: 22050
            val stopped = if (stopAt > 0) ", stopped ${now - stopAt} ms after the stop" else ""
            Log.i(
                TIMING, "request $requestCount: done in ${now - requestStarted} ms, " +
                    "${framesDelivered * 1000L / rate} ms of audio, rate ${request.speechRate}$stopped",
            )
        }
    }

    private var requestCount = 0
    private var requestStarted = 0L
    private var firstAudioLogged = false
    @Volatile private var stopAt = 0L

    private fun synthesizeRequest(request: SynthesisRequest, callback: SynthesisCallback) {
        val engine = engine()
        if (engine == null) {
            reportError(callback, TextToSpeech.ERROR_NOT_INSTALLED_YET)
            return
        }

        if (selectVoice(request) == TextToSpeech.ERROR) {
            reportError(
                callback,
                if (EspeakData.hasBaseResources(storage)) TextToSpeech.ERROR_SERVICE else TextToSpeech.ERROR_NOT_INSTALLED_YET,
            )
            return
        }

        val voice = synchronized(availableVoices) { matchingVoice }
        if (voice == null) {
            reportError(callback, TextToSpeech.ERROR_SERVICE)
            return
        }

        var text = getRequestString(request)
        if (text == null) {
            reportError(callback, TextToSpeech.ERROR_INVALID_REQUEST)
            return
        }

        if (DEBUG) {
            Log.i(TAG, "Received synthesis request: {language=\"${voice.name}\"}")
            val params = request.params
            for (key in params.keySet()) {
                Log.v(TAG, "Synthesis request contained param {$key, ${params.get(key)}}")
            }
        }

        var textOffset = 0
        if (text.startsWith("<?xml")) {
            // eSpeak does not recognise/skip "<?...?>" preprocessing tags, so
            // need to remove these before passing to synthesize. A declaration
            // missing its "?>" is left alone rather than having its first
            // character eaten by a -1 index.
            val terminator = text.indexOf("?>")
            if (terminator >= 0) {
                val declarationEnd = terminator + 2
                // Track what was dropped from the front, so that word
                // boundaries can be reported against the text the caller
                // actually passed in. This mirrors what String.trim() strips
                // (anything <= ' ').
                textOffset = declarationEnd
                while (textOffset < text.length && text[textOffset] <= ' ') {
                    textOffset++
                }
                text = text.substring(declarationEnd).trim()
            }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(storage)
        val settings = VoiceSettings(prefs, engine)

        // Detect SSML before normalizing. Real markup is ASCII, which NFKC
        // leaves untouched, but normalization can turn lookalikes such as a
        // fullwidth "＜ｓｐｅａｋ" into "<speak", and plain text must not
        // switch into SSML parsing because of that.
        val isSsml = text.startsWith("<speak")

        var normalization: UnicodeNormalization.Result? = null
        if (settings.isUnicodeNormalizationEnabled) {
            normalization = UnicodeNormalization.normalize(text)
            if (normalization != null) {
                text = normalization.text
            }
        }
        val grouped = if (isSsml) null else NumberGrouping.group(text, settings.longNumberGroup)
        if (grouped != null) {
            text = grouped.text
            normalization = normalization?.let { grouped.through(it) } ?: grouped
        }

        synthText = text
        synthTextOffset = textOffset
        synthNormalization = normalization
        synthTextCodePoints = text.codePointCount(0, text.length)
        anchorCodePoint = 0
        anchorOffset = 0
        stopRequested = false
        framesDelivered = 0
        runCodePoint = 0
        runFrames = 0

        // SSML is read as it is: splitting it would split its markup.
        val dual = if (isSsml) null else DualLanguage.read(prefs)
        this.callback = callback
        callback.start(engine.sampleRate, engine.audioFormat, engine.channelCount)
        if (dual != null) {
            synthesizeDual(engine, settings, request, text, voice, dual)
            return
        }

        engine.setVoice(voice, settings.voiceVariant)
        applyParameters(engine, settings, request)
        engine.synthesize(text, isSsml)
    }

    private fun applyParameters(
        engine: SpeechSynthesis,
        settings: VoiceSettings,
        request: SynthesisRequest,
        rate: Int = settings.rate,
        pitch: Int = settings.pitch,
        volume: Int = settings.volume,
    ) {
        engine.Rate.setValue(((rate.toLong() * rateScale(request)) / 100).toInt())
        engine.Pitch.setValue(pitch, request.pitch)
        engine.PitchRange.setValue(settings.pitchRange)
        engine.Volume.setValue(volume)
        engine.Punctuation.setValue(settings.punctuationLevel)
        engine.setPunctuationCharacters(settings.punctuationCharacters)
    }

    private fun rateScale(request: SynthesisRequest): Int = request.speechRate.takeIf { it > 0 } ?: 100

    /**
     * One of the two dual languages voices as a request reads it: the other
     * engine that speaks it, or none for EspeakZ; the EspeakZ voice that
     * speaks it, or stands in when the other engine cannot; and its rate,
     * pitch and volume for eSpeak and for the other engine.
     */
    private class Speaker(
        val voice: DualLanguage.Voice,
        val other: SecondEngine?,
        val espeak: Voice,
        val wpm: Int,
        val espeakPitch: Int,
        val espeakVolume: Int,
        val rate: Float,
        val pitch: Float,
        val gain: Float,
    )

    /**
     * The EspeakZ voice for [voice]: the one chosen when it is an EspeakZ
     * voice, otherwise EspeakZ's best voice for its language, or null when
     * EspeakZ does not speak it.
     */
    private fun espeakVoiceFor(voice: DualLanguage.Voice): Voice? = synchronized(availableVoices) {
        if (voice.isEspeakZ && voice.voice.isNotEmpty()) {
            allVoices.firstOrNull { it.name == voice.voice }?.let { return it }
        }
        EspeakZLanguage.bestVoice(allVoices, DualLanguage.normalize(voice.locale ?: Locale(voice.language)))
    }

    private fun speaker(
        voice: DualLanguage.Voice,
        espeak: Voice,
        engine: SpeechSynthesis,
        settings: VoiceSettings,
        request: SynthesisRequest,
    ): Speaker {
        // Another engine's normal speed is taken as eSpeak's default rate, so
        // that the same words per minute sound about the same in both voices.
        val wpm = voice.rate ?: settings.rate
        val volume = voice.volume ?: DualLanguage.DEFAULT_VOLUME
        return Speaker(
            voice,
            if (voice.isEspeakZ) null else otherEngine(voice),
            espeak,
            wpm,
            voice.pitch ?: settings.pitch,
            (settings.volume * volume / 100).coerceIn(engine.Volume.minValue, engine.Volume.maxValue),
            rateScale(request) / 100f * wpm / engine.Rate.defaultValue.coerceAtLeast(1),
            (request.pitch.takeIf { it > 0 } ?: 100) / 100f * (voice.pitch ?: DualLanguage.DEFAULT_PITCH) / DualLanguage.DEFAULT_PITCH,
            volume / 100f,
        )
    }

    /**
     * Reads a request in dual languages: each run of the text by the Latin
     * or the other alphabet's voice, in order, into the one audio stream the request
     * started. Runs for other engines are queued with them from the start, a
     * few ahead, so that each engine works on its runs while the runs before
     * them are read, and each is ready when its turn comes. A run another
     * engine cannot read is read by eSpeak, so that no text is ever left out.
     */
    private fun synthesizeDual(
        engine: SpeechSynthesis,
        settings: VoiceSettings,
        request: SynthesisRequest,
        text: String,
        requested: Voice,
        dual: DualLanguage.Settings,
    ) {
        connectEngines(dual)
        // An alphabet with no language in use is read as the app asks.
        val asRequested = DualLanguage.Voice(BuildConfig.APPLICATION_ID, "", null, requested.locale.language, null, null, null)
        val latin = speaker(dual.latin ?: asRequested, dual.latin?.let { espeakVoiceFor(it) } ?: requested, engine, settings, request)
        val nonLatin = speaker(
            dual.nonLatin ?: asRequested, dual.nonLatin?.let { espeakVoiceFor(it) } ?: requested, engine, settings, request,
        )
        val runs = DualLanguage.split(text, mainIsLatin = true, dual.numbersNonLatin, dual.emojisNonLatin)
        val volume = request.params?.takeIf { it.containsKey(TextToSpeech.Engine.KEY_PARAM_VOLUME) }
            ?.getFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME)

        fun speakerOf(run: DualLanguage.Run) = if (run.second) nonLatin else latin
        val elsewhere = runs.filter { speakerOf(it).other != null }
        val queued = HashMap<DualLanguage.Run, SecondEngine.Job?>()
        var nextToQueue = 0
        fun queueAhead() {
            while (nextToQueue < elsewhere.size && queued.size < PREFETCH_RUNS && !stopRequested) {
                val run = elsewhere[nextToQueue++]
                val s = speakerOf(run)
                queued[run] = s.other?.request(
                    text.substring(run.start, run.end), s.voice.voice, s.voice.locale ?: Locale(s.voice.language),
                    s.rate, s.pitch, volume,
                )
            }
        }

        inRuns = true
        try {
            queueAhead()
            for (run in runs) {
                if (stopRequested) break
                val s = speakerOf(run)
                val segment = text.substring(run.start, run.end)
                val codePoint = text.codePointCount(0, run.start)
                val other = s.other
                if (other != null) {
                    if (run !in queued) queueAhead()
                    val job = queued.remove(run)
                    queueAhead()
                    if (job != null) {
                        reportRange(codePoint, codePoint + segment.codePointCount(0, segment.length), framesDelivered)
                        val result = other.play(job, engine.sampleRate, s.gain, ::deliverSamples)
                        if (result == SecondEngine.Result.SPOKEN) continue
                        if (result == SecondEngine.Result.STOPPED || stopRequested) break
                    }
                }
                runCodePoint = codePoint
                runFrames = framesDelivered
                engine.setVoice(s.espeak, settings.voiceVariant)
                applyParameters(engine, settings, request, s.wpm, s.espeakPitch, s.espeakVolume)
                engine.synthesize(segment, false)
            }
        } finally {
            inRuns = false
            if (queued.values.any { it != null }) {
                latin.other?.stop()
                nonLatin.other?.stop()
            }
        }
        callback?.done()
    }

    /** The other engines in use, by package, connected and warmed up the first time they are asked for. */
    private val otherEngines = ConcurrentHashMap<String, SecondEngine>()

    private fun otherEngine(voice: DualLanguage.Voice): SecondEngine {
        otherEngines[voice.engine]?.let {
            if (!it.isBroken) return it
            it.shutdown()
        }
        return SecondEngine(this, storage.cacheDir, voice.engine).also {
            otherEngines[voice.engine] = it
            it.warmUp(voice.voice, voice.locale ?: Locale(voice.language))
        }
    }

    /** Connects to the engines [dual] uses, and lets go of any it no longer uses. */
    private fun connectEngines(dual: DualLanguage.Settings) {
        val used = listOfNotNull(dual.latin, dual.nonLatin).filter { !it.isEspeakZ }
        for ((pkg, client) in otherEngines) {
            if (used.none { it.engine == pkg }) {
                otherEngines.remove(pkg)
                client.shutdown()
            }
        }
        used.forEach { otherEngine(it) }
    }

    /**
     * Hands audio to the framework in pieces it accepts, counting the frames.
     * Returns false once the framework takes no more for this request.
     */
    private fun deliver(audioData: ByteArray, length: Int): Boolean {
        val callback = callback ?: return false
        val maxBytesToCopy = callback.maxBufferSize
        var offset = 0
        while (offset < length) {
            val bytesToWrite = minOf(maxBytesToCopy, length - offset)
            if (callback.audioAvailable(audioData, offset, bytesToWrite) != TextToSpeech.SUCCESS) {
                stopRequested = true
                return false
            }
            offset += bytesToWrite
        }
        if (DEBUG && !firstAudioLogged) {
            firstAudioLogged = true
            val now = SystemClock.elapsedRealtime()
            var line = "request $requestCount: first audio after ${now - requestStarted} ms, ${synthText?.length ?: 0} characters"
            if (requestCount == 1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    line += ", ${now - android.os.Process.getStartElapsedRealtime()} ms after the process started"
                }
                line += ", ${now / 1000.0} s after boot"
            }
            Log.i(TIMING, line)
        }
        framesDelivered += length / 2
        return true
    }

    private var sampleBytes = ByteArray(0)

    /** [deliver] for the second engine's converted 16-bit samples. */
    private fun deliverSamples(samples: ShortArray, count: Int): Boolean {
        if (stopRequested) return false
        if (sampleBytes.size < count * 2) sampleBytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val s = samples[i].toInt()
            sampleBytes[2 * i] = s.toByte()
            sampleBytes[2 * i + 1] = (s shr 8).toByte()
        }
        return deliver(sampleBytes, count * 2)
    }

    /**
     * Reports that code points [startCodePoint] to [endCodePoint] of
     * [synthText] start at frame [marker], against the text the caller
     * supplied.
     */
    private fun reportRange(startCodePoint: Int, endCodePoint: Int, marker: Int) {
        // rangeStart() is API 26; below that the framework has no way to
        // deliver word boundaries to the caller.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || synthText == null) {
            return
        }
        var start = codePointToOffset(startCodePoint)
        var end = codePointToOffset(endCodePoint)
        synthNormalization?.let {
            // The engine spoke normalized text; report the range against
            // the original so highlighting tracks the caller's string.
            start = it.toOriginalOffset(start)
            end = it.toOriginalOffset(end)
        }
        if (end <= start) {
            return
        }
        callback?.rangeStart(marker, synthTextOffset + start, synthTextOffset + end)
    }

    protected open fun rebuildAvailableVoices() {
        synchronized(availableVoices) {
            availableVoices.clear()
            val prefs = preferences
            val voices = if (prefs != null) LanguageSettings.filterVoices(allVoices, prefs) else allVoices
            for (voice in voices) {
                availableVoices[voice.name] = voice
            }
            if (DEBUG) {
                val selected = prefs?.let { LanguageSettings.getSelectedLanguages(it) }
                Log.i(TAG, "Rebuilt voices: selected=${selected?.size ?: "ALL"}, exposed=${availableVoices.size}")
            }
            val current = matchingVoice
            if (current != null && !availableVoices.containsKey(current.name)) {
                matchingVoice = null
            }
        }
    }

    /** Pipes synthesizer output from native eSpeak to the framework's callback. */
    private val synthCallback = object : SpeechSynthesis.SynthReadyCallback {
        override fun onSynthDataReady(audioData: ByteArray) {
            if (callback == null) return
            if (audioData.isEmpty()) {
                onSynthDataComplete()
                return
            }

            if (!deliver(audioData, audioData.size)) {
                // The framework has stopped accepting audio for this
                // request, so the rest of the buffer has nowhere to go. A
                // stop normally reaches the engine through onStop();
                // stopping here as well covers a failure that arrives
                // without one.
                synchronized(engineLock) { engine }?.stop()
            }
        }

        override fun onSynthDataComplete() {
            // In dual languages eSpeak reads one run at a time;
            // synthesizeRuns() ends the request after the last.
            if (inRuns) return
            callback?.done()
        }

        override fun onSynthWordBoundary(textPosition: Int, textLength: Int, markerInFrames: Int) {
            // eSpeak counts code points from 1, within the run it is reading;
            // rangeStart() wants 0-based UTF-16 indices into the text the
            // caller supplied, and frames from the start of the request.
            val wordStart = runCodePoint + textPosition - 1
            reportRange(wordStart, wordStart + maxOf(textLength, 0), runFrames + markerInFrames)
        }
    }

    private companion object {
        const val TAG = "TtsService"
        const val TIMING = "EspeakZTiming"
        /** How many of the second engine's runs are queued with it ahead of their turn. */
        const val PREFETCH_RUNS = 3
        val DEBUG = BuildConfig.DEBUG
    }
}
