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

package com.espeakz.app

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.preference.PreferenceManager
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Installs and manages eSpeak's voice data on the device.
 *
 * The data ships in the app's assets as plain files: the engine data that
 * every language needs, and one dictionary per language. The first time the
 * app process starts, the engine data and the dictionaries for the device's
 * languages and English are copied to device-protected storage, where the
 * engine reads them, even before the device is unlocked. More languages are
 * added and removed later through [setInstalledDictionaries].
 *
 * Everything here runs under one lock, so two parts of the app can never
 * install at the same time. A new copy is assembled in a temporary folder and
 * switched into place only once it is complete, and data the engine is using
 * is never deleted. Calling [ensureInstalled] again once the data is in place
 * does nothing. This replaces the old zip extraction, which every caller ran
 * on its own and which started by deleting the whole data folder, so that two
 * of them at once could leave the engine with missing files and silent.
 *
 * Everything that copies files blocks, so it must not run on the main thread.
 */
object EspeakData {
    private const val TAG = "EspeakData"

    /** The data folder's name, in the assets and on the device. */
    const val DATA_DIR = "espeak-ng-data"

    /** The dictionary every voice falls back to, always installed. */
    const val ENGLISH = "en"

    /**
     * Holds the installed dictionaries, written after every change, for
     * anything that reads the settings. TtsService itself notices a change
     * through [generation].
     */
    const val PREF_INSTALLED_DICTIONARIES = "espeakz_installed_dictionaries"

    private const val ASSET_VERSION = "espeakdata_version"
    private const val ASSET_INDEX = "espeakdata_index"
    private const val VERSION_FILE = "version"
    private const val DICT_SUFFIX = "_dict"
    private val DICTIONARY_NAME = Regex("[A-Za-z0-9-]+_dict")

    /** Files the engine cannot run without. */
    private val BASE_RESOURCES = listOf(VERSION_FILE, "intonations", "phondata", "phonindex", "phontab", "en_dict")

    /** One language file of the voice data. */
    class LanguageFile(
        /** The voice identifier: the file's path below lang/, such as "gmw/en". */
        val identifier: String,
        /** The voice's display name from the file, such as "English (Great Britain)". */
        val name: String,
        /** The first language the file lists, such as "en-gb". */
        val language: String,
        /** The dictionary the voice speaks with, such as "en". */
        val dictionary: String,
    )

    /** A language the user can install, by its dictionary. */
    class Language(val dictionary: String, val displayName: String)

    private val lock = Any()

    /**
     * Counts the changes to the installed data, so that a voice list built
     * earlier can tell it is out of date.
     */
    @Volatile
    @JvmStatic
    var generation = 0
        private set

    @Volatile private var storageContext: Context? = null
    @Volatile private var assetVersion: String? = null
    @Volatile private var assetFiles: List<String>? = null
    @Volatile private var languageFiles: Map<String, LanguageFile>? = null

