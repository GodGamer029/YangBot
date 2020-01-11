// automatically generated by the FlatBuffers compiler, do not modify

package yangbot.cpp;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Struct;

import java.nio.ByteBuffer;

@SuppressWarnings("unused")
public final class FlatControlPoint extends Struct {
    public static int createFlatControlPoint(FlatBufferBuilder builder, float position_x, float position_y, float position_z, float normal_x, float normal_y, float normal_z, float tangent_x, float tangent_y, float tangent_z) {
        builder.prep(4, 36);
        builder.prep(4, 12);
        builder.putFloat(tangent_z);
        builder.putFloat(tangent_y);
        builder.putFloat(tangent_x);
        builder.prep(4, 12);
        builder.putFloat(normal_z);
        builder.putFloat(normal_y);
        builder.putFloat(normal_x);
        builder.prep(4, 12);
        builder.putFloat(position_z);
        builder.putFloat(position_y);
        builder.putFloat(position_x);
        return builder.offset();
    }

    public void __init(int _i, ByteBuffer _bb) {
        bb_pos = _i;
        bb = _bb;
    }

    public FlatControlPoint __assign(int _i, ByteBuffer _bb) {
        __init(_i, _bb);
        return this;
    }

    public FlatVec3 position() {
        return position(new FlatVec3());
    }

    public FlatVec3 position(FlatVec3 obj) {
        return obj.__assign(bb_pos + 0, bb);
    }

    public FlatVec3 normal() {
        return normal(new FlatVec3());
    }

    public FlatVec3 normal(FlatVec3 obj) {
        return obj.__assign(bb_pos + 12, bb);
    }

    public FlatVec3 tangent() {
        return tangent(new FlatVec3());
    }

    public FlatVec3 tangent(FlatVec3 obj) {
        return obj.__assign(bb_pos + 24, bb);
    }
}

