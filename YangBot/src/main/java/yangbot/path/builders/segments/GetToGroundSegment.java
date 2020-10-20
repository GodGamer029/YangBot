package yangbot.path.builders.segments;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.path.builders.PathBuilder;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

// TODO: use navmesh
public class GetToGroundSegment extends AtbaSegment {

    public GetToGroundSegment(PathBuilder b) {
        super(b, b.getCurrentPosition().withZ(RLConstants.carElevation - 1));
    }

    @Override
    public Vector3 getEndTangent() { // Pointing from endpos to middle, bad approximation
        return new Vector2().sub(this.endPos.flatten()).normalized().withZ(0);
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        boolean isDone = super.step(dt, output);

        if (isDone)
            return true;

        final GameData gameData = GameData.current();
        final CarData carData = gameData.getCarData();

        if (carData.position.z < RLConstants.carElevation + 10)
            return true;

        var diff = carData.position.sub(endPos);

        // our endPos is likely outside the field, so make up for that by not caring about the up axis
        return new Vector2(diff.dot(carData.forward()), diff.dot(carData.right())).magnitude() < 100;
    }
}
