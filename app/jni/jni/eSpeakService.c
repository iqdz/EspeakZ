/*
 * Copyright (C) 2026 Harith Alhamdani
 * Copyright (C) 2012-2017 Reece H. Dunn
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
 * This file contains the JNI bindings to eSpeak used by SpeechSynthesis.kt.
 *
 * Android Version: 4.0 (Ice Cream Sandwich)
 * API Version:     14
 */

#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <string.h>
#include <math.h>
#include <jni.h>

#include <espeak-ng/speak_lib.h>
#include <Log.h>

/* How much audio eSpeak makes before handing it over. The first piece of an
 * utterance waits for a whole buffer, so this is the speech a screen reader
 * waits for: 300 ms cost a watch about 40 ms per request before any sound. */
#define BUFFER_SIZE_IN_MILLISECONDS 60

/* These are helpers for converting a jstring to wchar_t*.
 *
 * This assumes that wchar_t is a 32-bit (UTF-32) value.
 */
//@{

static const char *utf8_read(const char *in, wchar_t *c)
{
	if (((uint8_t)*in) < 0x80)
		*c = *in++;
	else switch (((uint8_t)*in) & 0xF0)
	{
	default:
		*c = ((uint8_t)*in++) & 0x1F;
		*c = (*c << 6) + (((uint8_t)*in++) & 0x3F);
		break;
	case 0xE0:
		*c = ((uint8_t)*in++) & 0x0F;
		*c = (*c << 6) + (((uint8_t)*in++) & 0x3F);
		*c = (*c << 6) + (((uint8_t)*in++) & 0x3F);
		break;
	case 0xF0:
		*c = ((uint8_t)*in++) & 0x07;
		*c = (*c << 6) + (((uint8_t)*in++) & 0x3F);
		*c = (*c << 6) + (((uint8_t)*in++) & 0x3F);
		*c = (*c << 6) + (((uint8_t)*in++) & 0x3F);
		break;
	}
	return in;
}

static wchar_t *unicode_string(JNIEnv *env, jstring str)
{
  if (str == NULL) return NULL;

  const char *utf8 = (*env)->GetStringUTFChars(env, str, NULL);
  wchar_t *utf32 = (wchar_t *)malloc((strlen(utf8) + 1) * sizeof(wchar_t));

  const char *utf8_current = utf8;
  wchar_t *utf32_current = utf32;
  while (*utf8_current)
  {
    utf8_current = utf8_read(utf8_current, utf32_current);
    ++utf32_current;
  }
  *utf32_current = 0;

  (*env)->ReleaseStringUTFChars(env, str, utf8);
  return utf32;
}

//@}

#define LOG_TAG "eSpeakService"
#define DEBUG true

enum synthesis_result {
  SYNTH_CONTINUE = 0,
  SYNTH_ABORT = 1
};

static JavaVM *jvm = NULL;
jmethodID METHOD_nativeSynthCallback;
jmethodID METHOD_nativeSynthWordCallback;

/* Audio frames handed to the Java layer so far for the current request.
 * Reset by nativeSynthesize before each espeak_Synth call. */
static int frames_delivered = 0;

/* Set by nativeStop from the framework's control thread while espeak_Synth is
 * still running on the synthesis thread.  espeak_ng_Cancel() cannot interrupt a
 * synthesis in progress -- its body is entirely #if USE_ASYNC, which this build
 * disables -- so returning SYNTH_ABORT from the callback is the only way to end
 * one early.  Without it a stop has to wait for the whole utterance to be
 * synthesized, which is silence for as long as the text the user just left. */
static atomic_int stop_requested;

/* A gentle low-shelf filter on everything eSpeak produces, so the voice
 * sounds a little fuller: frequencies below BASS_SHELF_HZ are lifted by
 * BASS_GAIN_DB, the rest is left as it is. It is the RBJ Audio EQ Cookbook
 * low shelf with a shelf slope of 1. Samples that the boost would push past
 * the 16-bit range are clipped to it. The state is reset per request, so one
 * utterance never colours the start of the next. Only nativeSynthesize runs
 * it, and the Kotlin side serialises those calls. */
#define BASS_SHELF_HZ 220.0
#define BASS_GAIN_DB 8.0

static struct {
  float b0, b1, b2, a1, a2;
  float x1, x2, y1, y2;
  int enabled;
} bass;

