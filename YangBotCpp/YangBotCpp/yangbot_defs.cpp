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
#include "rlu_headers/experimental/navigator.h"

bool init = false;
jbyte currentMode = -1;
jbyte currentMap = -1;
std::unique_ptr<Navigator> yangNavigator;
std::mutex initLock;
std::mutex navLock;

using namespace yangbot::cpp;

extern FlatVec3 Pack(const vec3& v) {
	return FlatVec3(v[0], v[1], v[2]);
}

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

JNIEXPORT ByteBuffer __cdecl simulateBall(void* inputBall, int tickrate)
{
	const FlatPhysics* inputBallPhys = flatbuffers::GetRoot<FlatPhysics>(inputBall);

	const float simulationRate = 1.f / tickrate;
	constexpr float secondsSimulated = 3.f;
	const int simulationSteps = (int)(secondsSimulated / simulationRate);

	ByteBuffer result = ByteBuffer();
	if (!init)
		return result;

	const vec3 position = *reinterpret_cast<const vec3*>(inputBallPhys->position());
	const vec3 velocity = *reinterpret_cast<const vec3*>(inputBallPhys->velocity());
	const vec3 angular = *reinterpret_cast<const vec3*>(inputBallPhys->angularVelocity());

	Ball ball = Ball();
	ball.position = position;
	ball.velocity = velocity;
	ball.angular_velocity = angular;

	flatbuffers::FlatBufferBuilder builder(256);
	
	std::vector<flatbuffers::Offset<FlatPhysics>> physicsTicks;
	for (int i = 0; i < simulationSteps; i++) {
		FlatPhysicsBuilder physData(builder);
		auto newPos = FlatVec3(ball.position[0], ball.position[1], ball.position[2]);
		auto newVel = FlatVec3(ball.velocity[0], ball.velocity[1], ball.velocity[2]);
		auto newAng = FlatVec3(ball.angular_velocity[0], ball.angular_velocity[1], ball.angular_velocity[2]);

		physData.add_position(&newPos);
		physData.add_angularVelocity(&newAng);
		physData.add_velocity(&newVel);
		physData.add_elapsedSeconds(ball.time);

		physicsTicks.push_back(physData.Finish());

		ball.step(simulationRate);
	}

	auto frames = builder.CreateVector(physicsTicks);
	FlatPhysicsPredictionBuilder physBuild(builder);
	physBuild.add_frames(frames);
	physBuild.add_tickrate(tickrate);

	builder.Finish(physBuild.Finish());
	result.ptr = new unsigned char[builder.GetSize()];
	result.size = builder.GetSize();

	memcpy(result.ptr, builder.GetBufferPointer(), result.size);

	return result;
}

