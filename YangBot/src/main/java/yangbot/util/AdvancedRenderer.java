package yangbot.util;

import com.google.flatbuffers.FlatBufferBuilder;
import rlbot.Bot;
import rlbot.cppinterop.RLBotDll;
import rlbot.render.RenderPacket;
import rlbot.render.Renderer;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancedRenderer extends Renderer {
    private static Map<Integer, AdvancedRenderer> botLoopMap = new ConcurrentHashMap<>();
    private RenderPacket previousPacket = null;

    public AdvancedRenderer(int index) {
        super(index);
    }

    public static AdvancedRenderer forBotLoop(final Bot bot) {
        botLoopMap.computeIfAbsent(bot.getIndex(), AdvancedRenderer::new);
        return botLoopMap.get(bot.getIndex());
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

    public void drawCentered3dCube(Color c, Vector3 center, float scale) {
        this.drawCentered3dCube(c, center, new Vector3(scale, scale, scale));
    }

    @SuppressWarnings("WeakerAccess")
    public void drawCentered3dCube(Color c, Vector3 center, Vector3 size) {
        Vector3 startPos = center.sub(size.div(2));
        Vector3 endPos = new Vector3(size);

        // Stripes
        this.drawLine3d(c, startPos.add(0, 0, 0), startPos.add(0, 0, endPos.z));
        this.drawLine3d(c, startPos.add(endPos.x, 0, 0), startPos.add(endPos.x, 0, endPos.z));
        this.drawLine3d(c, startPos.add(0, endPos.y, 0), startPos.add(0, endPos.y, endPos.z));
        this.drawLine3d(c, startPos.add(endPos.x, endPos.y, 0), startPos.add(endPos.x, endPos.y, endPos.z));

        // Ring 1
        this.drawLine3d(c, startPos, startPos.add(0, endPos.y, 0));
        this.drawLine3d(c, startPos, startPos.add(endPos.x, 0, 0));
        this.drawLine3d(c, startPos.add(0, endPos.y, 0), startPos.add(endPos.x, endPos.y, 0));
        this.drawLine3d(c, startPos.add(endPos.x, 0, 0), startPos.add(endPos.x, endPos.y, 0));

        // Ring 2
        this.drawLine3d(c, startPos.add(0, 0, endPos.z), startPos.add(0, endPos.y, endPos.z));
        this.drawLine3d(c, startPos.add(0, 0, endPos.z), startPos.add(endPos.x, 0, endPos.z));
        this.drawLine3d(c, startPos.add(0, endPos.y, endPos.z), startPos.add(endPos.x, endPos.y, endPos.z));
        this.drawLine3d(c, startPos.add(endPos.x, 0, endPos.z), startPos.add(endPos.x, endPos.y, endPos.z));
    }
}
