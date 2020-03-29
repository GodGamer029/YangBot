package yangbot.cpp;


import com.google.flatbuffers.FlatBufferBuilder;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import rlbot.cppinterop.ByteBufferStruct;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.ImmutableBallData;
import yangbot.path.Curve;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector3;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class YangBotJNAInterop {
    static {
        YangBotCppInterop.doNothing(); // Make sure to load required libs with the static{} initializer

        boolean is64Bit = System.getProperty("os.arch").contains("64");

        final String libName = System.mapLibraryName(is64Bit ? "YangBotCpp64" : "YangBotCpp32");
        Native.register(libName);
    }

    private static Memory getMemory(byte[] protoBytes) {
        if (protoBytes.length == 0)
            return new Memory(1);

        final Memory mem = new Memory(protoBytes.length);
        mem.write(0, protoBytes, 0, protoBytes.length);
        return mem;
    }

    public static Optional<yangbot.cpp.FlatCarCollisionInfo> simulateCarWallCollision(CarData carData) {
        try {
            FlatBufferBuilder builder = new FlatBufferBuilder(128);

            builder.finish(carData.makeFlat(builder));

            final byte[] proto = builder.sizedByteArray();
            final Memory memory = getMemory(proto);

            final ByteBufferStruct struct = simulateCarCollision(memory);
            if (struct.size < 4) {
                if (struct.size > 0)
                    Free(struct.ptr);
                return Optional.empty();
            }
            final byte[] protoBytes = struct.ptr.getByteArray(0, struct.size);
            Free(struct.ptr);
            return Optional.ofNullable(yangbot.cpp.FlatCarCollisionInfo.getRootAsFlatCarCollisionInfo(ByteBuffer.wrap(protoBytes)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public static Optional<yangbot.cpp.FlatCarData> simulateSimpleCar(CarData carData, float time) {
        try {
            FlatBufferBuilder builder = new FlatBufferBuilder(128);

            builder.finish(carData.makeFlat(builder));

            final byte[] proto = builder.sizedByteArray();
            final Memory memory = getMemory(proto);

            final ByteBufferStruct struct = simulateSimpleCar(memory, time);
            if (struct.size < 4) {
                if (struct.size > 0)
                    Free(struct.ptr);
                return Optional.empty();
            }
            final byte[] protoBytes = struct.ptr.getByteArray(0, struct.size);
            Free(struct.ptr);
            return Optional.ofNullable(yangbot.cpp.FlatCarData.getRootAsFlatCarData(ByteBuffer.wrap(protoBytes)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public static YangBallPrediction getBallPrediction(BallData ballData, int tickrate) {
        Memory ballMemory;
        {
            FlatBufferBuilder builder = new FlatBufferBuilder(128);

            builder.finish(ballData.makeFlatPhysics(builder));

            final byte[] proto = builder.sizedByteArray();
            ballMemory = getMemory(proto);
        }

        final ByteBufferStruct struct = simulateBall(ballMemory, tickrate);
        if (struct.size < 4) {
            if (struct.size > 0)
                Free(struct.ptr);
            return YangBallPrediction.empty();
        }
        final byte[] protoBytes = struct.ptr.getByteArray(0, struct.size);
        Free(struct.ptr);
        FlatPhysicsPrediction flatPrediction = FlatPhysicsPrediction.getRootAsFlatPhysicsPrediction(ByteBuffer.wrap(protoBytes));

        float tickFreq = 1f / tickrate;

        List<YangBallPrediction.YangPredictionFrame> ballDataList = new ArrayList<>();
        for (int i = 0; i < flatPrediction.framesLength(); i++) {
            FlatPhysics frame = flatPrediction.frames(i);
            ImmutableBallData data = new ImmutableBallData(frame);
            ballDataList.add(new YangBallPrediction.YangPredictionFrame(i * tickFreq + ballData.elapsedSeconds, i * tickFreq, data));
        }
        return YangBallPrediction.from(ballDataList, tickFreq);
    }

    public static Optional<Curve> findPath(Vector3 startPos, Vector3 startTangent, Vector3 endPos, Vector3 endTangent, float endTangentScalar) {
        try {
            FlatBufferBuilder builder = new FlatBufferBuilder(128);

            FlatNavigatorRequest.startFlatNavigatorRequest(builder);
            FlatNavigatorRequest.addStartPosition(builder, startPos.toYangbuffer(builder));
            FlatNavigatorRequest.addStartTangent(builder, startTangent.toYangbuffer(builder));
            FlatNavigatorRequest.addEndPosition(builder, endPos.toYangbuffer(builder));
            FlatNavigatorRequest.addEndTangent(builder, endTangent.toYangbuffer(builder));
            FlatNavigatorRequest.addEndTangentMultiplier(builder, endTangentScalar);

            builder.finish(FlatNavigatorRequest.endFlatNavigatorRequest(builder));

            final byte[] proto = builder.sizedByteArray();
            final Memory memory = getMemory(proto);

            long ms = System.currentTimeMillis();
            final ByteBufferStruct struct = findPath(memory);
            if (struct.size < 4) {
                if (struct.size > 0)
                    Free(struct.ptr);
                return Optional.empty();
            }
            final byte[] protoBytes = struct.ptr.getByteArray(0, struct.size);
            Free(struct.ptr);
            FlatCurve flatCurve = FlatCurve.getRootAsFlatCurve(ByteBuffer.wrap(protoBytes));
            if (flatCurve == null || flatCurve.length() == 0)
                return Optional.empty();

            return Optional.ofNullable(Curve.from(flatCurve));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public static Optional<yangbot.cpp.FlatPhysics> simulateCarBallCollision(CarData carData, BallData ballData, float dt) {
        try {
            Memory carMemory;
            Memory ballMemory;
            {
                FlatBufferBuilder builder = new FlatBufferBuilder(128);

                builder.finish(carData.makeFlat(builder));

                final byte[] proto = builder.sizedByteArray();
                carMemory = getMemory(proto);
            }

            {
                FlatBufferBuilder builder = new FlatBufferBuilder(128);

                builder.finish(ballData.makeFlatPhysics(builder));

                final byte[] proto = builder.sizedByteArray();
                ballMemory = getMemory(proto);
            }

            final ByteBufferStruct struct = simulateCarBallCollision(carMemory, ballMemory, dt);
            if (struct.size < 4) {
                if (struct.size > 0)
                    Free(struct.ptr);
                System.out.println("Got nothign back");
                return Optional.empty();
            }
            final byte[] protoBytes = struct.ptr.getByteArray(0, struct.size);
            Free(struct.ptr);
            return Optional.ofNullable(yangbot.cpp.FlatPhysics.getRootAsFlatPhysics(ByteBuffer.wrap(protoBytes)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private static native ByteBufferStruct simulateCarCollision(Pointer ptr);

    private static native ByteBufferStruct simulateSimpleCar(Pointer ptr, float time);

    private static native ByteBufferStruct simulateCarBallCollision(Pointer car, Pointer ball, float dt);

    private static native ByteBufferStruct findPath(Pointer pathRequest);

    private static native ByteBufferStruct simulateBall(Pointer ball, int tickrate);

    private static native void Free(Pointer ptr);
}
