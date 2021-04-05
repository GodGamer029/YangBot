package yangbot.optimizers.path;

import yangbot.input.CarData;
import yangbot.path.Curve;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector3;

import java.util.List;
import java.util.Optional;

public class AvoidObstacleInPathUtil {

    public static Optional<Curve> mutatePath(Curve currentPath, CarData car, float arrivalTimeAbsolute, YangBallPrediction ballPrediction, int maxIterations) {
        assert currentPath.getControlPoints() != null && currentPath.getControlPoints().size() > 0 : "Control points are needed to change the path";

        Curve.PathCheckStatus pathStatus = currentPath.getPathCheckStatus();
        if (pathStatus == null || pathStatus.pathStatus == Curve.PathStatus.UNKNOWN)
            pathStatus = currentPath.doPathChecking(car, arrivalTimeAbsolute, ballPrediction);

        List<Curve.ControlPoint> controlPoints = currentPath.getControlPoints();

        Curve lastValidPath = null;
        if (pathStatus.isValid()) {
            if (pathStatus.collidedWithBall) {
                for (int i = 0; i < maxIterations; i++) { // Move the path away from the ball
                    pathStatus = currentPath.doPathChecking(car, arrivalTimeAbsolute, ballPrediction);
                    if (pathStatus.isValid() && i > 0)
                        lastValidPath = currentPath;
                    if (pathStatus.collidedWithBall) { // Colliding with ball
                        AvoidObstacleInPathUtil.applyBallCollisionFix(pathStatus, controlPoints, currentPath, i);
                        currentPath = new Curve(controlPoints);
                    } else
                        break; // We successfully avoided the ball
                }
            }
            if (pathStatus.isValid() && !pathStatus.collidedWithBall) {
                return Optional.of(currentPath);
            }
        }
        return Optional.ofNullable(lastValidPath);
    }

    public static List<Curve.ControlPoint> applyBallCollisionFix(Curve.PathCheckStatus pathCheckStatus, List<Curve.ControlPoint> controlPoints, Curve currentPath, int tries) {
        if (!pathCheckStatus.collidedWithBall)
            return controlPoints;

        assert controlPoints.size() == 2 || controlPoints.size() == 3;

        // Take the contact point and place a control point in the opposite direction
        Vector3 contactPoint = pathCheckStatus.ballCollisionContactPoint.withZ(controlPoints.get(0).pos.z);
        final float distanceAtCollision = currentPath.findNearest(contactPoint);
        Vector3 pathPointAtCollision = currentPath.pointAt(distanceAtCollision);
        Vector3 tangentAtCollision = currentPath.tangentAt(distanceAtCollision);

        Vector3 collisionNormal = contactPoint.sub(pathPointAtCollision);
        Vector3 collisionNormalParallel = tangentAtCollision.mul(collisionNormal.dot(tangentAtCollision));
        Vector3 collisionNormalPerpendicular = collisionNormal.sub(collisionNormalParallel).normalized();

        Vector3 newControlPoint = pathPointAtCollision.add(collisionNormalPerpendicular.mul(yangbot.input.BallData.RADIUS * -1.2f * (tries / 2f + 1)).withZ(0)).withZ(controlPoints.get(0).pos.z);
        if (tries == 0)
            controlPoints.add(1, new Curve.ControlPoint(newControlPoint, newControlPoint.sub(controlPoints.get(0).pos).normalized()));
        else
            controlPoints.set(1, new Curve.ControlPoint(newControlPoint, newControlPoint.sub(controlPoints.get(0).pos).normalized()));
        controlPoints.get(2).tangent = controlPoints.get(2).pos.sub(controlPoints.get(1).pos);
        //this.collision = pathCheckStatus.ballCollisionContactPoint;

        return controlPoints;
    }
}
