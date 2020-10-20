// automatically generated by the FlatBuffers compiler, do not modify


#ifndef FLATBUFFERS_GENERATED_YANGBOT_YANGBOT_CPP_H_
#define FLATBUFFERS_GENERATED_YANGBOT_YANGBOT_CPP_H_

#include "flatbuffers/flatbuffers.h"

namespace yangbot {
namespace cpp {

struct FlatVec3;

struct FlatPhysics;

struct FlatCarData;

struct FlatPhysicsPrediction;

struct FlatRay;

struct FlatCarCollisionInfo;

struct FlatControlPoint;

struct FlatNavigatorRequest;

struct FlatCurve;

MANUALLY_ALIGNED_STRUCT(4) FlatVec3 FLATBUFFERS_FINAL_CLASS {
 private:
  float x_;
  float y_;
  float z_;

 public:
  FlatVec3() {
    memset(this, 0, sizeof(FlatVec3));
  }
  FlatVec3(float _x, float _y, float _z)
      : x_(flatbuffers::EndianScalar(_x)),
        y_(flatbuffers::EndianScalar(_y)),
        z_(flatbuffers::EndianScalar(_z)) {
  }
  float x() const {
    return flatbuffers::EndianScalar(x_);
  }
  float y() const {
    return flatbuffers::EndianScalar(y_);
  }
  float z() const {
    return flatbuffers::EndianScalar(z_);
  }
};
STRUCT_END(FlatVec3, 12);

MANUALLY_ALIGNED_STRUCT(4) FlatRay FLATBUFFERS_FINAL_CLASS {
 private:
  FlatVec3 start_;
  FlatVec3 direction_;

 public:
  FlatRay() {
    memset(this, 0, sizeof(FlatRay));
  }
  FlatRay(const FlatVec3 &_start, const FlatVec3 &_direction)
      : start_(_start),
        direction_(_direction) {
  }
  const FlatVec3 &start() const {
    return start_;
  }
  const FlatVec3 &direction() const {
    return direction_;
  }
};
STRUCT_END(FlatRay, 24);

MANUALLY_ALIGNED_STRUCT(4) FlatControlPoint FLATBUFFERS_FINAL_CLASS {
 private:
  FlatVec3 position_;
  FlatVec3 normal_;
  FlatVec3 tangent_;

 public:
  FlatControlPoint() {
    memset(this, 0, sizeof(FlatControlPoint));
  }
  FlatControlPoint(const FlatVec3 &_position, const FlatVec3 &_normal, const FlatVec3 &_tangent)
      : position_(_position),
        normal_(_normal),
        tangent_(_tangent) {
  }
  const FlatVec3 &position() const {
    return position_;
  }
  const FlatVec3 &normal() const {
    return normal_;
  }
  const FlatVec3 &tangent() const {
    return tangent_;
  }
};
STRUCT_END(FlatControlPoint, 36);

struct FlatPhysics FLATBUFFERS_FINAL_CLASS : private flatbuffers::Table {
  enum {
    VT_POSITION = 4,
    VT_VELOCITY = 6,
    VT_ANGULARVELOCITY = 8,
    VT_EULERROTATION = 10,
    VT_ELAPSEDSECONDS = 12
  };
  const FlatVec3 *position() const {
    return GetStruct<const FlatVec3 *>(VT_POSITION);
  }
  const FlatVec3 *velocity() const {
    return GetStruct<const FlatVec3 *>(VT_VELOCITY);
  }
  const FlatVec3 *angularVelocity() const {
    return GetStruct<const FlatVec3 *>(VT_ANGULARVELOCITY);
  }
  const FlatVec3 *eulerRotation() const {
    return GetStruct<const FlatVec3 *>(VT_EULERROTATION);
  }
  float elapsedSeconds() const {
    return GetField<float>(VT_ELAPSEDSECONDS, 0.0f);
  }
  bool Verify(flatbuffers::Verifier &verifier) const {
    return VerifyTableStart(verifier) &&
           VerifyField<FlatVec3>(verifier, VT_POSITION) &&
           VerifyField<FlatVec3>(verifier, VT_VELOCITY) &&
           VerifyField<FlatVec3>(verifier, VT_ANGULARVELOCITY) &&
           VerifyField<FlatVec3>(verifier, VT_EULERROTATION) &&
           VerifyField<float>(verifier, VT_ELAPSEDSECONDS) &&
           verifier.EndTable();
  }
};

struct FlatPhysicsBuilder {
  flatbuffers::FlatBufferBuilder &fbb_;
  flatbuffers::uoffset_t start_;
  void add_position(const FlatVec3 *position) {
    fbb_.AddStruct(FlatPhysics::VT_POSITION, position);
  }
  void add_velocity(const FlatVec3 *velocity) {
    fbb_.AddStruct(FlatPhysics::VT_VELOCITY, velocity);
  }
  void add_angularVelocity(const FlatVec3 *angularVelocity) {
    fbb_.AddStruct(FlatPhysics::VT_ANGULARVELOCITY, angularVelocity);
  }
  void add_eulerRotation(const FlatVec3 *eulerRotation) {
    fbb_.AddStruct(FlatPhysics::VT_EULERROTATION, eulerRotation);
  }
  void add_elapsedSeconds(float elapsedSeconds) {
    fbb_.AddElement<float>(FlatPhysics::VT_ELAPSEDSECONDS, elapsedSeconds, 0.0f);
  }
  explicit FlatPhysicsBuilder(flatbuffers::FlatBufferBuilder &_fbb)
        : fbb_(_fbb) {
    start_ = fbb_.StartTable();
  }
  FlatPhysicsBuilder &operator=(const FlatPhysicsBuilder &);
  flatbuffers::Offset<FlatPhysics> Finish() {
    const auto end = fbb_.EndTable(start_);
    auto o = flatbuffers::Offset<FlatPhysics>(end);
    return o;
  }
};

inline flatbuffers::Offset<FlatPhysics> CreateFlatPhysics(
    flatbuffers::FlatBufferBuilder &_fbb,
    const FlatVec3 *position = 0,
    const FlatVec3 *velocity = 0,
    const FlatVec3 *angularVelocity = 0,
    const FlatVec3 *eulerRotation = 0,
    float elapsedSeconds = 0.0f) {
  FlatPhysicsBuilder builder_(_fbb);
  builder_.add_elapsedSeconds(elapsedSeconds);
  builder_.add_eulerRotation(eulerRotation);
  builder_.add_angularVelocity(angularVelocity);
  builder_.add_velocity(velocity);
  builder_.add_position(position);
  return builder_.Finish();
}

struct FlatCarData FLATBUFFERS_FINAL_CLASS : private flatbuffers::Table {
  enum {
    VT_PHYSICS = 4,
    VT_ONGROUND = 6
  };
  const FlatPhysics *physics() const {
    return GetPointer<const FlatPhysics *>(VT_PHYSICS);
  }
  bool onGround() const {
    return GetField<uint8_t>(VT_ONGROUND, 0) != 0;
  }
  bool Verify(flatbuffers::Verifier &verifier) const {
    return VerifyTableStart(verifier) &&
           VerifyOffset(verifier, VT_PHYSICS) &&
           verifier.VerifyTable(physics()) &&
           VerifyField<uint8_t>(verifier, VT_ONGROUND) &&
           verifier.EndTable();
  }
};

struct FlatCarDataBuilder {
  flatbuffers::FlatBufferBuilder &fbb_;
  flatbuffers::uoffset_t start_;
  void add_physics(flatbuffers::Offset<FlatPhysics> physics) {
    fbb_.AddOffset(FlatCarData::VT_PHYSICS, physics);
  }
  void add_onGround(bool onGround) {
    fbb_.AddElement<uint8_t>(FlatCarData::VT_ONGROUND, static_cast<uint8_t>(onGround), 0);
  }
  explicit FlatCarDataBuilder(flatbuffers::FlatBufferBuilder &_fbb)
        : fbb_(_fbb) {
    start_ = fbb_.StartTable();
  }
  FlatCarDataBuilder &operator=(const FlatCarDataBuilder &);
  flatbuffers::Offset<FlatCarData> Finish() {
    const auto end = fbb_.EndTable(start_);
    auto o = flatbuffers::Offset<FlatCarData>(end);
    return o;
  }
};

inline flatbuffers::Offset<FlatCarData> CreateFlatCarData(
    flatbuffers::FlatBufferBuilder &_fbb,
    flatbuffers::Offset<FlatPhysics> physics = 0,
    bool onGround = false) {
  FlatCarDataBuilder builder_(_fbb);
  builder_.add_physics(physics);
  builder_.add_onGround(onGround);
  return builder_.Finish();
}

struct FlatPhysicsPrediction FLATBUFFERS_FINAL_CLASS : private flatbuffers::Table {
  enum {
    VT_FRAMES = 4,
    VT_TICKRATE = 6
  };
  const flatbuffers::Vector<flatbuffers::Offset<FlatPhysics>> *frames() const {
    return GetPointer<const flatbuffers::Vector<flatbuffers::Offset<FlatPhysics>> *>(VT_FRAMES);
  }
  int32_t tickrate() const {
    return GetField<int32_t>(VT_TICKRATE, 0);
  }
  bool Verify(flatbuffers::Verifier &verifier) const {
    return VerifyTableStart(verifier) &&
           VerifyOffset(verifier, VT_FRAMES) &&
           verifier.Verify(frames()) &&
           verifier.VerifyVectorOfTables(frames()) &&
           VerifyField<int32_t>(verifier, VT_TICKRATE) &&
           verifier.EndTable();
  }
};

struct FlatPhysicsPredictionBuilder {
  flatbuffers::FlatBufferBuilder &fbb_;
  flatbuffers::uoffset_t start_;
  void add_frames(flatbuffers::Offset<flatbuffers::Vector<flatbuffers::Offset<FlatPhysics>>> frames) {
    fbb_.AddOffset(FlatPhysicsPrediction::VT_FRAMES, frames);
  }
  void add_tickrate(int32_t tickrate) {
    fbb_.AddElement<int32_t>(FlatPhysicsPrediction::VT_TICKRATE, tickrate, 0);
  }
  explicit FlatPhysicsPredictionBuilder(flatbuffers::FlatBufferBuilder &_fbb)
        : fbb_(_fbb) {
    start_ = fbb_.StartTable();
  }
  FlatPhysicsPredictionBuilder &operator=(const FlatPhysicsPredictionBuilder &);
  flatbuffers::Offset<FlatPhysicsPrediction> Finish() {
    const auto end = fbb_.EndTable(start_);
    auto o = flatbuffers::Offset<FlatPhysicsPrediction>(end);
    return o;
  }
};

inline flatbuffers::Offset<FlatPhysicsPrediction> CreateFlatPhysicsPrediction(
    flatbuffers::FlatBufferBuilder &_fbb,
    flatbuffers::Offset<flatbuffers::Vector<flatbuffers::Offset<FlatPhysics>>> frames = 0,
    int32_t tickrate = 0) {
  FlatPhysicsPredictionBuilder builder_(_fbb);
  builder_.add_tickrate(tickrate);
  builder_.add_frames(frames);
  return builder_.Finish();
}

inline flatbuffers::Offset<FlatPhysicsPrediction> CreateFlatPhysicsPredictionDirect(
    flatbuffers::FlatBufferBuilder &_fbb,
    const std::vector<flatbuffers::Offset<FlatPhysics>> *frames = nullptr,
    int32_t tickrate = 0) {
  return yangbot::cpp::CreateFlatPhysicsPrediction(
      _fbb,
      frames ? _fbb.CreateVector<flatbuffers::Offset<FlatPhysics>>(*frames) : 0,
      tickrate);
}

struct FlatCarCollisionInfo FLATBUFFERS_FINAL_CLASS : private flatbuffers::Table {
  enum {
    VT_CARDATA = 4,
    VT_IMPACT = 6
  };
  const FlatCarData *carData() const {
    return GetPointer<const FlatCarData *>(VT_CARDATA);
  }
  const FlatRay *impact() const {
    return GetStruct<const FlatRay *>(VT_IMPACT);
  }
  bool Verify(flatbuffers::Verifier &verifier) const {
    return VerifyTableStart(verifier) &&
           VerifyOffset(verifier, VT_CARDATA) &&
           verifier.VerifyTable(carData()) &&
           VerifyField<FlatRay>(verifier, VT_IMPACT) &&
           verifier.EndTable();
  }
};

struct FlatCarCollisionInfoBuilder {
  flatbuffers::FlatBufferBuilder &fbb_;
  flatbuffers::uoffset_t start_;
  void add_carData(flatbuffers::Offset<FlatCarData> carData) {
    fbb_.AddOffset(FlatCarCollisionInfo::VT_CARDATA, carData);
  }
  void add_impact(const FlatRay *impact) {
    fbb_.AddStruct(FlatCarCollisionInfo::VT_IMPACT, impact);
  }
  explicit FlatCarCollisionInfoBuilder(flatbuffers::FlatBufferBuilder &_fbb)
        : fbb_(_fbb) {
    start_ = fbb_.StartTable();
  }
  FlatCarCollisionInfoBuilder &operator=(const FlatCarCollisionInfoBuilder &);
  flatbuffers::Offset<FlatCarCollisionInfo> Finish() {
    const auto end = fbb_.EndTable(start_);
    auto o = flatbuffers::Offset<FlatCarCollisionInfo>(end);
    return o;
  }
};

inline flatbuffers::Offset<FlatCarCollisionInfo> CreateFlatCarCollisionInfo(
    flatbuffers::FlatBufferBuilder &_fbb,
    flatbuffers::Offset<FlatCarData> carData = 0,
    const FlatRay *impact = 0) {
  FlatCarCollisionInfoBuilder builder_(_fbb);
  builder_.add_impact(impact);
  builder_.add_carData(carData);
  return builder_.Finish();
}

struct FlatNavigatorRequest FLATBUFFERS_FINAL_CLASS : private flatbuffers::Table {
  enum {
    VT_STARTPOSITION = 4,
    VT_STARTTANGENT = 6,
    VT_ENDPOSITION = 8,
    VT_ENDTANGENT = 10,
    VT_ENDTANGENTMULTIPLIER = 12
  };
  const FlatVec3 *startPosition() const {
    return GetStruct<const FlatVec3 *>(VT_STARTPOSITION);
  }
  const FlatVec3 *startTangent() const {
    return GetStruct<const FlatVec3 *>(VT_STARTTANGENT);
  }
  const FlatVec3 *endPosition() const {
    return GetStruct<const FlatVec3 *>(VT_ENDPOSITION);
  }
  const FlatVec3 *endTangent() const {
    return GetStruct<const FlatVec3 *>(VT_ENDTANGENT);
  }
  float endTangentMultiplier() const {
    return GetField<float>(VT_ENDTANGENTMULTIPLIER, 0.0f);
  }
  bool Verify(flatbuffers::Verifier &verifier) const {
    return VerifyTableStart(verifier) &&
           VerifyField<FlatVec3>(verifier, VT_STARTPOSITION) &&
           VerifyField<FlatVec3>(verifier, VT_STARTTANGENT) &&
           VerifyField<FlatVec3>(verifier, VT_ENDPOSITION) &&
           VerifyField<FlatVec3>(verifier, VT_ENDTANGENT) &&
           VerifyField<float>(verifier, VT_ENDTANGENTMULTIPLIER) &&
           verifier.EndTable();
  }
};

struct FlatNavigatorRequestBuilder {
  flatbuffers::FlatBufferBuilder &fbb_;
  flatbuffers::uoffset_t start_;
  void add_startPosition(const FlatVec3 *startPosition) {
    fbb_.AddStruct(FlatNavigatorRequest::VT_STARTPOSITION, startPosition);
  }
  void add_startTangent(const FlatVec3 *startTangent) {
    fbb_.AddStruct(FlatNavigatorRequest::VT_STARTTANGENT, startTangent);
  }
  void add_endPosition(const FlatVec3 *endPosition) {
    fbb_.AddStruct(FlatNavigatorRequest::VT_ENDPOSITION, endPosition);
  }
  void add_endTangent(const FlatVec3 *endTangent) {
    fbb_.AddStruct(FlatNavigatorRequest::VT_ENDTANGENT, endTangent);
  }
  void add_endTangentMultiplier(float endTangentMultiplier) {
    fbb_.AddElement<float>(FlatNavigatorRequest::VT_ENDTANGENTMULTIPLIER, endTangentMultiplier, 0.0f);
  }
  explicit FlatNavigatorRequestBuilder(flatbuffers::FlatBufferBuilder &_fbb)
        : fbb_(_fbb) {
    start_ = fbb_.StartTable();
  }
  FlatNavigatorRequestBuilder &operator=(const FlatNavigatorRequestBuilder &);
  flatbuffers::Offset<FlatNavigatorRequest> Finish() {
    const auto end = fbb_.EndTable(start_);
    auto o = flatbuffers::Offset<FlatNavigatorRequest>(end);
    return o;
  }
};

inline flatbuffers::Offset<FlatNavigatorRequest> CreateFlatNavigatorRequest(
    flatbuffers::FlatBufferBuilder &_fbb,
    const FlatVec3 *startPosition = 0,
    const FlatVec3 *startTangent = 0,
    const FlatVec3 *endPosition = 0,
    const FlatVec3 *endTangent = 0,
    float endTangentMultiplier = 0.0f) {
  FlatNavigatorRequestBuilder builder_(_fbb);
  builder_.add_endTangentMultiplier(endTangentMultiplier);
  builder_.add_endTangent(endTangent);
  builder_.add_endPosition(endPosition);
  builder_.add_startTangent(startTangent);
  builder_.add_startPosition(startPosition);
  return builder_.Finish();
}

struct FlatCurve FLATBUFFERS_FINAL_CLASS : private flatbuffers::Table {
  enum {
    VT_LENGTH = 4,
    VT_POINTS = 6,
    VT_TANGENTS = 8,
    VT_DISTANCES = 10,
    VT_CURVATURES = 12
  };
  float length() const {
    return GetField<float>(VT_LENGTH, 0.0f);
  }
  const flatbuffers::Vector<const FlatVec3 *> *points() const {
    return GetPointer<const flatbuffers::Vector<const FlatVec3 *> *>(VT_POINTS);
  }
  const flatbuffers::Vector<const FlatVec3 *> *tangents() const {
    return GetPointer<const flatbuffers::Vector<const FlatVec3 *> *>(VT_TANGENTS);
  }
  const flatbuffers::Vector<float> *distances() const {
    return GetPointer<const flatbuffers::Vector<float> *>(VT_DISTANCES);
  }
  const flatbuffers::Vector<float> *curvatures() const {
    return GetPointer<const flatbuffers::Vector<float> *>(VT_CURVATURES);
  }
  bool Verify(flatbuffers::Verifier &verifier) const {
    return VerifyTableStart(verifier) &&
           VerifyField<float>(verifier, VT_LENGTH) &&
           VerifyOffset(verifier, VT_POINTS) &&
           verifier.Verify(points()) &&
           VerifyOffset(verifier, VT_TANGENTS) &&
           verifier.Verify(tangents()) &&
           VerifyOffset(verifier, VT_DISTANCES) &&
           verifier.Verify(distances()) &&
           VerifyOffset(verifier, VT_CURVATURES) &&
           verifier.Verify(curvatures()) &&
           verifier.EndTable();
  }
};

struct FlatCurveBuilder {
  flatbuffers::FlatBufferBuilder &fbb_;
  flatbuffers::uoffset_t start_;
  void add_length(float length) {
    fbb_.AddElement<float>(FlatCurve::VT_LENGTH, length, 0.0f);
  }
  void add_points(flatbuffers::Offset<flatbuffers::Vector<const FlatVec3 *>> points) {
    fbb_.AddOffset(FlatCurve::VT_POINTS, points);
  }
  void add_tangents(flatbuffers::Offset<flatbuffers::Vector<const FlatVec3 *>> tangents) {
    fbb_.AddOffset(FlatCurve::VT_TANGENTS, tangents);
  }
  void add_distances(flatbuffers::Offset<flatbuffers::Vector<float>> distances) {
    fbb_.AddOffset(FlatCurve::VT_DISTANCES, distances);
  }
  void add_curvatures(flatbuffers::Offset<flatbuffers::Vector<float>> curvatures) {
    fbb_.AddOffset(FlatCurve::VT_CURVATURES, curvatures);
  }
  explicit FlatCurveBuilder(flatbuffers::FlatBufferBuilder &_fbb)
        : fbb_(_fbb) {
    start_ = fbb_.StartTable();
  }
  FlatCurveBuilder &operator=(const FlatCurveBuilder &);
  flatbuffers::Offset<FlatCurve> Finish() {
    const auto end = fbb_.EndTable(start_);
    auto o = flatbuffers::Offset<FlatCurve>(end);
    return o;
  }
};

inline flatbuffers::Offset<FlatCurve> CreateFlatCurve(
    flatbuffers::FlatBufferBuilder &_fbb,
    float length = 0.0f,
    flatbuffers::Offset<flatbuffers::Vector<const FlatVec3 *>> points = 0,
    flatbuffers::Offset<flatbuffers::Vector<const FlatVec3 *>> tangents = 0,
    flatbuffers::Offset<flatbuffers::Vector<float>> distances = 0,
    flatbuffers::Offset<flatbuffers::Vector<float>> curvatures = 0) {
  FlatCurveBuilder builder_(_fbb);
  builder_.add_curvatures(curvatures);
  builder_.add_distances(distances);
  builder_.add_tangents(tangents);
  builder_.add_points(points);
  builder_.add_length(length);
  return builder_.Finish();
}

inline flatbuffers::Offset<FlatCurve> CreateFlatCurveDirect(
    flatbuffers::FlatBufferBuilder &_fbb,
    float length = 0.0f,
    const std::vector<FlatVec3> *points = nullptr,
    const std::vector<FlatVec3> *tangents = nullptr,
    const std::vector<float> *distances = nullptr,
    const std::vector<float> *curvatures = nullptr) {
  return yangbot::cpp::CreateFlatCurve(
      _fbb,
      length,
      points ? _fbb.CreateVectorOfStructs<FlatVec3>(*points) : 0,
      tangents ? _fbb.CreateVectorOfStructs<FlatVec3>(*tangents) : 0,
      distances ? _fbb.CreateVector<float>(*distances) : 0,
      curvatures ? _fbb.CreateVector<float>(*curvatures) : 0);
}

inline const yangbot::cpp::FlatCurve *GetFlatCurve(const void *buf) {
  return flatbuffers::GetRoot<yangbot::cpp::FlatCurve>(buf);
}

inline const yangbot::cpp::FlatCurve *GetSizePrefixedFlatCurve(const void *buf) {
  return flatbuffers::GetSizePrefixedRoot<yangbot::cpp::FlatCurve>(buf);
}

inline bool VerifyFlatCurveBuffer(
    flatbuffers::Verifier &verifier) {
  return verifier.VerifyBuffer<yangbot::cpp::FlatCurve>(nullptr);
}

inline bool VerifySizePrefixedFlatCurveBuffer(
    flatbuffers::Verifier &verifier) {
  return verifier.VerifySizePrefixedBuffer<yangbot::cpp::FlatCurve>(nullptr);
}

inline void FinishFlatCurveBuffer(
    flatbuffers::FlatBufferBuilder &fbb,
    flatbuffers::Offset<yangbot::cpp::FlatCurve> root) {
  fbb.Finish(root);
}

inline void FinishSizePrefixedFlatCurveBuffer(
    flatbuffers::FlatBufferBuilder &fbb,
    flatbuffers::Offset<yangbot::cpp::FlatCurve> root) {
  fbb.FinishSizePrefixed(root);
}

}  // namespace cpp
}  // namespace yangbot

#endif  // FLATBUFFERS_GENERATED_YANGBOT_YANGBOT_CPP_H_