static void bass_init(int sample_rate) {
  if (sample_rate <= 0) {
    bass.enabled = 0;
    return;
  }
  const double a = pow(10.0, BASS_GAIN_DB / 40.0);
  const double w0 = 2.0 * M_PI * BASS_SHELF_HZ / sample_rate;
  const double cos_w0 = cos(w0);
  const double alpha = sin(w0) / 2.0 * sqrt(2.0);
  const double sqrt_a_alpha = 2.0 * sqrt(a) * alpha;
  const double a0 = (a + 1) + (a - 1) * cos_w0 + sqrt_a_alpha;
  bass.b0 = (float)(a * ((a + 1) - (a - 1) * cos_w0 + sqrt_a_alpha) / a0);
  bass.b1 = (float)(2 * a * ((a - 1) - (a + 1) * cos_w0) / a0);
  bass.b2 = (float)(a * ((a + 1) - (a - 1) * cos_w0 - sqrt_a_alpha) / a0);
  bass.a1 = (float)(-2 * ((a - 1) + (a + 1) * cos_w0) / a0);
  bass.a2 = (float)(((a + 1) + (a - 1) * cos_w0 - sqrt_a_alpha) / a0);
  bass.enabled = 1;
}

static void bass_reset(void) {
  bass.x1 = bass.x2 = bass.y1 = bass.y2 = 0;
}

static void bass_process(short *samples, int count) {
  if (!bass.enabled) return;
  for (int i = 0; i < count; i++) {
    const float x = samples[i];
    const float y = bass.b0 * x + bass.b1 * bass.x1 + bass.b2 * bass.x2
                  - bass.a1 * bass.y1 - bass.a2 * bass.y2;
    bass.x2 = bass.x1;
    bass.x1 = x;
    bass.y2 = bass.y1;
    bass.y1 = y;
    samples[i] = (short)(y > 32767.0f ? 32767 : (y < -32768.0f ? -32768 : y));
  }
}

static JNIEnv *getJniEnv() {
  JNIEnv *env = NULL;
  (*jvm)->AttachCurrentThread(jvm, &env, NULL);
  return env;
}

/* Callback from espeak.  Should call back to the TTS API */
static int SynthCallback(short *audioData, int numSamples,
                         espeak_EVENT *events) {
  JNIEnv *env = getJniEnv();
  jobject object = (jobject)events->user_data;

  /* espeak marks the end of the request with a NULL buffer, not with a zero
   * sample count -- an empty buffer can legitimately occur mid-stream. */
  if (audioData == NULL || atomic_load(&stop_requested)) {
    /* Report completion either way: espeak returns ENS_SPEECH_STOPPED without
     * a final NULL-buffer callback when aborted, so this is the only place the
     * Java side hears that the request is over and can call done(). */
    (*env)->CallVoidMethod(env, object, METHOD_nativeSynthCallback, NULL);
    return SYNTH_ABORT;
  }

  for (espeak_EVENT *event = events;
       event->type != espeakEVENT_LIST_TERMINATED; ++event) {
    if (event->type != espeakEVENT_WORD)
      continue;

    /* event->sample is an absolute frame index within the request, but it is
     * recorded inside WavegenFill2() before libsonic compresses the buffer, so
     * at sonic-accelerated rates (wpm above espeakRATE_MAXIMUM) it can point
     * past the audio actually produced.  Clamping to the current buffer keeps
     * it exact while sonic is idle and bounded by one buffer when it is not. */
    int marker = event->sample;
    if (marker < frames_delivered)
      marker = frames_delivered;
    else if (marker > frames_delivered + numSamples)
      marker = frames_delivered + numSamples;

    (*env)->CallVoidMethod(env, object, METHOD_nativeSynthWordCallback,
                           (jint) event->text_position, (jint) event->length,
                           (jint) marker);
  }

  if (numSamples > 0) {
    bass_process(audioData, numSamples);
    jbyteArray arrayAudioData = (*env)->NewByteArray(env, numSamples * 2);
    (*env)->SetByteArrayRegion(env, arrayAudioData, 0, (numSamples * 2), (jbyte *) audioData);
    (*env)->CallVoidMethod(env, object, METHOD_nativeSynthCallback, arrayAudioData);
    /* The callback runs many times per request without returning to Java, so
     * the local reference has to be released here or the table overflows. */
    (*env)->DeleteLocalRef(env, arrayAudioData);
    frames_delivered += numSamples;
  }

  return SYNTH_CONTINUE;
}

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

JNIEXPORT jint
JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  jvm = vm;
  JNIEnv *env;

  if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
    LOGE("Failed to get the environment using GetEnv()");
    return -1;
  }

  return JNI_VERSION_1_6;
}

