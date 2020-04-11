package yangbot.util.math;

import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector2;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.List;

public class Area2 {
    public final List<Vector2> points;
    private final Area area;

    public Area2(List<Vector2> points) {
        assert points.size() > 2 : "An area needs more than 2 points otherwise space-time will collapse";
        this.points = points;

        var path = new Path2D.Float();
        var it = points.iterator();
        var first = it.next();
        path.moveTo(first.x, first.y);
        while (it.hasNext()) {
            var next = it.next();
            path.lineTo(next.x, next.y);
        }

        this.area = new Area(path);
    }

    public boolean contains(Vector2 point) {
        return this.area.contains(point.x, point.y);
    }

    public void draw(AdvancedRenderer renderer, float zPos) {
        var it = this.points.iterator();
        Vector2 lastPoint = it.next();
        renderer.drawCentered3dCube(Color.YELLOW, lastPoint.withZ(zPos), 30);
        while (it.hasNext()) {
            var p = it.next();
            renderer.drawLine3d(Color.RED, lastPoint.withZ(zPos), p.withZ(zPos));
            renderer.drawCentered3dCube(Color.YELLOW, p.withZ(zPos), 30);
            lastPoint = p;
        }
        renderer.drawLine3d(Color.RED, lastPoint.withZ(zPos), this.points.get(0).withZ(zPos));
    }
}
