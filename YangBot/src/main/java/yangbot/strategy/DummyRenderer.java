package yangbot.strategy;

import org.jetbrains.annotations.NotNull;
import rlbot.vector.Vector3;
import yangbot.input.ControlsOutput;
import yangbot.util.AdvancedRenderer;

import java.awt.*;

public class DummyRenderer extends AdvancedRenderer {
    public DummyRenderer(int index) {
        super(index);
    }

    @Override
    public void finishAndSendIfDifferent() {
        resetPacket();
    }

    @Override
    public void drawString2d(String text, Color color, Point upperLeft, int scaleX, int scaleY) {}

    @Override
    public void drawLine3d(Color color, Vector3 start, Vector3 end) {}

    @Override
    public void drawRectangle2d(Color color, Point upperLeft, int width, int height, boolean filled) {}

    @Override
    public void drawRectangle3d(Color color, Vector3 upperLeft, int width, int height, boolean filled) {}

    @Override
    public void drawCenteredRectangle3d(Color color, Vector3 position, int width, int height, boolean filled) {}

    @Override
    public void drawString3d(String text, Color color, Vector3 upperLeft, int scaleX, int scaleY) {}

    @Override
    public void drawLine3dRaw(int colorOffset, Vector3 start, Vector3 end) {}

    @Override
    public int insertColor(Color color) {
        return 0;
    }

    @Override
    public void drawCentered3dCube(Color c, yangbot.util.math.vector.Vector3 center, float scale) {}

    @Override
    public void drawCentered3dCube(Color color, yangbot.util.math.vector.@NotNull Vector3 center, yangbot.util.math.vector.Vector3 size) {}

    @Override
    public void drawCircle(Color c, yangbot.util.math.vector.Vector3 center, float radius) {}

    @Override
    public void drawCircle(Color c, yangbot.util.math.vector.Vector3 center, float radius, float startAngle, float endAngle) {}

    @Override
    public void drawControlsOutput(ControlsOutput output, int y) {}
}