JNIEXPORT jboolean
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeClassInit(
    JNIEnv* env, jclass clazz) {
  if (DEBUG) LOGV("%s", __FUNCTION__);
  METHOD_nativeSynthCallback = (*env)->GetMethodID(env, clazz, "nativeSynthCallback", "([B)V");
  METHOD_nativeSynthWordCallback = (*env)->GetMethodID(env, clazz, "nativeSynthWordCallback", "(III)V");

  return JNI_TRUE;
}

JNIEXPORT jint
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeCreate(
    JNIEnv *env, jobject object, jstring path) {
  if (DEBUG) LOGV("%s [env=%p, object=%p]", __FUNCTION__, env, object);

  const char *c_path = path ? (*env)->GetStringUTFChars(env, path, NULL) : NULL;

  if (DEBUG) LOGV("Initializing with path %s", c_path);
  int sampleRate = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, BUFFER_SIZE_IN_MILLISECONDS, c_path, 0);
  bass_init(sampleRate);

  if (c_path) (*env)->ReleaseStringUTFChars(env, path, c_path);

  return sampleRate;
}

JNIEXPORT jobject
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeGetVersion(
    JNIEnv *env, jclass clazz) {
  if (DEBUG) LOGV("%s", __FUNCTION__);
  return (*env)->NewStringUTF(env, espeak_Info(NULL));
}

JNIEXPORT jobjectArray
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeGetAvailableVoices(
    JNIEnv *env, jobject object) {
  if (DEBUG) LOGV("%s", __FUNCTION__);

  const espeak_VOICE **voices = espeak_ListVoices(NULL);

  int count;

  // First, count the number of voices returned.
  for (count = 0; voices[count] != NULL; count++);

  // Next, create a Java String array.
  jobjectArray voicesArray = (jobjectArray) (*env)->NewObjectArray(
      env, count * 4, (*env)->FindClass(env, "java/lang/String"), NULL);

  const espeak_VOICE *v;
  char gender_buf[12];
  char age_buf[12];

  // Finally, populate the array.
  for (int i = 0, voicesIndex = 0; (v = voices[i]) != NULL; i++) {
    const char *lang_name = v->languages + 1;
    const char *identifier = v->identifier;
    sprintf(gender_buf, "%d", v->gender);
    sprintf(age_buf, "%d", v->age);

    jstring lang = (*env)->NewStringUTF(env, lang_name);
    (*env)->SetObjectArrayElement(env, voicesArray, voicesIndex++, lang);
    (*env)->DeleteLocalRef(env, lang);
    jstring ident = (*env)->NewStringUTF(env, identifier);
    (*env)->SetObjectArrayElement(env, voicesArray, voicesIndex++, ident);
    (*env)->DeleteLocalRef(env, ident);
    jstring gender = (*env)->NewStringUTF(env, gender_buf);
    (*env)->SetObjectArrayElement(env, voicesArray, voicesIndex++, gender);
    (*env)->DeleteLocalRef(env, gender);
    jstring age = (*env)->NewStringUTF(env, age_buf);
    (*env)->SetObjectArrayElement(env, voicesArray, voicesIndex++, age);
    (*env)->DeleteLocalRef(env, age);
  }

  return voicesArray;
}

JNIEXPORT jboolean
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeSetVoiceByName(
    JNIEnv *env, jobject object, jstring name) {
  const char *c_name = name ? (*env)->GetStringUTFChars(env, name, NULL) : NULL;

  if (DEBUG) LOGV("%s(name=%s)", __FUNCTION__, c_name);

  const espeak_ERROR result = espeak_SetVoiceByName(c_name);

  if (c_name) (*env)->ReleaseStringUTFChars(env, name, c_name);

  switch (result) {
    case EE_OK:             return JNI_TRUE;
    case EE_INTERNAL_ERROR: LOGE("espeak_SetVoiceByName: internal error."); break;
    case EE_BUFFER_FULL:    LOGE("espeak_SetVoiceByName: buffer full."); break;
    case EE_NOT_FOUND:      LOGE("espeak_SetVoiceByName: not found."); break;
  }

  return JNI_FALSE;
}

