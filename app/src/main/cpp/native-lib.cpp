#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "NativeTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_cameramqttapp_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("Native code is running!");
    return env->NewStringUTF("Hello from NDK");
}