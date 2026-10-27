import java.io.File
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application") version "9.3.0"
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("org.hamcrest:hamcrest:2.2")
    androidTestImplementation("org.hamcrest:hamcrest-core:2.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// Release signing is read from keystore.properties in the project root, which
// is never committed. Its passwords may instead come from the
// ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_PASSWORD environment variables.
// Without the file, a release build is left unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    namespace = "com.espeakz.app"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.espeakz.app"
        minSdk = 21
        targetSdk = 36
        versionCode = 23
        versionName = "1.53.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DUSE_ASYNC:BOOL=OFF", "-DUSE_MBROLA:BOOL=OFF", "-DUSE_LIBSONIC:BOOL=ON")
                targets += listOf("ttsespeak", "espeak-data")
            }
        }
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.directories.apply { clear(); add("src") }
            kotlin.directories.apply { clear(); add("src") }
            res.directories.apply { clear(); add("res") }
        }
        getByName("androidTest") {
            java.directories.apply { clear(); add("eSpeakTests/src") }
            kotlin.directories.apply { clear(); add("eSpeakTests/src") }
            res.directories.apply { clear(); add("eSpeakTests/res") }
        }
    }

    lint {
        abortOnError = false
    }
}

/**
 * Packs the voice data that the native build compiled into the app's assets,
 * as plain files, so that the app can copy the engine data and each language's
 * dictionary on its own. espeakdata_version holds a hash of all of it, which
 * tells the app when an update brings new data.
 *
 * espeakdata_index lists every file ("F" lines) and every language file with
 * its name, language and dictionary ("L" lines), so the app reads one small
 * file instead of listing its assets and opening each language file, which
 * took hundreds of milliseconds at every start. The language files are read
 * by the same rule as EspeakData.parseLanguageFile, which is how eSpeak reads
 * them.
 */
abstract class EspeakDataAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun pack() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val data = File(out, "espeak-ng-data")
        dataDir.get().asFile.copyRecursively(data, overwrite = true)

        val files = data.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(data).invariantSeparatorsPath to it }
            .sortedBy { it.first }
            .toList()

        val digest = MessageDigest.getInstance("SHA-256")
        val index = StringBuilder()
        for ((path, file) in files) {
            digest.update(path.toByteArray())
            digest.update(file.readBytes())
            index.append("F\t").append(path).append('\n')
        }
        for ((path, file) in files) {
            if (!path.startsWith("lang/")) continue
            val entry = languageEntry(file.readLines()) ?: continue
            index.append("L\t").append(path.removePrefix("lang/")).append('\t').append(entry).append('\n')
        }
        File(out, "espeakdata_version").writeText(digest.digest().joinToString("") { "%02x".format(it) })
        File(out, "espeakdata_index").writeText(index.toString())
    }

    /** "name\tlanguage\tdictionary" for one language file, or null without a language line. */
    private fun languageEntry(lines: List<String>): String? {
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
        return listOf(name.orEmpty(), first, dictionary ?: first.substringBefore('-')).joinToString("\t")
    }
}

val espeakDataAssets = tasks.register<EspeakDataAssets>("createEspeakDataAssets") {
    // The espeak-data target of jni/CMakeLists.txt compiles the data with a
    // host build of the engine and copies it here.
    dataDir.set(layout.buildDirectory.dir("generated/espeak-ng-data"))
    dependsOn("externalNativeBuildDebug")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(espeakDataAssets, EspeakDataAssets::outputDir)
    }
}
