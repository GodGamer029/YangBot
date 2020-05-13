package yangbot.strategy;

import rlbot.vector.Vector3;
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
    public void drawString2d(String text, Color color, Point upperLeft, int scaleX, int scaleY) {

    }

    @Override
    public void drawLine3d(Color color, Vector3 start, Vector3 end) {

    }

    @Override
    public void drawRectangle2d(Color color, Point upperLeft, int width, int height, boolean filled) {

    }

    @Override
    public void drawRectangle3d(Color color, Vector3 upperLeft, int width, int height, boolean filled) {

    }

    @Override
    public void drawCenteredRectangle3d(Color color, Vector3 position, int width, int height, boolean filled) {

    }

    @Override
    public void drawString3d(String text, Color color, Vector3 upperLeft, int scaleX, int scaleY) {

    }
}
