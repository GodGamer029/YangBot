#include "framework.h"
#include <jni.h>
#include "yangbot_defs.h"


JNIEXPORT jfloat JNICALL Java_yangbot_cpp_YangBotCppInterop_hello(JNIEnv* env, jclass thisObj) {
	Sleep(10000);
	return 13.5f;
}