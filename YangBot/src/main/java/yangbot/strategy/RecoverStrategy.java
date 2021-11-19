package yangbot.strategy;

import yangbot.cpp.FlatCarCollisionInfo;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.TurnManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.Optional;

public class RecoverStrategy extends Strategy {

    private TurnManeuver groundTurnManeuver;
    private TurnManeuver boostTurnManeuver;
    private float recoverStartTime = 0;

    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        if (car.hasWheelContact)
            this.setDone();
        this.groundTurnManeuver = new TurnManeuver();
        this.boostTurnManeuver = new TurnManeuver();
        this.recoverStartTime = car.elapsedSeconds;
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        final float targetZModifier = -0.8f;
        final float boostZModifier = -0.5f;

        final GameData gameData = GameData.current();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ballData = gameData.getBallData();

        if (car.hasWheelContact) {
            this.setDone();
            return;
        }

        if (car.angularVelocity.flatten().magnitude() > 5f) {
            return; // probably flipping
        }

        controlsOutput.withThrottle(1);

        Optional<FlatCarCollisionInfo> carCollisionInfoOptional = YangBotJNAInterop.simulateCarWallCollision(car);
        if (carCollisionInfoOptional.isEmpty()) {
            this.setDone();
            return;
        }

        FlatCarCollisionInfo carCollisionInfo = carCollisionInfoOptional.get();

        Vector3 impactNormal = new Vector3(carCollisionInfo.impact().direction());
        Vector3 carPositionAtImpact = new Vector3(carCollisionInfo.carData().physics().position());
        float simulationTime = carCollisionInfo.carData().physics().elapsedSeconds();

        float recoverEndTime = car.elapsedSeconds + simulationTime;

        final boolean speedflipPossible = recoverEndTime - this.recoverStartTime <= DodgeManeuver.startTimeout && !car.doubleJumped && recoverEndTime - recoverStartTime > 0.15f && carPositionAtImpact.z < 1000;
        Vector3 targetDirection = ballData.position.sub(car.position).normalized();

        if (carPositionAtImpact.z > 150) {
            targetDirection = new Vector3(0, 0, -1);
        } else {
            Optional<YangBallPrediction.YangPredictionFrame> ballFrameAtImpact = gameData.getBallPrediction().getFrameAtRelativeTime(simulationTime + 0.5f);
            if (ballFrameAtImpact.isPresent())
                targetDirection = ballFrameAtImpact.get().ballData.position.sub(carPositionAtImpact).normalized();
        }

        Matrix3x3 targetOrientationMatrix = Matrix3x3.roofTo(impactNormal, targetDirection);
        if (speedflipPossible) {
            Vector3 left = targetOrientationMatrix.right();
            targetOrientationMatrix = Matrix3x3.axisToRotation(left.mul(Math.PI * -0.125)).matrixMul(targetOrientationMatrix);
            this.groundTurnManeuver.maxErrorAngularVelocity = 0.15f;
        } else
            this.groundTurnManeuver.maxErrorAngularVelocity = 1f;
        this.groundTurnManeuver.target = targetOrientationMatrix;

        if (car.boost > 60 && simulationTime > 0.6f && this.groundTurnManeuver.simulate(car).elapsedSeconds + RLConstants.simulationTickFrequency < simulationTime) { // More than 50 boost & can complete the surface-align maneuver before impact
            Vector3 boostDirection = targetDirection
                    .flatten()
                    .unitVectorWithZ(targetZModifier);

            this.boostTurnManeuver.target = Matrix3x3.lookAt(boostDirection, this.groundTurnManeuver.target.up());
            this.boostTurnManeuver.step(dt, controlsOutput);

            Vector3 forward = car.forward();
            if (this.boostTurnManeuver.isDone() || (forward.z <= boostZModifier && forward.angle(boostDirection) < Math.PI * 0.3f))
                controlsOutput.withBoost(true);
        } else {
            this.groundTurnManeuver.step(dt, controlsOutput);
        }

        final Vector3 impactPosition = new Vector3(carCollisionInfo.impact().start());

        renderer.drawCentered3dCube(Color.RED, car.position, 50);
        renderer.drawLine3d(Color.YELLOW, impactPosition, impactPosition.add(impactNormal.mul(150)));

        if (speedflipPossible && simulationTime <= 0.15f &&
                Math.abs(car.velocity.dot(car.right())) < 300 &&
                Math.abs(car.angularVelocity.z) < 0.75f) {
            double backWheelsHeight = impactNormal.dot(car.hitbox.removeOffset(car.hitbox.permutatePoint(car.position, -1, 0, -1)).sub(impactPosition));
            double frontWheelsHeight = impactNormal.dot(car.hitbox.removeOffset(car.hitbox.permutatePoint(car.position, 1, 0, -1)).sub(impactPosition));
            if (backWheelsHeight <= 10 && backWheelsHeight > 0 && frontWheelsHeight > backWheelsHeight + 15 && frontWheelsHeight < backWheelsHeight + 50) {
                controlsOutput.withJump(true);
                controlsOutput.withPitch(-1);
                controlsOutput.withYaw(0);
                controlsOutput.withRoll(0);
            }
        }

        car.hitbox.draw(renderer, impactPosition, 1.5f, Color.RED);
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }
}