JNIEXPORT jboolean
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeSetVoiceByProperties(
    JNIEnv *env, jobject object, jstring language, jint gender, jint age) {
  const char *c_language = language ? (*env)->GetStringUTFChars(env, language, NULL) : NULL;

  if (DEBUG) LOGV("%s(language=%s, gender=%d, age=%d)", __FUNCTION__, c_language, gender, age);

  espeak_VOICE voice_select;
  memset(&voice_select, 0, sizeof(espeak_VOICE));
  voice_select.languages = c_language;
  voice_select.gender = (int) gender;
  voice_select.age = (int) age;

  const espeak_ERROR result = espeak_SetVoiceByProperties(&voice_select);

  if (c_language) (*env)->ReleaseStringUTFChars(env, language, c_language);

  switch (result) {
    case EE_OK:             return JNI_TRUE;
    case EE_INTERNAL_ERROR: LOGE("espeak_SetVoiceByProperties: internal error."); break;
    case EE_BUFFER_FULL:    LOGE("espeak_SetVoiceByProperties: buffer full."); break;
    case EE_NOT_FOUND:      LOGE("espeak_SetVoiceByProperties: not found."); break;
  }

  return JNI_FALSE;
}

JNIEXPORT jboolean
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeSetParameter(
    JNIEnv *env, jobject object, jint parameter, jint value) {
  if (DEBUG) LOGV("%s(parameter=%d, value=%d)", __FUNCTION__, parameter, value);
  const espeak_ERROR result = espeak_SetParameter((espeak_PARAMETER)parameter, (int)value, 0);

  switch (result) {
    case EE_OK:             return JNI_TRUE;
    case EE_INTERNAL_ERROR: LOGE("espeak_SetParameter: internal error."); break;
    case EE_BUFFER_FULL:    LOGE("espeak_SetParameter: buffer full."); break;
    case EE_NOT_FOUND:      LOGE("espeak_SetParameter: not found."); break;
  }

  return JNI_FALSE;
}

JNIEXPORT jint
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeGetParameter(
    JNIEnv *env, jobject object, jint parameter, jint current) {
  if (DEBUG) LOGV("%s(parameter=%d, pitch=%d)", __FUNCTION__, parameter, current);
  return espeak_GetParameter((espeak_PARAMETER)parameter, (int)current);
}

JNIEXPORT jboolean
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeSetPunctuationCharacters(
    JNIEnv *env, jobject object, jstring characters) {
  if (DEBUG) LOGV("%s)", __FUNCTION__);

  wchar_t *list = unicode_string(env, characters);
  const espeak_ERROR result = espeak_SetPunctuationList(list);
  free(list);
  switch (result) {
    case EE_OK:             return JNI_TRUE;
    case EE_INTERNAL_ERROR: LOGE("espeak_SetPunctuationList: internal error."); break;
    case EE_BUFFER_FULL:    LOGE("espeak_SetPunctuationList: buffer full."); break;
    case EE_NOT_FOUND:      LOGE("espeak_SetPunctuationList: not found."); break;
  }

  return JNI_FALSE;
}

JNIEXPORT jboolean
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeSynthesize(
    JNIEnv *env, jobject object, jstring text, jboolean isSsml) {
  if (DEBUG) LOGV("%s", __FUNCTION__);
  const char *c_text = text ? (*env)->GetStringUTFChars(env, text, NULL) : NULL;
  unsigned int unique_identifier;

  espeak_SetSynthCallback(SynthCallback);
  frames_delivered = 0;
  bass_reset();
  atomic_store(&stop_requested, 0);
  const espeak_ERROR result = espeak_Synth(c_text, strlen(c_text), 0,  // position
               POS_CHARACTER, 0, // end position (0 means no end position)
               isSsml ? espeakCHARS_UTF8 | espeakSSML // UTF-8 encoded SSML
                      : espeakCHARS_UTF8,             // UTF-8 encoded text
               &unique_identifier, object);
  espeak_Synchronize();

  if (c_text) (*env)->ReleaseStringUTFChars(env, text, c_text);

  switch (result) {
    case EE_OK:             return JNI_TRUE;
    case EE_INTERNAL_ERROR: LOGE("espeak_Synth: internal error."); break;
    case EE_BUFFER_FULL:    LOGE("espeak_Synth: buffer full."); break;
    case EE_NOT_FOUND:      LOGE("espeak_Synth: not found."); break;
  }

  return JNI_TRUE;
}

JNIEXPORT jboolean
JNICALL Java_com_espeakz_app_SpeechSynthesis_nativeStop(
    JNIEnv *env, jobject object) {
  if (DEBUG) LOGV("%s", __FUNCTION__);
  atomic_store(&stop_requested, 1);
  espeak_Cancel();

  return JNI_TRUE;
}

#ifdef __cplusplus
}
#endif /* __cplusplus */
