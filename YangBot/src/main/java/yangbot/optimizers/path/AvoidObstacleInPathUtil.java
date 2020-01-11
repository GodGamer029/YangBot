package yangbot.optimizers.path;

import yangbot.prediction.Curve;
import yangbot.vector.Vector3;

import java.util.List;

public class AvoidObstacleInPathUtil {

    public static List<Curve.ControlPoint> applyBallCollisionFix(Curve.PathCheckStatus pathCheckStatus, List<Curve.ControlPoint> controlPoints, Curve currentPath, int tries) {
        if (!pathCheckStatus.collidedWithBall)
            return controlPoints;

        // Take the contact point and place a control point in the opposite direction
        Vector3 contactPoint = pathCheckStatus.ballCollisionContactPoint.withZ(controlPoints.get(0).point.z);
        final float distanceAtCollision = currentPath.findNearest(contactPoint);
        Vector3 pathPointAtCollision = currentPath.pointAt(distanceAtCollision);
        Vector3 tangentAtCollision = currentPath.tangentAt(distanceAtCollision);

        Vector3 collisionNormal = contactPoint.sub(pathPointAtCollision);
        Vector3 collisionNormalParallel = tangentAtCollision.mul(collisionNormal.dot(tangentAtCollision));
        Vector3 collisionNormalPerpendicular = collisionNormal.sub(collisionNormalParallel).normalized();

        Vector3 newControlPoint = pathPointAtCollision.add(collisionNormalPerpendicular.mul(yangbot.input.BallData.RADIUS * -1.2f * (tries / 2f + 1)).withZ(0)).withZ(controlPoints.get(0).point.z);
        if (tries == 0)
            controlPoints.add(1, new Curve.ControlPoint(newControlPoint, newControlPoint.sub(controlPoints.get(0).point).normalized()));
        else
            controlPoints.set(1, new Curve.ControlPoint(newControlPoint, newControlPoint.sub(controlPoints.get(0).point).normalized()));
        controlPoints.get(2).tangent = controlPoints.get(2).point.sub(controlPoints.get(1).point);
        //this.collision = pathCheckStatus.ballCollisionContactPoint;

        return controlPoints;
    }
}
