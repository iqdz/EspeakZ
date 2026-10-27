/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2012-2015 Reece H. Dunn
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

import com.espeakz.app.SpeechSynthesis

/** Every voice eSpeak NG ships, with what Android should see of it. */
object VoiceData {
    class Voice(
        val name: String,
        val identifier: String,
        val ianaLanguage: String,
        val javaLanguage: String,
        val ianaCountry: String,
        val javaCountry: String,
        val variant: String,
        val gender: Int,
        val displayName: String,
        val locale: String,
        vararg sampleText: String,
    ) {
        val sampleText: List<String> = sampleText.toList()
    }

    class Exception(voice: Voice, context: String?, error: AssertionError) :
        AssertionError("Voice \"${voice.name}\" $context -- $error")

    @JvmField
    val voices = arrayOf(
        Voice("af",              "gmw/af",              "af",  "afr", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Afrikaans",                        "afr",              "Hierdie is 'n voorbeeld van gesproke teks in Afrikaans"),
        Voice("am",              "sem/am",              "am",  "amh", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Amharic",                          "amh",              "ይሄ በአማርኛ ላይ የተነገረ ጽሑፍ ናሙና ነው።", "ይሄ በAmharic ላይ የተነገረ ጽሑፍ ናሙና ነው።"),
        Voice("an",              "roa/an",              "an",  "arg", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Aragonese",                        "arg",              "This is a sample of text spoken in Aragonese"),
        Voice("ar",              "sem/ar",              "ar",  "ara", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Arabic",                           "ara",              "هذه عينة من النص الذي تم نطقه في العربية"),
        Voice("as",              "inc/as",              "as",  "asm", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Assamese",                         "asm",              "This is a sample of text spoken in Assamese", "This is a sample of text spoken in অসমীয়া"),
        Voice("az",              "trk/az",              "az",  "aze", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Azerbaijani",                      "aze",              "This is a sample of text spoken in Azerbaijani", "This is a sample of text spoken in azərbaycan"),
        Voice("bg",              "zls/bg",              "bg",  "bul", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Bulgarian",                        "bul",              "Това е откъс от изговорен текст на български"),
        Voice("bn",              "inc/bn",              "bn",  "ben", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Bengali",                          "ben",              "This is a sample of text spoken in বাংলা", "This is a sample of text spoken in Bengali"),
        Voice("bs",              "zls/bs",              "bs",  "bos", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Bosnian",                          "bos",              "This is a sample of text spoken in Bosnian", "This is a sample of text spoken in bosanski"),
        Voice("ca",              "roa/ca",              "ca",  "cat", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Catalan",                          "cat",              "Aquesta és una mostra de text llegit en veu alta en català"),
        Voice("cs",              "zlw/cs",              "cs",  "ces", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Czech",                            "ces",              "Toto je ukázkový text namluvený v jazyce čeština"),
        Voice("cy",              "cel/cy",              "cy",  "cym", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Welsh",                            "cym",              "This is a sample of text spoken in Welsh", "This is a sample of text spoken in Cymraeg"),
        Voice("da",              "gmq/da",              "da",  "dan", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Danish",                           "dan",              "Dette er et eksempel på talt tekst på dansk"),
        Voice("de",              "gmw/de",              "de",  "deu", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "German",                           "deu",              "Dies ist ein Beispieltext auf Deutsch"),
        Voice("el",              "grk/el",              "el",  "ell", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Greek (Modern)",                   "ell",              "Αυτό είναι ένα δείγμα κειμένου που εκφέρεται στα Ελληνικά"),
        Voice("en-gb-scotland",  "gmw/en-GB-scotland",  "en",  "eng", "GB",  "GBR", "scotland", SpeechSynthesis.GENDER_MALE, "English (Scotland)",               "eng-GBR-scotland", "This is a sample of text spoken in English (United Kingdom,Scottish Standard English)"),
        Voice("en-gb",           "gmw/en",              "en",  "eng", "GB",  "GBR", "",         SpeechSynthesis.GENDER_MALE, "English (UK)",                     "eng-GBR",          "This is a sample of text spoken in English (United Kingdom)"),
        Voice("en-gb-x-gbclan",  "gmw/en-GB-x-gbclan",  "en",  "eng", "GB",  "GBR", "gbclan",   SpeechSynthesis.GENDER_MALE, "English (Lancashire)",             "eng-GBR-gbclan",   "This is a sample of text spoken in English (United Kingdom,GBCLAN)"),
        Voice("en-gb-x-rp",      "gmw/en-GB-x-rp",      "en",  "eng", "GB",  "GBR", "rp",       SpeechSynthesis.GENDER_MALE, "English (Received Pronunciation)", "eng-GBR-rp",       "This is a sample of text spoken in English (United Kingdom,RP)", "This is a sample of text spoken in English (United Kingdom,rp)"),
        Voice("en-gb-x-gbcwmd",  "gmw/en-GB-x-gbcwmd",  "en",  "eng", "GB",  "GBR", "gbcwmd",   SpeechSynthesis.GENDER_MALE, "English (West Midlands)",          "eng-GBR-gbcwmd",   "This is a sample of text spoken in English (United Kingdom,GBCWMD)"),
        Voice("en-us",           "gmw/en-US",           "en",  "eng", "US",  "USA", "",         SpeechSynthesis.GENDER_MALE, "English (US)",                     "eng-USA",          "This is a sample of text spoken in English (United States)"),
        Voice("en-029",          "gmw/en-029",          "en",  "eng", "JM",  "JAM", "",         SpeechSynthesis.GENDER_MALE, "English (Caribbean)",              "eng-JAM",          "This is a sample of text spoken in English (Jamaica)"),
        Voice("eo",              "art/eo",              "eo",  "epo", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Esperanto",                        "epo",              "This is a sample of text spoken in Esperanto", "This is a sample of text spoken in esperanto"),
        Voice("es",              "roa/es",              "es",  "spa", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Spanish",                          "spa",              "Esto es un ejemplo de texto hablado en español."),
        Voice("es-419",          "roa/es-419",          "es",  "spa", "MX",  "MEX", "",         SpeechSynthesis.GENDER_MALE, "Spanish (Latin America)",          "spa-MEX",          "Esto es un ejemplo de texto hablado en español (México).", "Este es un ejemplo de texto hablado en español (México)."),
        Voice("et",              "urj/et",              "et",  "est", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Estonian",                         "est",              "This is a sample of text spoken in eesti", "This is a sample of text spoken in Estonian"),
        Voice("eu",              "eu",                  "eu",  "eus", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Basque",                           "eus",              "This is a sample of text spoken in Basque", "This is a sample of text spoken in euskara"),
        Voice("fa",              "ira/fa",              "fa",  "fas", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Farsi (Persian)",                  "fas",              "این یک نمونه از نوشتار گفته شده در فارسی است"),
        Voice("fi",              "urj/fi",              "fi",  "fin", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Finnish",                          "fin",              "Tämä on näyte puhutusta tekstistä kielellä suomi"),
        Voice("fr-be",           "roa/fr-BE",           "fr",  "fra", "BE",  "BEL", "",         SpeechSynthesis.GENDER_MALE, "French (Belgium)",                 "fra-BEL",          "Voici un exemple de texte énoncé en français (Belgique)."),
        Voice("fr-ch",           "roa/fr-CH",           "fr",  "fra", "CH",  "CHE", "",         SpeechSynthesis.GENDER_MALE, "French (Switzerland)",             "fra-CHE",          "Voici un exemple de texte énoncé en français (Suisse)."),
        Voice("fr-fr",           "roa/fr",              "fr",  "fra", "FR",  "FRA", "",         SpeechSynthesis.GENDER_MALE, "French (France)",                  "fra-FRA",          "Voici un exemple de texte énoncé en français (France)."),
        Voice("ga",              "cel/ga",              "ga",  "gle", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Irish",                            "gle",              "This is a sample of text spoken in Irish", "This is a sample of text spoken in Gaeilge"),
        Voice("gd",              "cel/gd",              "gd",  "gla", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Scottish Gaelic",                  "gla",              "This is a sample of text spoken in Scottish Gaelic", "This is a sample of text spoken in Gàidhlig"),
        Voice("gn",              "sai/gn",              "gn",  "grn", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Guarani",                          "grn",              "This is a sample of text spoken in Guarani"),
        Voice("grc",             "grk/grc",             "grc", "grc", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Greek (Ancient)",                  "grc",              "This is a sample of text spoken in Ancient Greek"),
        Voice("gu",              "inc/gu",              "gu",  "guj", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Gujarati",                         "guj",              "This is a sample of text spoken in Gujarati", "This is a sample of text spoken in ગુજરાતી"),
        Voice("hi",              "inc/hi",              "hi",  "hin", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Hindi",                            "hin",              "यह हिन्दी में बोले गए पाठ का नमूना है"),
        Voice("hr",              "zls/hr",              "hr",  "hrv", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Croatian",                         "hrv",              "Ovo je primjer teksta izgovorenog na hrvatski"),
        Voice("hu",              "urj/hu",              "hu",  "hun", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Hungarian",                        "hun",              "Ez egy magyar nyelven felolvasott szöveg mintája."),
        Voice("hy",              "ine/hy",              "hy",  "hye", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Armenian",                         "hye",              "This is a sample of text spoken in Armenian", "This is a sample of text spoken in հայերեն"),
        Voice("hyw",             "ine/hyw",             "hyw", "hyw", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Armenian (West Armenia)",          "hyw",              "This is a sample of text spoken in Western Armenian", "This is a sample of text spoken in հայերէն", "This is a sample of text spoken in hyw"),
        Voice("ia",              "art/ia",              "ia",  "ina", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Interlingua",                      "ina",              "This is a sample of text spoken in Interlingua", "This is a sample of text spoken in interlingua"),
        Voice("id",              "poz/id",              "in",  "ind", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Indonesia",                        "ind",              "Ini adalah sebuah contoh teks yang diucapkan di Bahasa Indonesia", "Ini adalah sebuah contoh teks yang diucapkan di Indonesia"), // NOTE: 'id' is the correct ISO 639-1 code, but Android/Java uses 'in'.
        Voice("is",              "gmq/is",              "is",  "isl", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Icelandic",                        "isl",              "This is a sample of text spoken in íslenska", "This is a sample of text spoken in Icelandic"),
        Voice("it",              "roa/it",              "it",  "ita", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Italian",                          "ita",              "Questo è un esempio di testo parlato in italiano"),
        Voice("ja",              "jpx/ja",              "ja",  "jpn", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Japanese",                         "jpn",              "日本語で話すテキストサンプルです。"),
        Voice("jbo",             "art/jbo",             "jbo", "jbo", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Lojban",                           "jbo",              "This is a sample of text spoken in Lojban"),
        Voice("ka",              "ccs/ka",              "ka",  "kat", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Georgian",                         "kat",              "This is a sample of text spoken in Georgian", "This is a sample of text spoken in ქართული"),
        Voice("kl",              "esx/kl",              "kl",  "kal", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Greenlandic",                      "kal",              "This is a sample of text spoken in Kalaallisut", "This is a sample of text spoken in kalaallisut"),
        Voice("kn",              "dra/kn",              "kn",  "kan", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Kannada",                          "kan",              "This is a sample of text spoken in Kannada", "This is a sample of text spoken in ಕನ್ನಡ"),
        Voice("ko",              "ko",                  "ko",  "kor", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Korean",                           "kor",              "한국어로 읽은 텍스트 샘플입니다."),
        Voice("kok",             "inc/kok",             "kok", "kok", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Konkani",                          "kok",              "This is a sample of text spoken in कोंकणी"),
        Voice("ku",              "ira/ku",              "ku",  "kur", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Kurdish",                          "kur",              "This is a sample of text spoken in Kurdish"),
        Voice("ky",              "trk/ky",              "ky",  "kir", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Kyrgyz",                           "kir",              "This is a sample of text spoken in кыргызча"),
        Voice("la",              "itc/la",              "la",  "lat", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Latin",                            "lat",              "This is a sample of text spoken in Latin"),
        Voice("lt",              "bat/lt",              "lt",  "lit", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Lithuanian",                       "lit",              "Tai teksto, sakomo lietuvių, pavyzdys"),
        Voice("lv",              "bat/lv",              "lv",  "lav", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Latvian",                          "lav",              "Šis ir izrunāta teksta paraugs šādā valodā: latviešu."),
        Voice("mi",              "poz/mi",              "mi",  "mri", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Māori",                            "mri",              "This is a sample of text spoken in Maori"),
        Voice("mk",              "zls/mk",              "mk",  "mkd", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Macedonian",                       "mkd",              "This is a sample of text spoken in Macedonian", "This is a sample of text spoken in македонски"),
        Voice("ml",              "dra/ml",              "ml",  "mal", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Malayalam",                        "mal",              "This is a sample of text spoken in Malayalam", "This is a sample of text spoken in മലയാളം"),
        Voice("mr",              "inc/mr",              "mr",  "mar", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Marathi",                          "mar",              "This is a sample of text spoken in Marathi", "This is a sample of text spoken in मराठी"),
        Voice("ms",              "poz/ms",              "ms",  "msa", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Malay",                            "msa",              "Ini adalah sampel teks yang dilafazkan dalam Bahasa Melayu", "Ini adalah sampel teks yang dilafazkan dalam Malay"),
        Voice("mt",              "sem/mt",              "mt",  "mlt", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Maltese",                          "mlt",              "This is a sample of text spoken in Malti"),
        Voice("my",              "sit/my",              "my",  "mya", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Myanmar",                          "mya",              "This is a sample of text spoken in Telugu", "This is a sample of text spoken in ဗမာ"),
        Voice("ne",              "inc/ne",              "ne",  "nep", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Nepali",                           "nep",              "This is a sample of text spoken in Nepali", "This is a sample of text spoken in नेपाली"),
        Voice("nl",              "gmw/nl",              "nl",  "nld", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Dutch",                            "nld",              "Dit is een voorbeeld van tekst die is uitgesproken in het Nederlands"),
        Voice("nb",              "gmq/nb",              "nb",  "nob", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Norwegian (Bokmål)",               "nob",              "Dette er et teksteksempel lest opp på norsk bokmål"),
        Voice("om",              "cus/om",              "om",  "orm", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Oromoo",                           "orm",              "This is a sample of text spoken in Oromo", "This is a sample of text spoken in Oromoo"),
        Voice("or",              "inc/or",              "or",  "ori", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Oriya",                            "ori",              "This is a sample of text spoken in Oriya", "This is a sample of text spoken in ଓଡ଼ିଆ"),
        Voice("pa",              "inc/pa",              "pa",  "pan", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Panjabi",                          "pan",              "This is a sample of text spoken in Punjabi", "This is a sample of text spoken in ਪੰਜਾਬੀ"),
        Voice("pap",             "roa/pap",             "pap", "pap", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Papiamento",                       "pap",              "This is a sample of text spoken in Papiamento"),
        Voice("pl",              "zlw/pl",              "pl",  "pol", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Polish",                           "pol",              "To jest przykład tekstu mówionego przy ustawieniu polski"),
        Voice("pt",              "roa/pt",              "pt",  "por", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Portuguese",                       "por",              "Este é um exemplo de texto falado em português"),
        Voice("pt-br",           "roa/pt-BR",           "pt",  "por", "BR",  "BRA", "",         SpeechSynthesis.GENDER_MALE, "Portuguese (Brazil)",              "por-BRA",          "Esta é uma amostra de texto falado em português (Brasil)"),
        Voice("ro",              "roa/ro",              "ro",  "ron", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Romanian",                         "ron",              "Aceasta este o mostră de text vorbit în română"),
        Voice("ru",              "zle/ru",              "ru",  "rus", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Russian",                          "rus",              "Так синтезатор речи озвучивает русский текст"),
        Voice("sd",              "inc/sd",              "sd",  "snd", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Sindhi",                           "snd",              "This is a sample of text spoken in Sindhi"),
        Voice("si",              "inc/si",              "si",  "sin", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Sinhalese",                        "sin",              "This is a sample of text spoken in Sinhala", "This is a sample of text spoken in සිංහල"),
        Voice("sk",              "zlw/sk",              "sk",  "slk", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Slovak",                           "slk",              "Toto je ukážkový text nahovorený v jazyku slovenčina"),
        Voice("sl",              "zls/sl",              "sl",  "slv", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Slovenian",                        "slv",              "To je vzorec besedila, izgovorjen v slovenščina"),
        Voice("sq",              "ine/sq",              "sq",  "sqi", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Albanian",                         "sqi",              "This is a sample of text spoken in Albanian", "This is a sample of text spoken in Shqip", "This is a sample of text spoken in shqip"),
        Voice("sr",              "zls/sr",              "sr",  "srp", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Serbian",                          "srp",              "Ово је пример текста који је изговорен на језику Српски", "Ово је пример текста који је изговорен на језику српски"),
        Voice("sv",              "gmq/sv",              "sv",  "swe", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Swedish",                          "swe",              "Detta är ett textexempel som läses på svenska"),
        Voice("sw",              "bnt/sw",              "sw",  "swa", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Swahili",                          "swa",              "Hii ni sampuli ya maandishi yaliyonenwa katika Kiswahili", "Hii ni sampuli ya maandishi yaliyonenwa katika Swahili"),
        Voice("ta",              "dra/ta",              "ta",  "tam", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Tamil",                            "tam",              "This is a sample of text spoken in தமிழ்", "This is a sample of text spoken in Tamil"),
        Voice("te",              "dra/te",              "te",  "tel", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Telugu",                           "tel",              "This is a sample of text spoken in Telugu", "This is a sample of text spoken in తెలుగు"),
        Voice("tn",              "bnt/tn",              "tn",  "tsn", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Setswana",                         "tsn",              "This is a sample of text spoken in Tswana"),
        Voice("tr",              "trk/tr",              "tr",  "tur", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Turkish",                          "tur",              "Bu, Türkçe dilinde seslendirilen örnek bir metindir"),
        Voice("tt",              "trk/tt",              "tt",  "tat", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Tatar",                            "tat",              "This is a sample of text spoken in Tatar"),
        Voice("ur",              "inc/ur",              "ur",  "urd", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Urdu",                             "urd",              "This is a sample of text spoken in Urdu", "This is a sample of text spoken in اردو"),
        Voice("vi",              "aav/vi",              "vi",  "vie", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Vietnamese",                       "vie",              "Đây là mẫu văn bản được đọc bằng Tiếng Việt"),
        Voice("vi-vn-x-central", "aav/vi-VN-x-central", "vi",  "vie", "VN",  "VNM", "central",  SpeechSynthesis.GENDER_MALE, "Vietnamese",                       "vie-VNM-central",  "Đây là mẫu văn bản được đọc bằng Tiếng Việt (Việt Nam,CENTRAL)", "Đây là mẫu văn bản được đọc bằng Tiếng Việt (Việt Nam,central)"),
        Voice("vi-vn-x-south",   "aav/vi-VN-x-south",   "vi",  "vie", "VN",  "VNM", "south",    SpeechSynthesis.GENDER_MALE, "Vietnamese",                       "vie-VNM-south",    "Đây là mẫu văn bản được đọc bằng Tiếng Việt (Việt Nam,SOUTH)"),
        Voice("cmn",             "sit/cmn",             "zh",  "zho", "",    "",    "",         SpeechSynthesis.GENDER_MALE, "Chinese (Mandarin)",               "zho",              "This is a sample of text spoken in 中文"),
        Voice("yue",             "sit/yue",             "zh",  "zho", "HK",  "HKG", "",         SpeechSynthesis.GENDER_MALE, "Chinese (Cantonese)",              "zho-HKG",          "This is a sample of text spoken in 中文 (中華人民共和國香港特別行政區)", "This is a sample of text spoken in 中文 (香港)"),
    )
}