JNIEXPORT void JNICALL Java_yangbot_cpp_YangBotCppInterop_init(JNIEnv* env, jclass thisObj, jbyte mode, jbyte map) {
	initLock.lock();
	if (!init) {
		navLock.lock();

		Car* sampleCar = new Car();

		yangNavigator = std::make_unique<Navigator>(*sampleCar);

		navLock.unlock();
	}
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

ByteBuffer __cdecl simulateSimpleCar(void* inputCar, float time) {
	const FlatCarData* inputCarData = flatbuffers::GetRoot<FlatCarData>(inputCar);
	const FlatPhysics* inputCarPhysics = inputCarData->physics();

	constexpr float simulationRate = 1.f / 60.f;
	const float secondsSimulated = time;
	const int simulationSteps = (int)(secondsSimulated / simulationRate);

	ByteBuffer result = ByteBuffer();
	if (!init)
		return result;

	const vec3 position = *reinterpret_cast<const vec3*>(inputCarPhysics->position());
	const vec3 velocity = *reinterpret_cast<const vec3*>(inputCarPhysics->velocity());
	const vec3 angular = *reinterpret_cast<const vec3*>(inputCarPhysics->angularVelocity());
	const vec3 rotate = *reinterpret_cast<const vec3*>(inputCarPhysics->eulerRotation());

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
	FlatPhysicsBuilder physData(builder);

	auto newPos = FlatVec3(car.position[0], car.position[1], car.position[2]);
	auto newVel = FlatVec3(car.velocity[0], car.velocity[1], car.velocity[2]);
	auto newAng = FlatVec3(car.angular_velocity[0], car.angular_velocity[1], car.angular_velocity[2]);
	vec3 eul = rotation_to_euler(car.orientation);
	auto newEuler = FlatVec3(eul[0], eul[1], eul[2]);

	physData.add_position(&newPos);
	physData.add_angularVelocity(&newAng);
	physData.add_eulerRotation(&newEuler);
	physData.add_velocity(&newVel);
	physData.add_elapsedSeconds(simulationTime);

	auto phys = physData.Finish();

	FlatCarDataBuilder carBuild(builder);
	carBuild.add_physics(phys);
	carBuild.add_onGround(car.on_ground);

	builder.Finish(carBuild.Finish());
	result.ptr = new unsigned char[builder.GetSize()];
	result.size = builder.GetSize();

	memcpy(result.ptr, builder.GetBufferPointer(), result.size);

	return result;
}

ByteBuffer __cdecl simulateCarBallCollision(void* inputCar, void* inputBall, float dt) {

	const FlatCarData* inputCarData = flatbuffers::GetRoot<FlatCarData>(inputCar);
	const FlatPhysics* carPhysics = inputCarData->physics();
	const FlatPhysics* inputBallData = flatbuffers::GetRoot<FlatPhysics>(inputBall);

	ByteBuffer result = ByteBuffer();
	if (!init)
		return result;

	const vec3 positionCar = *reinterpret_cast<const vec3*>(carPhysics->position());
	const vec3 velocityCar = *reinterpret_cast<const vec3*>(carPhysics->velocity());
	const vec3 angularCar = *reinterpret_cast<const vec3*>(carPhysics->angularVelocity());
	const vec3 rotateCar = *reinterpret_cast<const vec3*>(carPhysics->eulerRotation());

	const vec3 positionBall = *reinterpret_cast<const vec3*>(inputBallData->position());
	const vec3 velocityBall = *reinterpret_cast<const vec3*>(inputBallData->velocity());
	const vec3 angularBall = *reinterpret_cast<const vec3*>(inputBallData->angularVelocity());

	const vec3 g = { 0, 0, -650 };

	Car car = Car();
	car.position = positionCar;
	car.velocity = velocityCar;
	car.angular_velocity = angularCar;
	car.orientation = euler_to_rotation(rotateCar);

	Ball ball = Ball();
	ball.position = positionBall;
	ball.velocity = velocityBall;
	ball.angular_velocity = angularBall;

	ball.step(dt, car);

	flatbuffers::FlatBufferBuilder builder(256);
	FlatPhysicsBuilder outputBallData(builder);

	auto newPos = FlatVec3(ball.position[0], ball.position[1], ball.position[2]);
	auto newAng = FlatVec3(ball.angular_velocity[0], ball.angular_velocity[1], ball.angular_velocity[2]);
	auto newVel = FlatVec3(ball.velocity[0], ball.velocity[1], ball.velocity[2]);

	outputBallData.add_position(&newPos);
	outputBallData.add_angularVelocity(&newAng);
	outputBallData.add_velocity(&newVel);

	builder.Finish(outputBallData.Finish());
	result.ptr = new unsigned char[builder.GetSize()];
	result.size = builder.GetSize();

	memcpy(result.ptr, builder.GetBufferPointer(), result.size);

	return result;
}

ByteBuffer __cdecl simulateCarCollision(void* inputCar){
	constexpr float simulationRate = 1.f / 60.f;
	constexpr float secondsSimulated = 3.5f;
	constexpr int simulationSteps = (int)(secondsSimulated / simulationRate);

	const FlatCarData* inputCarData = flatbuffers::GetRoot<FlatCarData>(inputCar);
	const FlatPhysics* carPhysics = inputCarData->physics();

	ByteBuffer result = ByteBuffer();
	if (!init)
		return result;

	const vec3 position = *reinterpret_cast<const vec3*>(carPhysics->position());
	const vec3 velocity = *reinterpret_cast<const vec3*>(carPhysics->velocity());
	const vec3 angular = *reinterpret_cast<const vec3*>(carPhysics->angularVelocity());
	const vec3 rotate = *reinterpret_cast<const vec3*>(carPhysics->eulerRotation());

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
		contact = Field::collide(sphere{ car.position, 18 });
		if (norm(contact.direction) > 0.f) 
			break;
	}

	flatbuffers::FlatBufferBuilder builder(256);
	FlatPhysicsBuilder carPhysicsBuilder(builder);

	auto newPos = FlatVec3(car.position[0], car.position[1], car.position[2]);
	auto newVel = FlatVec3(car.velocity[0], car.velocity[1], car.velocity[2]);
	auto newAng = FlatVec3(car.angular_velocity[0], car.angular_velocity[1], car.angular_velocity[2]);
	vec3 eul = rotation_to_euler(car.orientation);
	auto newEuler = FlatVec3(eul[0], eul[1], eul[2]);

	carPhysicsBuilder.add_position(&newPos);
	carPhysicsBuilder.add_angularVelocity(&newAng);
	carPhysicsBuilder.add_eulerRotation(&newEuler);
	carPhysicsBuilder.add_velocity(&newVel);
	carPhysicsBuilder.add_elapsedSeconds(simulationTime);

	auto physics = carPhysicsBuilder.Finish();

	FlatCarDataBuilder carBuilder(builder);

	carBuilder.add_onGround(car.on_ground);
	carBuilder.add_physics(physics);
	
	auto flatCar = carBuilder.Finish();

	FlatCarCollisionInfoBuilder carCollision(builder);
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


JNIEXPORT jfloatArray JNICALL Java_yangbot_cpp_YangBotCppInterop_aerialML(JNIEnv* env, jclass thisObj, jobject orientEulerV, jobject angularVelV, jobject targetOrientEulerV, jfloat dt)
{
	if (!init)
		return env->NewFloatArray(0);

	static jclass jvec3 = env->FindClass("yangbot/util/math/vector/Vector3");
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

JNIEXPORT ByteBuffer __cdecl findPath(void* pathRequest) {
	const FlatNavigatorRequest* navRequest = flatbuffers::GetRoot<FlatNavigatorRequest>(pathRequest);
	ByteBuffer result = ByteBuffer();
	if (!init)
		return result;

	const vec3 startPosition = *reinterpret_cast<const vec3*>(navRequest->startPosition());
	const vec3 startTangent = *reinterpret_cast<const vec3*>(navRequest->startTangent());

	flatbuffers::FlatBufferBuilder builder(256);
	
	{
		std::scoped_lock<std::mutex> lock(navLock);
		auto nav = yangNavigator.get();
		// Only analyze surroundings if positions are different
		if (norm(nav->car.position - startPosition) > 0.5 || norm(nav->car.forward() - startTangent) > 0.005f) {
			//std::cout << norm(nav->car.position - startPosition) << ":" << norm(nav->car.forward() - startTangent) << std::endl;
			nav->car.position = startPosition;
			nav->car.orientation(0, 0) = startTangent[0];
			nav->car.orientation(1, 0) = startTangent[1];
			nav->car.orientation(2, 0) = startTangent[2];
			nav->analyze_surroundings(1337.f);
		}
		
		const vec3 endPosition = *reinterpret_cast<const vec3*>(navRequest->endPosition());
		const vec3 endTangent = *reinterpret_cast<const vec3*>(navRequest->endTangent());
		const float endTangentMultiplier = navRequest->endTangentMultiplier();

		Curve c;
		{
			c = nav->path_to(endPosition, endTangent, endTangentMultiplier);

			auto curvatures = builder.CreateVector(c.curvatures);
			auto distances = builder.CreateVector(c.distances);
			auto points = builder.CreateVectorOfNativeStructs<FlatVec3, vec3>(c.points);
			auto tangents = builder.CreateVectorOfNativeStructs<FlatVec3, vec3>(c.tangents);

			FlatCurveBuilder curveBuilder(builder);
			curveBuilder.add_length(c.length);
			curveBuilder.add_curvatures(curvatures);
			curveBuilder.add_distances(distances);
			curveBuilder.add_points(points);
			curveBuilder.add_tangents(tangents);
			
			builder.Finish(curveBuilder.Finish());
			result.ptr = new unsigned char[builder.GetSize()];
			result.size = builder.GetSize();

			memcpy(result.ptr, builder.GetBufferPointer(), result.size);

			return result;
		}
	}
}

JNIEXPORT void JNICALL Free(void* ptr) {
	free(ptr);
}