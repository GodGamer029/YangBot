package yangbot.util;

import com.google.flatbuffers.FlatBufferBuilder;
import org.jetbrains.annotations.NotNull;
import rlbot.Bot;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.RenderMessage;
import rlbot.flat.RenderType;
import rlbot.render.RenderPacket;
import rlbot.render.Renderer;
import yangbot.input.ControlsOutput;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancedRenderer extends Renderer {
    private final static Map<Integer, AdvancedRenderer> botLoopMap = new ConcurrentHashMap<>();
    private RenderPacket previousPacket = null;
    private List<Integer> renderMessageOffsetsRef;

    public AdvancedRenderer(int index) {
        super(index);
        try{
            var field = Renderer.class.getDeclaredField("renderMessageOffsets");
            field.setAccessible(true);
            this.renderMessageOffsetsRef = (List<Integer>)field.get(this);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static AdvancedRenderer forBotLoop(final Bot bot) {
        return forBotIndex(bot.getIndex());
    }

    public static AdvancedRenderer forBotIndex(int index) {
        botLoopMap.computeIfAbsent(index, AdvancedRenderer::new);
        return botLoopMap.get(index);
    }

    public void startPacket() {
        builder = new FlatBufferBuilder(1000);
    }

    public void finishAndSendIfDifferent() {
        RenderPacket packet = doFinishPacket();
        if (!packet.equals(previousPacket)) {
            RLBotDll.sendRenderPacket(packet);
            previousPacket = packet;
        }
    }

    public int insertColor(java.awt.Color color){
        return rlbot.flat.Color.createColor(this.builder, color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
    }

    public void drawLine3dRaw(int colorOffset, rlbot.vector.Vector3 start, rlbot.vector.Vector3 end) {
        RenderMessage.startRenderMessage(builder);
        RenderMessage.addRenderType(builder, RenderType.DrawLine3D);
        RenderMessage.addColor(builder, colorOffset);
        RenderMessage.addStart(builder, start.toFlatbuffer(builder));
        RenderMessage.addEnd(builder, end.toFlatbuffer(builder));
        int finalOffset = RenderMessage.endRenderMessage(builder);

        renderMessageOffsetsRef.add(finalOffset);
    }

    public void drawCentered3dCube(Color c, Vector3 center, float scale) {
        this.drawCentered3dCube(c, center, new Vector3(scale, scale, scale));
    }

    @SuppressWarnings("WeakerAccess")
    public void drawCentered3dCube(Color color, @NotNull Vector3 center, Vector3 size) {
        Vector3 startPos = center.sub(size.div(2));
        Vector3 endPos = new Vector3(size);

        int c = insertColor(color);

        // Stripes
        this.drawLine3dRaw(c, startPos.add(0, 0, 0), startPos.add(0, 0, endPos.z));
        this.drawLine3dRaw(c, startPos.add(endPos.x, 0, 0), startPos.add(endPos.x, 0, endPos.z));
        this.drawLine3dRaw(c, startPos.add(0, endPos.y, 0), startPos.add(0, endPos.y, endPos.z));
        this.drawLine3dRaw(c, startPos.add(endPos.x, endPos.y, 0), startPos.add(endPos.x, endPos.y, endPos.z));

        // Ring 1
        this.drawLine3dRaw(c, startPos, startPos.add(0, endPos.y, 0));
        this.drawLine3dRaw(c, startPos, startPos.add(endPos.x, 0, 0));
        this.drawLine3dRaw(c, startPos.add(0, endPos.y, 0), startPos.add(endPos.x, endPos.y, 0));
        this.drawLine3dRaw(c, startPos.add(endPos.x, 0, 0), startPos.add(endPos.x, endPos.y, 0));

        // Ring 2
        this.drawLine3dRaw(c, startPos.add(0, 0, endPos.z), startPos.add(0, endPos.y, endPos.z));
        this.drawLine3dRaw(c, startPos.add(0, 0, endPos.z), startPos.add(endPos.x, 0, endPos.z));
        this.drawLine3dRaw(c, startPos.add(0, endPos.y, endPos.z), startPos.add(endPos.x, endPos.y, endPos.z));
        this.drawLine3dRaw(c, startPos.add(endPos.x, 0, endPos.z), startPos.add(endPos.x, endPos.y, endPos.z));
    }

    public void drawCircle(Color c, Vector3 center, float radius) {
        final Vector2 start = new Vector2(1, 0);

        final float resolution = 20;
        for (int i = 0; i < resolution; i++) {
            final var thisPos = start.rotateBy((Math.PI * 2 * i) / resolution);
            final var nextPos = start.rotateBy((Math.PI * 2 * (i + 1)) / resolution);
            this.drawLine3d(c, thisPos.mul(radius).withZ(0).add(center), nextPos.mul(radius).withZ(0).add(center));
        }
    }

    public void drawCircle(Color c, Vector3 center, float radius, float startAngle, float endAngle) {
        final float resolution = 10;

        if (startAngle > endAngle) {
            float temp = startAngle;
            startAngle = endAngle;
            endAngle = temp;
        }

        float angleDiv = (endAngle - startAngle) / resolution;
        assert angleDiv > 0 : angleDiv;

        for (float currentAngle = startAngle; currentAngle < endAngle; currentAngle += angleDiv) {
            final var thisPos = Vector2.fromAngle(currentAngle);

            var nextPos = Vector2.fromAngle(currentAngle + angleDiv);
            if (currentAngle + angleDiv >= endAngle) {
                angleDiv = endAngle - currentAngle;
                nextPos = Vector2.fromAngle(currentAngle + angleDiv);
                currentAngle = endAngle; // Exit condition
            }

            this.drawLine3d(c, thisPos.mul(radius).withZ(0).add(center), nextPos.mul(radius).withZ(0).add(center));
        }
    }

    public void drawControlsOutput(ControlsOutput output, int y){
        this.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, y += 20), 1, 1);
        this.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, y += 20), 1, 1);
        this.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, y += 20), 1, 1);
        this.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, y += 20), 1, 1);
        this.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), output.getThrottle() < 0 ? Color.RED : Color.WHITE, new Point(10, y += 20), 1, 1);
        this.drawString2d(String.format("Boost: %s", output.holdBoost() ? "true" : "false"), output.holdBoost() ? Color.GREEN : Color.RED, new Point(10, y += 20), 1, 1 );
        this.drawString2d(String.format("Jump: %s", output.holdJump() ? "true" : "false"), Color.WHITE, new Point(10, y += 20), 1, 1 );
    }
}
