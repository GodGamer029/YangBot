package yangbot.path.builders.segments;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.PathSegment;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class AtbaSegment extends PathSegment {

    protected final Vector3 startPos;
    protected Vector3 endPos;
    protected float targetSpeed = -1;
    private DriveManeuver driveManeuver;

    public AtbaSegment(PathBuilder b, Vector3 endPos) {
        super(b);
        this.startPos = b.getCurrentPosition();
        this.endPos = endPos;
        this.driveManeuver = new DriveManeuver();
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

        this.driveManeuver.target = this.endPos;

        DriveManeuver.steerController(output, carData, this.endPos);

        if (targetSpeed > 0){
            this.driveManeuver.minimumSpeed = targetSpeed - 5;
            this.driveManeuver.maximumSpeed = targetSpeed + 10;
            this.driveManeuver.allowBoost = targetSpeed > DriveManeuver.max_throttle_speed;
        }else{
            this.driveManeuver.allowBoost = false;
            this.driveManeuver.minimumSpeed = DriveManeuver.max_throttle_speed;
            this.driveManeuver.maximumSpeed = CarData.MAX_VELOCITY;
        }

        this.driveManeuver.step(dt, output);

        return carData.position.distance(endPos) < 100;
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        renderer.drawCentered3dCube(Color.RED, this.startPos, 100);
        renderer.drawLine3d(color, this.startPos, this.endPos);
        renderer.drawCentered3dCube(Color.GREEN, this.endPos, 100);
    }
}
