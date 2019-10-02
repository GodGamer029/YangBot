#include "framework.h"
#include <jni.h>
#include "yangbot_defs.h"
#include "rlu_headers/simulation/ball.h"
#include "rlu_headers/simulation/game.h"

bool init = false;

JNIEXPORT jfloat JNICALL Java_yangbot_cpp_YangBotCppInterop_hello(JNIEnv* env, jclass thisObj) {
	
	return 13.5f;
}

JNIEXPORT jfloatArray JNICALL Java_yangbot_cpp_YangBotCppInterop_ballstep(JNIEnv* env, jclass thisObj, jobject pos, jobject vel) {
	constexpr float simulationRate = 1.f / 60.f;
	constexpr float secondsSimulated = 6.f;
	constexpr int simulationSteps = (int) (secondsSimulated / simulationRate);
	if (!init) {
		init = true;
		Game::set_mode("soccar");
	}

	jclass jvec3 = env->FindClass("yangbot/vector/Vector3");
	jmethodID jvec3_getContents = env->GetMethodID(jvec3, "get", "(I)F");
	if (jvec3_getContents == NULL)
		return NULL;

	vec3 position;
	position[0] = env->CallFloatMethod(pos, jvec3_getContents, 0);
	position[1] = env->CallFloatMethod(pos, jvec3_getContents, 1);
	position[2] = env->CallFloatMethod(pos, jvec3_getContents, 2);

	vec3 velocity;
	velocity[0] = env->CallFloatMethod(vel, jvec3_getContents, 0);
	velocity[1] = env->CallFloatMethod(vel, jvec3_getContents, 1);
	velocity[2] = env->CallFloatMethod(vel, jvec3_getContents, 2);

	Ball ball = Ball();
	ball.x = position;
	ball.v = velocity;

	jfloat simulationResults[3 * (simulationSteps) + 1];

	for (int i = 0; i < simulationSteps; i++) {
		ball.step(simulationRate);
		simulationResults[i * 3 + 0] = ball.x[0];
		simulationResults[i * 3 + 1] = ball.x[1];
		simulationResults[i * 3 + 2] = ball.x[2];
	}

	jfloatArray output = env->NewFloatArray(3 * (simulationSteps));
	if (output == NULL)
		return NULL;
	env->SetFloatArrayRegion(output, 0, 3 * (simulationSteps), simulationResults);
	return output;
}