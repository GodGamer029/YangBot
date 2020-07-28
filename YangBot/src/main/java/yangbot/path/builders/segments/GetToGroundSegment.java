package yangbot.path.builders.segments;

import yangbot.input.RLConstants;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

// TODO: use navmesh
public class GetToGroundSegment extends AtbaSegment {

    public GetToGroundSegment(Vector3 startPos, float startSpeed) {
        super(startPos, startSpeed, startPos.withZ(RLConstants.carElevation - 1));
    }

    @Override
    public Vector3 getEndTangent() { // Pointing from endpos to middle, bad approximation
        return new Vector2().sub(this.endPos.flatten()).normalized().withZ(0);
    }
}
