package yangbot.path.builders.segments;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.path.builders.PathSegment;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class AtbaSegment extends PathSegment {

    protected final Vector3 startPos;
    protected Vector3 endPos;
    protected float targetSpeed = -1;

    public AtbaSegment(Vector3 startPos, float startSpeed, Vector3 endPos) {
        super(startSpeed);
        this.startPos = startPos;
        this.endPos = endPos;
    }

    @Override
    public Vector3 getEndPos() {
        return this.endPos;
    }

    @Override
    public Vector3 getEndTangent() {
        return this.endPos.sub(this.startPos).normalized();
    }

    @Override
    public float getEndSpeed() {
        return DriveManeuver.max_throttle_speed;
    }

    @Override
    public float getTimeEstimate() {
        return (float) this.startPos.distance(this.endPos) / (getEndSpeed() * 0.9f);
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        final GameData gameData = GameData.current();
        final CarData carData = gameData.getCarData();

        if (targetSpeed > 0)
            DriveManeuver.speedController(dt, output, carData.forwardSpeed(), targetSpeed - 10, targetSpeed + 10, 0.04f, false);
        else
            DriveManeuver.speedController(dt, output, carData.forwardSpeed(), DriveManeuver.max_throttle_speed, CarData.MAX_VELOCITY, 0.04f, false);

        DriveManeuver.steerController(output, carData, this.endPos);

        return carData.position.distance(endPos) < 100;
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        renderer.drawCentered3dCube(Color.RED, this.startPos, 100);
        renderer.drawLine3d(color, this.startPos, this.endPos);
        renderer.drawCentered3dCube(Color.GREEN, this.endPos, 100);
    }
}
