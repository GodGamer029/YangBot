package yangbot.cpp;


import com.google.flatbuffers.FlatBufferBuilder;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import rlbot.cppinterop.ByteBufferStruct;
import yangbot.input.CarData;

import java.nio.ByteBuffer;
import java.util.Optional;

public class YangBotJNAInterop {
    static {
        YangBotCppInterop.doNothing();

        boolean is64Bit = System.getProperty("os.arch").contains("64");

        final String libName = is64Bit ? "YangBotCpp64.dll" : "YangBotCpp32.dll";
        Native.register(libName);
    }

    private static Memory getMemory(byte[] protoBytes) {
        if (protoBytes.length == 0) {
            // The empty controller state is actually 0 bytes, so this can happen.
            // You're not allowed to pass 0 bytes to the Memory constructor, so do this.
            return new Memory(1);
        }

        final Memory mem = new Memory(protoBytes.length);
        mem.write(0, protoBytes, 0, protoBytes.length);
        return mem;
    }

    public static Optional<CarCollisionInfo> simulateCarWallCollision(CarData carData) {
        try {
            FlatBufferBuilder builder = new FlatBufferBuilder(128);

            FBSCarData.startFBSCarData(builder);
            carData.apply(builder);
            int offset = FBSCarData.endFBSCarData(builder);
            builder.finish(offset);

            final byte[] proto = builder.sizedByteArray();
            final Memory memory = getMemory(proto);

            final ByteBufferStruct struct = simulateCarCollision(memory, proto.length);
            if (struct.size < 4) {
                if (struct.size > 0)
                    Free(struct.ptr);
                return Optional.empty();
            }
            final byte[] protoBytes = struct.ptr.getByteArray(0, struct.size);
            Free(struct.ptr);
            return Optional.ofNullable(CarCollisionInfo.getRootAsCarCollisionInfo(ByteBuffer.wrap(protoBytes)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public static native ByteBufferStruct simulateCarCollision(Pointer ptr, int size);

    private static native void Free(Pointer ptr);
}