    /**
     * The device-protected storage context, whatever context is passed in, so
     * that the data always lands where the engine reads it. Importing a
     * dictionary through the settings screen's own context once wrote it
     * where the engine never looked.
     */
    @JvmStatic
    fun storage(context: Context): Context {
        storageContext?.let { return it }
        val app = context.applicationContext ?: context
        val storage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) app.createDeviceProtectedStorageContext() else app
        storageContext = storage
        return storage
    }

    @JvmStatic
    fun dataPath(context: Context): File =
        File(storage(context).getDir("voices", Context.MODE_PRIVATE), DATA_DIR)

    @JvmStatic
    fun hasBaseResources(context: Context): Boolean {
        val data = dataPath(context)
        return BASE_RESOURCES.all { File(data, it).exists() }
    }

    /** True when the installed data matches the data this app version ships. */
    @JvmStatic
    fun isInstalled(context: Context): Boolean {
        if (!hasBaseResources(context)) return false
        val installed = try {
            File(dataPath(context), VERSION_FILE).readText().trim()
        } catch (e: Exception) {
            return false
        }
        return installed == assetVersion(context)
    }

    /**
     * Installs the voice data if it is missing or older than this app's, and
     * waits for an install another thread started. Returns whether the data
     * is ready for the engine.
     */
    @JvmStatic
    fun ensureInstalled(context: Context): Boolean {
        val storage = storage(context)
        synchronized(lock) {
            if (isInstalled(storage)) return true
            // An update keeps the languages the user had. A first install, or
            // a data folder an older build left empty, gets the defaults.
            val dictionaries = installedDictionaries(storage).ifEmpty { timed("choose default languages") { defaultDictionaries(storage) } }
            return timed("install voice data") { install(storage, dictionaries) }
        }
    }

    /** The dictionaries installed on the device, such as "ar" and "en". */
    @JvmStatic
    fun installedDictionaries(context: Context): Set<String> {
        val files = dataPath(context).list() ?: return emptySet()
        return files.filter { it.endsWith(DICT_SUFFIX) }.map { it.removeSuffix(DICT_SUFFIX) }.toSet()
    }

    /** The dictionaries this app ships, whether installed or not. */
    @JvmStatic
    fun availableDictionaries(context: Context): Set<String> =
        assetFiles(context)
            .filter { !it.contains('/') && it.endsWith(DICT_SUFFIX) }
            .map { it.removeSuffix(DICT_SUFFIX) }
            .toSet()

    /**
     * The languages the user can install, one per dictionary, by name in the
     * device's language where Android knows it, sorted by that name.
     */
    @JvmStatic
    fun availableLanguages(context: Context): List<Language> {
        val names = languageNames(context)
        return availableDictionaries(context).map { dictionary ->
            Language(dictionary, names[dictionary] ?: dictionary)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    }

    @JvmStatic
    fun displayName(context: Context, dictionary: String): String =
        languageNames(context)[dictionary] ?: dictionary

    /** The language file of a voice, such as "gmw/en", or null when it is unknown. */
    @JvmStatic
    fun languageFile(context: Context, identifier: String): LanguageFile? = languageFiles(context)[identifier]

    /** The dictionary a voice speaks with, or null when the voice is unknown. */
    @JvmStatic
    fun dictionaryForVoice(context: Context, identifier: String): String? =
        languageFiles(context)[identifier]?.dictionary

    /**
     * The dictionaries for the device's languages, plus English. A language
     * eSpeak does not speak adds nothing.
     */
    @JvmStatic
    fun defaultDictionaries(context: Context): Set<String> {
        val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list = LocaleList.getDefault()
            (0 until list.size()).map { list[it] }
        } else {
            listOf(Locale.getDefault())
        }
        // Compared as plain codes: creating a Java locale for each of the
        // 150 language files, or looking up ISO 639-2 codes, takes a third of
        // a second on a watch. eSpeak uses a two-letter code wherever one
        // exists, as Android does, so the codes match as they are.
        val deviceLanguages = locales.map { canonicalLanguage(it.language) }.toSet()
        val result = mutableSetOf(ENGLISH)
        for (file in languageFiles(context).values) {
            val voiceLanguage = canonicalLanguage(file.language.substringBefore('-').lowercase(Locale.ROOT))
            if (voiceLanguage in deviceLanguages) {
                result.add(file.dictionary)
            }
        }
        return result.intersect(availableDictionaries(context) + ENGLISH)
    }

    /**
     * Installs exactly [wanted], plus English. Dictionaries the app ships are
     * copied from its assets, the rest removed. Returns whether it succeeded.
     */
    @JvmStatic
    fun setInstalledDictionaries(context: Context, wanted: Set<String>): Boolean {
        val storage = storage(context)
        synchronized(lock) {
            if (!ensureInstalled(storage)) return false
            val data = dataPath(storage)
            val target = wanted + ENGLISH
            val available = availableDictionaries(storage)
            var ok = true
            for (dictionary in target - installedDictionaries(storage)) {
                if (dictionary !in available) continue
                ok = copyAssetAtomically(storage.assets, "$DATA_DIR/$dictionary$DICT_SUFFIX", File(data, dictionary + DICT_SUFFIX)) && ok
            }
            for (dictionary in installedDictionaries(storage) - target) {
                if (!File(data, dictionary + DICT_SUFFIX).delete()) ok = false
            }
            publishInstalled(storage)
            return ok
        }
    }

    /**
     * Adds a dictionary the user chose, such as one they compiled themselves.
     * The name must look like "xx_dict". Returns whether it was imported.
     */
    @JvmStatic
    fun importDictionary(context: Context, fileName: String, input: InputStream): Boolean {
        if (!DICTIONARY_NAME.matches(fileName)) return false
        val storage = storage(context)
        synchronized(lock) {
            if (!ensureInstalled(storage)) return false
            val data = dataPath(storage)
            val temp = File(data, "$fileName.new")
            try {
                temp.outputStream().use { input.copyTo(it, BUFFER_SIZE) }
                if (!temp.renameTo(File(data, fileName))) {
                    temp.delete()
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import $fileName", e)
                temp.delete()
                return false
            }
            publishInstalled(storage)
            return true
        }
    }

    private fun install(storage: Context, dictionaries: Set<String>): Boolean {
        val started = SystemClock.elapsedRealtime()
        val target = dataPath(storage)
        val parent = target.parentFile!!
        val temp = File(parent, "$DATA_DIR.new")
        val old = File(parent, "$DATA_DIR.old")
        try {
            temp.deleteRecursively()
            old.deleteRecursively()
            val assets = storage.assets
            val available = availableDictionaries(storage)
            val wanted = (dictionaries + ENGLISH).filter { it in available }.toSet()

            // The engine data, then the wanted dictionaries.
            for (path in assetFiles(storage)) {
                val isDictionary = !path.contains('/') && path.endsWith(DICT_SUFFIX)
                if (isDictionary && path.removeSuffix(DICT_SUFFIX) !in wanted) continue
                copyAsset(assets, "$DATA_DIR/$path", File(temp, path))
            }
            // Dictionaries the user imported, which the app does not ship.
            target.listFiles()?.forEach { file ->
                if (file.name.endsWith(DICT_SUFFIX) && file.name.removeSuffix(DICT_SUFFIX) !in available) {
                    file.copyTo(File(temp, file.name), overwrite = true)
                }
            }
            // Written last: the data counts as installed only once it is all there.
            File(temp, VERSION_FILE).writeText(assetVersion(storage))

            if (target.exists() && !target.renameTo(old)) {
                throw IllegalStateException("Could not move the old voice data aside")
            }
            if (!temp.renameTo(target)) {
                old.renameTo(target)
                throw IllegalStateException("Could not move the new voice data into place")
            }
            old.deleteRecursively()
            publishInstalled(storage)
            Log.i(TAG, "Installed voice data with ${wanted.sorted()} in ${SystemClock.elapsedRealtime() - started} ms")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install the voice data", e)
            temp.deleteRecursively()
            return false
        }
    }

    private fun publishInstalled(storage: Context) {
        generation++
        @Suppress("DEPRECATION")
        PreferenceManager.getDefaultSharedPreferences(storage).edit()
            .putStringSet(PREF_INSTALLED_DICTIONARIES, installedDictionaries(storage))
            .apply()
    }

    private fun copyAsset(assets: AssetManager, path: String, destination: File) {
        destination.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
        assets.open(path).use { input ->
            destination.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
        }
    }

    /** Copies next to the destination first, so the engine never reads half a file. */
    private fun copyAssetAtomically(assets: AssetManager, path: String, destination: File): Boolean {
        val temp = File(destination.parentFile, destination.name + ".new")
        return try {
            copyAsset(assets, path, temp)
            temp.renameTo(destination) || run { temp.delete(); false }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install $path", e)
            temp.delete()
            false
        }
    }

    private fun assetVersion(context: Context): String {
        assetVersion?.let { return it }
        val version = storage(context).assets.open(ASSET_VERSION).bufferedReader().use { it.readText().trim() }
        assetVersion = version
        return version
    }

    /** Every file of the shipped data, as paths below espeak-ng-data. */
    private fun assetFiles(context: Context): List<String> {
        assetFiles?.let { return it }
        readIndex(context)
        assetFiles?.let { return it }
        // No index, as in a build made without it: list the assets, which is
        // slow, seconds on a watch.
        val files = mutableListOf<String>()
        timed("list voice data assets") { listAssets(storage(context).assets, DATA_DIR, "", files) }
        assetFiles = files
        return files
    }

    /**
     * Reads espeakdata_index, which the build writes: every file of the data
     * and every language file's name, language and dictionary. One small file
     * instead of hundreds of asset lookups at every start.
     */
    private fun readIndex(context: Context) {
        val lines = try {
            timed("read the voice data index") {
                storage(context).assets.open(ASSET_INDEX).bufferedReader().use { it.readLines() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No voice data index; reading the assets one by one", e)
            return
        }
        val files = ArrayList<String>(lines.size)
        val languages = HashMap<String, LanguageFile>()
        for (line in lines) {
            val parts = line.split('\t')
            when {
                parts[0] == "F" && parts.size == 2 -> files.add(parts[1])
                parts[0] == "L" && parts.size == 5 -> {
                    val identifier = parts[1]
                    languages[identifier] = LanguageFile(
                        identifier,
                        readableName(parts[2].ifEmpty { identifier.substringAfterLast('/') }),
                        parts[3],
                        parts[4],
                    )
                }
            }
        }
        languageFiles = languages
        assetFiles = files
    }

    private fun listAssets(assets: AssetManager, root: String, relative: String, into: MutableList<String>) {
        val path = if (relative.isEmpty()) root else "$root/$relative"
        val children = assets.list(path) ?: return
        if (children.isEmpty()) {
            if (relative.isNotEmpty()) into.add(relative)
            return
        }
        for (child in children) {
            listAssets(assets, root, if (relative.isEmpty()) child else "$relative/$child", into)
        }
    }

    /** Every language file the voice data has. */
    @JvmStatic
    fun allLanguageFiles(context: Context): Collection<LanguageFile> = languageFiles(context).values

    /** The language files, read from the assets, by voice identifier. */
    private fun languageFiles(context: Context): Map<String, LanguageFile> {
        languageFiles?.let { return it }
        readIndex(context)
        languageFiles?.let { return it }
        val assets = storage(context).assets
        val files = HashMap<String, LanguageFile>()
        timed("read language files") {
            for (path in assetFiles(context)) {
                if (!path.startsWith("lang/")) continue
                val identifier = path.removePrefix("lang/")
                parseLanguageFile(identifier, assets.open("$DATA_DIR/$path").bufferedReader().use { it.readLines() })
                    ?.let { files[identifier] = it }
            }
        }
        languageFiles = files
        return files
    }

    /**
     * Reads a language file the way eSpeak does: the first "language" line
     * names the language, and its part before the first "-" names the
     * dictionary unless a "dictionary" line says otherwise.
     */
    /**
     * A language file's name as people read it. A few files are named like
     * codes, such as "xextan-test" for the constructed language Xextan: the
     * test mark is dropped and the name capitalized. Real names, which carry
     * capitals, are left as they are.
     */
    @JvmStatic
    fun readableName(name: String): String {
        if (name.any { it.isUpperCase() }) return name
        val words = name.removeSuffix("-test").removeSuffix("_test").replace('-', ' ').replace('_', ' ').trim()
        return words.ifEmpty { name }.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    @JvmStatic
    fun parseLanguageFile(identifier: String, lines: List<String>): LanguageFile? {
        var name: String? = null
        var language: String? = null
        var dictionary: String? = null
        for (raw in lines) {
            val line = raw.substringBefore("//").trim()
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "name" -> if (name == null) name = line.removePrefix("name").trim()
                "language" -> if (language == null && parts.size > 1) language = parts[1]
                "dictionary" -> if (parts.size > 1) dictionary = parts[1]
            }
        }
        val first = language ?: return null
        return LanguageFile(
            identifier,
            readableName(name ?: identifier.substringAfterLast('/')),
            first,
            dictionary ?: first.substringBefore('-'),
        )
    }

    /**
     * A name for each shipped dictionary: the language's name in the device's
     * language where Android knows it, otherwise the name eSpeak's language
     * file gives. Where two dictionaries would share a name, as Mandarin and
     * Cantonese both read "Chinese", the language files' names tell them apart.
     */
    private fun languageNames(context: Context): Map<String, String> {
        val files = languageFiles(context).values
        val fileNames = HashMap<String, String>()
        val localized = HashMap<String, String>()
        for (dictionary in availableDictionaries(context)) {
            val file = files.firstOrNull { it.identifier.substringAfterLast('/') == dictionary }
                ?: files.firstOrNull { it.dictionary == dictionary }
            fileNames[dictionary] = file?.name ?: dictionary
            val name = SpeechSynthesis.localeFromLanguageName(dictionary)?.getDisplayLanguage(Locale.getDefault())
            if (!name.isNullOrEmpty() && !name.equals(dictionary, ignoreCase = true)) {
                localized[dictionary] = name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            }
        }
        val shared = localized.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return fileNames.mapValues { (dictionary, fileName) ->
            localized[dictionary]?.takeIf { it !in shared } ?: fileName
        }
    }

    /**
     * One spelling for each language code: the current ISO codes for the
     * three that Java once renamed, and Chinese for eSpeak's Mandarin and
     * Cantonese, as SpeechSynthesis maps them.
     */
    private fun canonicalLanguage(code: String): String = when (code) {
        "iw" -> "he"
        "in" -> "id"
        "ji" -> "yi"
        "cmn", "yue" -> "zh"
        else -> code
    }

    private const val BUFFER_SIZE = 64 * 1024
}
