#include "framework.h"
#include <jni.h>
#include <mutex>
#include "yangbot_defs.h"
#include "yangbot_generated.h"
#include "rlu_headers/simulation/ball.h"
#include "rlu_headers/simulation/game.h"
#include "rlu_headers/simulation/field.h"
#include "rlu_headers/mechanics/reorient_ML.h"
#include "rlu_headers/mechanics/reorient.h"

bool init = false;
jbyte currentMode = -1;
jbyte currentMap = -1;
std::mutex initLock;

using namespace yangbot::cpp;

JNIEXPORT jfloatArray JNICALL Java_yangbot_cpp_YangBotCppInterop_getSurfaceCollision(JNIEnv* env, jclass thisObj, jobject pos, jfloat sphereSize) {
	if (!init)
		return env->NewFloatArray(0);

	static jclass jvec3 = env->FindClass("yangbot/vector/Vector3");
	static jmethodID jvec3_get = env->GetMethodID(jvec3, "get", "(I)F");

	vec3 position = { env->CallFloatMethod(pos, jvec3_get, 0), env->CallFloatMethod(pos, jvec3_get, 1), env->CallFloatMethod(pos, jvec3_get, 2) };
	
	ray contact = Field::collide(sphere{ position, sphereSize });

	jfloatArray output = env->NewFloatArray(3 * 2);
	if (output == NULL)
		return NULL;
	env->SetFloatArrayRegion(output, 0, 3 * 2, reinterpret_cast<float*>(&contact));
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


ByteBuffer __cdecl simulateSimpleCar(void* inputCar, int protoSize) {
	const FBSCarData* inputCarData = flatbuffers::GetRoot<FBSCarData>(inputCar);

	constexpr float simulationRate = 1.f / 60.f;
	const float secondsSimulated = inputCarData->elapsedSeconds();
	const int simulationSteps = (int)(secondsSimulated / simulationRate);

	ByteBuffer result = ByteBuffer();
	if (!init)
		return result;

	const vec3 position = *reinterpret_cast<const vec3*>(inputCarData->position());
	const vec3 velocity = *reinterpret_cast<const vec3*>(inputCarData->velocity());
	const vec3 angular = *reinterpret_cast<const vec3*>(inputCarData->angularVelocity());
	const vec3 rotate = *reinterpret_cast<const vec3*>(inputCarData->eulerRotation());

	const vec3 g = { 0, 0, -650 };

	float simulationTime = 0;
	Car car = Car();
	car.position = position;
	car.velocity = velocity;
	car.angular_velocity = angular;
	car.orientation = euler_to_rotation(rotate);
	car.on_ground = inputCarData->onGround();

	for (int i = 0; i < simulationSteps; i++) {
		simulationTime += simulationRate;
		car.step(Input(), simulationRate);
	}

	flatbuffers::FlatBufferBuilder builder(256);
	FBSCarDataBuilder carData(builder);

	auto newPos = FlatVec3(car.position[0], car.position[1], car.position[2]);
	auto newVel = FlatVec3(car.velocity[0], car.velocity[1], car.velocity[2]);
	auto newAng = FlatVec3(car.angular_velocity[0], car.angular_velocity[1], car.angular_velocity[2]);
	vec3 eul = rotation_to_euler(car.orientation);
	auto newEuler = FlatVec3(eul[0], eul[1], eul[2]);

	carData.add_position(&newPos);
	carData.add_angularVelocity(&newAng);
	carData.add_eulerRotation(&newEuler);
	carData.add_velocity(&newVel);
	carData.add_onGround(car.on_ground);
	carData.add_elapsedSeconds(simulationTime);

	builder.Finish(carData.Finish());
	result.ptr = new unsigned char[builder.GetSize()];
	result.size = builder.GetSize();

	memcpy(result.ptr, builder.GetBufferPointer(), result.size);

	return result;
}

ByteBuffer __cdecl simulateCarCollision(void* inputCar, int protoSize){
	constexpr float simulationRate = 1.f / 60.f;
	constexpr float secondsSimulated = 3.5f;
	constexpr int simulationSteps = (int)(secondsSimulated / simulationRate);

	const FBSCarData* inputCarData = flatbuffers::GetRoot<FBSCarData>(inputCar);

	ByteBuffer result = ByteBuffer();
	if (!init)
		return result;

	const vec3 position = *reinterpret_cast<const vec3*>(inputCarData->position());
	const vec3 velocity = *reinterpret_cast<const vec3*>(inputCarData->velocity());
	const vec3 angular = *reinterpret_cast<const vec3*>(inputCarData->angularVelocity());
	const vec3 rotate = *reinterpret_cast<const vec3*>(inputCarData->eulerRotation());

	const vec3 g = { 0, 0, -650 };

	ray contact;
	float simulationTime = 0;
	Car car = Car();
	car.position = position;
	car.velocity = velocity;
	car.angular_velocity = angular;
	car.orientation = euler_to_rotation(rotate);
	car.on_ground = false;

	for (int i = 0; i < simulationSteps; i++) {
		simulationTime += simulationRate;
		car.step(Input(), simulationRate);
		contact = Field::collide(sphere{ car.position, 60 });
		if (norm(contact.direction) > 0.f) 
			break;
	}

	flatbuffers::FlatBufferBuilder builder(256);
	FBSCarDataBuilder carData(builder);

	auto newPos = FlatVec3(car.position[0], car.position[1], car.position[2]);
	auto newVel = FlatVec3(car.velocity[0], car.velocity[1], car.velocity[2]);
	auto newAng = FlatVec3(car.angular_velocity[0], car.angular_velocity[1], car.angular_velocity[2]);
	vec3 eul = rotation_to_euler(car.orientation);
	auto newEuler = FlatVec3(eul[0], eul[1], eul[2]);

	carData.add_position(&newPos);
	carData.add_angularVelocity(&newAng);
	carData.add_eulerRotation(&newEuler);
	carData.add_velocity(&newVel);
	carData.add_onGround(car.on_ground);
	carData.add_elapsedSeconds(simulationTime);
	auto flatCar = carData.Finish();

	CarCollisionInfoBuilder carCollision(builder);
	carCollision.add_carData(flatCar);

	if (norm(contact.direction) <= 0.f) 
		contact = Field::collide(sphere{ car.position, 400 });

	auto fStart = FlatVec3(contact.start[0], contact.start[1], contact.start[2]);
	auto fDirection = FlatVec3(contact.direction[0], contact.direction[1], contact.direction[2]);
	FlatRay flatRay = FlatRay(fStart, fDirection);
	carCollision.add_impact(&flatRay);
	auto carCollisionResult = carCollision.Finish();
	builder.Finish(carCollisionResult);
	result.ptr = new unsigned char[builder.GetSize()];
	result.size = builder.GetSize();
	
	memcpy(result.ptr, builder.GetBufferPointer(), result.size);

	return result;
}

JNIEXPORT jfloatArray JNICALL Java_yangbot_cpp_YangBotCppInterop_ballstep(JNIEnv* env, jclass thisObj, jobject pos, jobject vel, jobject ang) {
	constexpr float simulationRate = 1.f / 60.f;
	constexpr float secondsSimulated = 6.f;
	constexpr int simulationSteps = (int) (secondsSimulated / simulationRate);
	if (!init) 
		return env->NewFloatArray(0);
	
	static jclass jvec3 = env->FindClass("yangbot/vector/Vector3");
	static jmethodID jvec3_get = env->GetMethodID(jvec3, "get", "(I)F");

	vec3 position = { env->CallFloatMethod(pos, jvec3_get, 0), env->CallFloatMethod(pos, jvec3_get, 1), env->CallFloatMethod(pos, jvec3_get, 2) };
	vec3 velocity = { env->CallFloatMethod(vel, jvec3_get, 0), env->CallFloatMethod(vel, jvec3_get, 1), env->CallFloatMethod(vel, jvec3_get, 2) };
	vec3 angular  = { env->CallFloatMethod(ang, jvec3_get, 0), env->CallFloatMethod(ang, jvec3_get, 1), env->CallFloatMethod(ang, jvec3_get, 2) };

	Ball ball = Ball();
	ball.position = position;
	ball.velocity = velocity;
	ball.angular_velocity = angular;

	jfloat simulationResults[3 * (simulationSteps) + 1];

	for (int i = 0; i < simulationSteps; i++) {
		ball.step(simulationRate);
		simulationResults[i * 3 + 0] = ball.position[0];
		simulationResults[i * 3 + 1] = ball.position[1];
		simulationResults[i * 3 + 2] = ball.position[2];
	}

	jfloatArray output = env->NewFloatArray(3 * (simulationSteps));
	if (output == NULL)
		return NULL;
	env->SetFloatArrayRegion(output, 0, 3 * (simulationSteps), simulationResults);
	return output;
}

JNIEXPORT jfloatArray JNICALL Java_yangbot_cpp_YangBotCppInterop_aerialML(JNIEnv* env, jclass thisObj, jobject orientEulerV, jobject angularVelV, jobject targetOrientEulerV, jfloat dt)
{
	if (!init)
		return env->NewFloatArray(0);

	static jclass jvec3 = env->FindClass("yangbot/vector/Vector3");
	static jmethodID jvec3_get = env->GetMethodID(jvec3, "get", "(I)F");

	vec3 orientEuler = { env->CallFloatMethod(orientEulerV, jvec3_get, 0), env->CallFloatMethod(orientEulerV, jvec3_get, 1), env->CallFloatMethod(orientEulerV, jvec3_get, 2) };
	vec3 angular = { env->CallFloatMethod(angularVelV, jvec3_get, 0), env->CallFloatMethod(angularVelV, jvec3_get, 1), env->CallFloatMethod(angularVelV, jvec3_get, 2) };
	vec3 targetOrientEuler = { env->CallFloatMethod(targetOrientEulerV, jvec3_get, 0), env->CallFloatMethod(targetOrientEulerV, jvec3_get, 1), env->CallFloatMethod(targetOrientEulerV, jvec3_get, 2) };

	mat3 orient = euler_to_rotation(orientEuler);
	mat3 targetOrient = euler_to_rotation(targetOrientEuler);

	Car car = Car();
	car.orientation = orient;
	car.angular_velocity = angular;
	Reorient reorientML(car);
	reorientML.target_orientation = targetOrient;
	reorientML.eps_phi = 0.01f;
	//reorientML.eps_omega = 0.02f;
	reorientML.step(dt);

	vec3 cOutput = { reorientML.controls.roll, reorientML.controls.pitch, reorientML.controls.yaw };

	jfloatArray output = env->NewFloatArray(3);
	if (output == NULL)
		return NULL;
	env->SetFloatArrayRegion(output, 0, 3, reinterpret_cast<float*>(&cOutput));
	return output;
}

JNIEXPORT void JNICALL Free(void* ptr) {
	free(ptr);
}