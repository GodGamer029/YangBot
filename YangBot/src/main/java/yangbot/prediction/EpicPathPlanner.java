package yangbot.prediction;

import yangbot.input.CarData;
import yangbot.optimizers.path.AvoidObstacleInPathUtil;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EpicPathPlanner {
    private Vector3 startPos = null, startTangent;
    private Vector3 endPos = null, endTangent;
    private boolean avoidBall = false;
    private boolean ballAvoidanceNecessary = false;
    private CarData carSim = null;
    private float arrivalTime;

    public EpicPathPlanner withStart(Vector3 pos, Vector3 tangent) {
        this.startPos = pos;
        this.startTangent = tangent;
        return this;
    }

    public EpicPathPlanner withEnd(Vector3 pos, Vector3 tangent) {
        this.endPos = pos;
        this.endTangent = tangent;
        return this;
    }

    public EpicPathPlanner withBallAvoidance(boolean enable, CarData car, float arrivalTime, boolean isRequired) {
        this.avoidBall = enable;
        this.carSim = car;
        this.arrivalTime = arrivalTime;
        this.ballAvoidanceNecessary = isRequired;
        return this;
    }

    public Optional<Curve> plan() {
        assert this.startPos != null && this.startTangent != null;
        assert this.endPos != null && this.endTangent != null;

        Curve currentPath;
        List<Curve.ControlPoint> controlPoints = new ArrayList<>();
        // Construct Path
        {
            controlPoints.add(new Curve.ControlPoint(this.startPos, this.startTangent));
            controlPoints.add(new Curve.ControlPoint(this.endPos, this.endTangent));

            currentPath = new Curve(controlPoints);
        }

        if (this.avoidBall) {
            assert this.carSim != null;
            Optional<Curve> avoidancePath = AvoidObstacleInPathUtil.mutatePath(currentPath, this.carSim, this.arrivalTime, YangBallPrediction.get(), 7);
            if (avoidancePath.isPresent())
                currentPath = avoidancePath.get(); // Replace if it worked
            else if (this.ballAvoidanceNecessary) // Return nothing if it didn't work, but was required
                currentPath = null;
        }
        return Optional.ofNullable(currentPath);
    }
}
