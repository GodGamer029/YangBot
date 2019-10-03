#include "framework.h"
#include <jni.h>
#include <mutex>
#include "yangbot_defs.h"
#include "rlu_headers/simulation/ball.h"
#include "rlu_headers/simulation/game.h"
#include "rlu_headers/simulation/field.h"

bool init = false;
jbyte currentMode = -1;
jbyte currentMap = -1;
std::mutex initLock;

JNIEXPORT jfloatArray JNICALL Java_yangbot_cpp_YangBotCppInterop_getSurfaceCollision(JNIEnv* env, jclass thisObj, jobject pos, jfloat sphereSize) {
	if (!init)
		return  env->NewFloatArray(0);

	static jclass jvec3 = env->FindClass("yangbot/vector/Vector3");
	static jmethodID jvec3_getContents = env->GetMethodID(jvec3, "get", "(I)F");

	vec3 position = { env->CallFloatMethod(pos, jvec3_getContents, 0), env->CallFloatMethod(pos, jvec3_getContents, 1), env->CallFloatMethod(pos, jvec3_getContents, 2) };
	
	ray contact = Field::collide(sphere{ position, sphereSize });
	ray* contactPtr = &contact;

	jfloatArray output = env->NewFloatArray(3 * 2);
	if (output == NULL)
		return NULL;
	env->SetFloatArrayRegion(output, 0, 3 * 2, reinterpret_cast<float*>(contactPtr));
	return output;
}

JNIEXPORT void JNICALL Java_yangbot_cpp_YangBotCppInterop_init(JNIEnv* env, jclass thisObj, jbyte mode, jbyte map) {
	initLock.lock();
	if (currentMode != mode || currentMap != map) {
		std::string modeS;
		switch (mode) {
		case 0:
			modeS = "soccar";
			break;
		case 1:
			modeS = "hoops";
			break;
		case 2:
			modeS = "dropshot";
			break;
		default:
			modeS = "soccar";
		}
		if (map == 37)
			modeS = "throwback";
		if (map == 36)
			modeS = "dropshot";
		if (map == 35)
			modeS = "hoops";
		Game::set_mode(modeS);
		currentMode = mode;
		currentMap = map;
	}
	
	init = true;
	initLock.unlock();
}

JNIEXPORT jfloatArray JNICALL Java_yangbot_cpp_YangBotCppInterop_simulateCarCollision(JNIEnv* env, jclass thisObj, jobject pos, jobject vel) {
	constexpr float simulationRate = 1.f / 60.f;
	constexpr float secondsSimulated = 3.f;
	constexpr int simulationSteps = (int)(secondsSimulated / simulationRate);
	if (!init)
		return env->NewFloatArray(0);

	static jclass jvec3 = env->FindClass("yangbot/vector/Vector3");
	static jmethodID jvec3_get = env->GetMethodID(jvec3, "get", "(I)F");

	vec3 position = { env->CallFloatMethod(pos, jvec3_get, 0), env->CallFloatMethod(pos, jvec3_get, 1), env->CallFloatMethod(pos, jvec3_get, 2) };
	vec3 velocity = { env->CallFloatMethod(vel, jvec3_get, 0), env->CallFloatMethod(vel, jvec3_get, 1), env->CallFloatMethod(vel, jvec3_get, 2) };
	const vec3 g = { 0, 0, -650 };

	ray contact;
	float simulationTime = 0;
	for (int i = 0; i < simulationSteps; i++) {
		simulationTime += simulationRate;
		velocity += g * simulationRate;
		position += velocity * simulationRate;
		contact = Field::collide(sphere{ position, 60 });
		if (norm(contact.direction) > 0.f) 
			break;
	}

	if (norm(contact.direction) <= 0.f) {
		contact = Field::collide(sphere{ position, 400 });
		if (norm(contact.direction) <= 0.f) {
			return env->NewFloatArray(0);
		}
	}

	jfloatArray output = env->NewFloatArray(2 * 3 + 1);
	if (output == NULL)
		return NULL;
	env->SetFloatArrayRegion(output, 0, 2 * 3, reinterpret_cast<float*>(&contact));
	env->SetFloatArrayRegion(output, 2 * 3, 1, &simulationTime);
	return output;
}

JNIEXPORT jfloatArray JNICALL Java_yangbot_cpp_YangBotCppInterop_ballstep(JNIEnv* env, jclass thisObj, jobject pos, jobject vel, jobject ang) {
	constexpr float simulationRate = 1.f / 60.f;
	constexpr float secondsSimulated = 6.f;
	constexpr int simulationSteps = (int) (secondsSimulated / simulationRate);
	if (!init) 
		return env->NewFloatArray(0);
	
	static jclass jvec3 = env->FindClass("yangbot/vector/Vector3");
	static jmethodID jvec3_getContents = env->GetMethodID(jvec3, "get", "(I)F");

	vec3 position;
	position[0] = env->CallFloatMethod(pos, jvec3_getContents, 0);
	position[1] = env->CallFloatMethod(pos, jvec3_getContents, 1);
	position[2] = env->CallFloatMethod(pos, jvec3_getContents, 2);

	vec3 velocity;
	velocity[0] = env->CallFloatMethod(vel, jvec3_getContents, 0);
	velocity[1] = env->CallFloatMethod(vel, jvec3_getContents, 1);
	velocity[2] = env->CallFloatMethod(vel, jvec3_getContents, 2);

	vec3 angular;
	angular[0] = env->CallFloatMethod(ang, jvec3_getContents, 0);
	angular[1] = env->CallFloatMethod(ang, jvec3_getContents, 1);
	angular[2] = env->CallFloatMethod(ang, jvec3_getContents, 2);

	Ball ball = Ball();
	ball.x = position;
	ball.v = velocity;
	ball.w = angular;

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