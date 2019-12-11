package yangbot.strategy;

import yangbot.cpp.CarCollisionInfo;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.manuever.DodgeManeuver;
import yangbot.manuever.TurnManeuver;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.Optional;

public class RecoverStrategy extends Strategy {

    private TurnManeuver groundTurnManeuver;
    private TurnManeuver boostTurnManeuver;
    private float recoverStartTime = 0;
    private float recoverEndTime = -1;

    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        if (car.hasWheelContact)
            this.setDone();
        groundTurnManeuver = new TurnManeuver();
        boostTurnManeuver = new TurnManeuver();
        recoverStartTime = car.elapsedSeconds;
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        final float targetZModifier = -0.8f;
        final float boostZModifier = -0.5f;

        GameData gameData = GameData.current();
        AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        CarData car = gameData.getCarData();
        BallData ballData = gameData.getBallData();

        if (car.hasWheelContact) {
            this.setDone();
            return;
        }

        controlsOutput.withThrottle(1);

        Optional<CarCollisionInfo> carCollisionInfoOptional = YangBotJNAInterop.simulateCarWallCollision(car);
        if (!carCollisionInfoOptional.isPresent()) {
            this.setDone();
            return;
        }

        CarCollisionInfo carCollisionInfo = carCollisionInfoOptional.get();

        Vector3 impactNormal = new Vector3(carCollisionInfo.impact().direction());
        Vector3 carPositionAtImpact = new Vector3(carCollisionInfo.carData().position());
        float simulationTime = carCollisionInfo.carData().elapsedSeconds();

        recoverEndTime = car.elapsedSeconds + simulationTime;

        boolean speedflipPossible = recoverEndTime - recoverStartTime <= DodgeManeuver.startTimeout && !car.doubleJumped && recoverEndTime - recoverStartTime > 0.15f && carPositionAtImpact.z < 1500;
        Vector3 targetDirection = ballData.position.sub(car.position).normalized();

        if (carPositionAtImpact.z > 500) {
            targetDirection = new Vector3(0, 0, -1);
        } else {
            Optional<YangBallPrediction.YangPredictionFrame> ballFrameAtImpact = gameData.getBallPrediction().getFrameAtRelativeTime(simulationTime + 0.5f);
            if (ballFrameAtImpact.isPresent())
                targetDirection = ballFrameAtImpact.get().ballData.position.sub(carPositionAtImpact).normalized();
        }

        Matrix3x3 targetOrientationMatrix = Matrix3x3.roofTo(impactNormal, targetDirection);
        if (speedflipPossible) {
            Vector3 left = targetOrientationMatrix.left();
            targetOrientationMatrix = Matrix3x3.axisToRotation(left.mul(Math.PI * -0.175)).matrixMul(targetOrientationMatrix);
        }
        groundTurnManeuver.target = targetOrientationMatrix;

        if (car.boost > 60 && groundTurnManeuver.simulate(car).elapsedSeconds + RLConstants.simulationTickFrequency < simulationTime) { // More than 50 boost & can complete the surface-align maneuver before impact
            Vector3 boostDirection = targetDirection
                    .flatten()
                    .unitVectorWithZ(targetZModifier);

            boostTurnManeuver.target = Matrix3x3.lookAt(boostDirection, groundTurnManeuver.target.up());
            boostTurnManeuver.step(dt, controlsOutput);

            Vector3 forward = car.forward();
            if (boostTurnManeuver.isDone() || (forward.z <= boostZModifier && forward.angle(boostDirection) < Math.PI * 0.3f))
                controlsOutput.withBoost(true);
        } else {
            groundTurnManeuver.step(dt, controlsOutput);
        }

        Vector3 impactPosition = new Vector3(carCollisionInfo.impact().start());

        renderer.drawCentered3dCube(Color.RED, car.position, 50);
        renderer.drawLine3d(Color.YELLOW, impactPosition, impactPosition.add(impactNormal.mul(150)));

        if (simulationTime >= 2f / 60f)
            renderer.drawString2d(String.format("Arriving in: %.1f", simulationTime), Color.WHITE, new Point(400, 400), 2, 2);
        renderer.drawString2d(String.format("Total: %.1f", this.recoverEndTime - this.recoverStartTime), speedflipPossible ? Color.GREEN : Color.RED, new Point(400, 450), 2, 2);

        if (speedflipPossible && simulationTime < 0.5f) {
            double backWheelsHeight = impactNormal.dot(car.hitbox.removeOffset(car.hitbox.permutatePoint(car.position, -1, 0, -1)).sub(impactPosition));
            double frontWheelsHeight = impactNormal.dot(car.hitbox.removeOffset(car.hitbox.permutatePoint(car.position, 1, 0, -1)).sub(impactPosition));
            if (backWheelsHeight <= 10 && backWheelsHeight > 0 && frontWheelsHeight - 15 > backWheelsHeight && !car.doubleJumped) {
                controlsOutput.withJump(true);
                controlsOutput.withPitch(-1);
            }
        }

        car.hitbox.draw(renderer, impactPosition, 1.5f, Color.RED);
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.of(new DefaultStrategy());
    }
}
