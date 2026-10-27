# EspeakZ

By Harith Alhamdani

EspeakZ is a text-to-speech engine for Android, made for screen reader users first, TalkBack users above all. It is a fork of the eSpeak NG Android app and speaks with the eSpeak NG speech engine, which supports more than 100 languages and accents.

EspeakZ is not the official eSpeak NG app and is not endorsed by the eSpeak NG project. It installs alongside the official app.

## Privacy

1. EspeakZ does not collect any data and does not connect to the internet for any reason. It does not ask for the internet permission.
2. What EspeakZ reads, its settings and its voice data stay on your device.
3. With Dual languages, EspeakZ hands the text in the second language to the engine whose voice you chose. That engine's own privacy terms apply to it, and a voice marked "needs internet" sends that text to its provider.

## About the fork

The fork's goals:

1. Speech from the very first request after installing, without turning the engine off and on and without an extra install step. Done.
2. Klatt variant 6 as the default voice variant. Done.
3. The app's Android code rewritten in Kotlin. Done.
4. Everything in the app fully usable with TalkBack, a keyboard and a TV remote's D-pad. In progress.

## Changes from eSpeak NG

EspeakZ started from eSpeak NG 1.53.0, upstream commit ba90c8e9f440ad544f674a790bb5f53878b6ffc5 of 22 September 2026. Changes made in October 2026:

1. Speech from the first request. The voice data installs the moment the app starts, whatever starts it, and the speech service waits for it instead of failing, so a screen reader's first words after installing EspeakZ or selecting it as the engine are spoken. Android's voice data check never reports data as missing, so there is no install step. In eSpeak NG, two parts of the app could unpack the voice data at the same time, each deleting the other's files, and the engine could be started again while it was speaking; either could leave it silent until it was turned off and on.
2. Voice languages. The first start installs the engine data with the dictionaries for the device's languages and English only, which takes a fraction of a second. More languages are added and removed in Settings, Voice languages. Every change is copied next to the data first and switched into place only when complete.
3. Klatt variant 6 is the default voice variant. A variant chosen in the settings, or the old default gender setting, still wins.
4. The app's Android code and its tests are written in Kotlin. The native engine is started once per process and used by one caller at a time. The settings screen reads from the engine on a background thread, so it never waits for speech in progress.
5. Fixes: the Announcer and Marcelo voice variants pointed to files that do not exist and left the engine silent; two Robosoft variants were listed twice; Import eSpeak dictionary saved files where the engine never looked, and now uses Android's file picker; a variant eSpeak cannot load now falls back to the plain voice instead of silence.
6. The app is named EspeakZ, with application id and package com.espeakz.app, so it installs alongside the official eSpeak NG app. It has a launcher icon on phones, tablets, TVs and watches, which opens its settings.
7. The settings have an About EspeakZ screen with the author, the changes, the credits and the license.
8. Faster start. The build writes an index of the voice data, so the app reads one small file instead of looking up hundreds of files at every start. Every start after the first now takes about 15 milliseconds on a phone and under a tenth of a second on a OnePlus Watch 2, where it took 0.3 and 2.6 seconds. The first start after installing, which also copies the data, takes about 0.3 seconds on the phone and 1.2 seconds on the watch, where it took 0.8 and 5 seconds.
9. A fuller voice: a low-shelf filter lifts the frequencies below 220 Hz by 8 dB, for every voice and variant, without letting loud speech distort.
10. Dual languages. Two languages are read at once, each by its own voice: one written in Latin letters, such as English or French, and one in another alphabet, such as Arabic. In Settings, Dual languages, one list holds every language that EspeakZ or any other installed engine speaks, with the two in use checked; choosing a language puts it in use for its alphabet and shows its installed voices from every engine. Every language keeps the voice chosen for it, with its own speech rate, pitch and volume and a Test voice button, so many languages can be set up and any two used. Numbers and punctuation, and emojis, are read by the voice you choose. Another engine's audio joins EspeakZ's own stream, prepared a few parts ahead, its voice loaded before the first words and the silence around its parts cut short, so the two voices follow each other without gaps. An engine that cannot speak, or does not answer, is passed over and EspeakZ reads the text itself.
11. EspeakZ language. EspeakZ speaks in the device's language, plain English being American English, and in that language when an app asks for one EspeakZ does not have. Choose another in Settings, EspeakZ language, and turn on Use EspeakZ language in all apps to use it whatever apps ask for.
12. Numbers longer than 6 digits, such as phone numbers, can be read one, two or three digits at a time.
13. Keep EspeakZ running asks Android to leave EspeakZ out of battery saving, on phones, tablets and TVs. EspeakZ speaks on the lock screen and before the first unlock after a restart.
14. Quicker answers. EspeakZ hands over its first 60 milliseconds of speech instead of waiting for 300, no longer reloads a voice that has not changed, builds its voice list from the index instead of reading every voice file, and starts the engine off the main thread. With TalkBack, the first sound of each request now comes about 4 milliseconds after it on a OnePlus Ace 2 Pro and about 15 milliseconds after it on a OnePlus Watch 2, and a stop takes effect within a few milliseconds.
15. Easier to use with a screen reader, a keyboard and a D-pad: the voice settings sliders say their name and value once, Tab and Shift+Tab leave every list for its buttons, lists open on the entry already chosen, and the voice settings have a Test voice button.
16. Languages that EspeakZ has but has not installed are reported to Android as such, and Android's install voice data button opens Voice languages. A language file named like a code, xextan-test, is shown as Xextan.
17. Android TV and Fire TV show EspeakZ with a banner on their home screens.
18. Privacy: EspeakZ collects no data and does not connect to the internet.
19. The project: the app is an app module, the engine source is in the espeak-ng folder, the build file is Kotlin script, and the voice data is packed in the app as plain files. On a Windows computer, the voice data step builds the engine with Ninja and the C compiler on PATH, because CMake 3.22.1 has no generator for Visual Studio 2026.

## Credits

EspeakZ exists because of the work of the eSpeak NG project and everyone who contributed to it.

1. eSpeak NG, https://github.com/espeak-ng/espeak-ng: the speech engine, the voice data for every language and the original Android app. eSpeak NG is maintained by Reece H. Dunn and the eSpeak NG contributors.
2. eSpeak, created by Jonathan Duddington, which eSpeak NG is based on.
3. The eSpeak NG Android app, by Reece H. Dunn, Beka Gozalishvili, Alexandr Epaneshnikov and the eSpeak NG contributors, with code from Google Inc. and the Android Open Source Project.
4. The language maintainers and translators of eSpeak NG and its Android app, named in espeak-ng/ChangeLog.md and in the source files.
5. The Sonic library by Bill Cox, https://github.com/waywardgeek/sonic, which makes high speech rates possible.

## License

EspeakZ is free and open source software, under the same license as eSpeak NG: the GNU General Public License, version 3 or any later version. LICENSE.txt has the copyright notice and the license of each part, and COPYING has the full license text. Every copyright notice in the source files is kept.

The complete source code of EspeakZ, with all changes, is at https://github.com/iqdz/EspeakZ, under the same license.

EspeakZ comes with no warranty, to the extent permitted by law.

Android is a trademark of Google LLC.

## Building

From the project root, run gradlew :app:assembleDebug. The build needs:

1. JDK 17 or later.
2. The Android SDK with platform 36, NDK 29.0.14206865 and CMake 3.22.1.
3. git, to fetch the Sonic library during the first build.
4. A C compiler for the computer you build on, used to compile the voice data: gcc or clang on Linux and macOS, llvm-mingw on Windows.

The debug APK is written to app/build/outputs/apk/debug.
