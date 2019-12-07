package yangbot.strategy;

import rlbot.flat.BoxShape;
import yangbot.cpp.CarCollisionInfo;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.manuever.TurnManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.Optional;

public class RecoverStrategy extends Strategy {

    private TurnManeuver groundTurnManeuver;
    private TurnManeuver boostTurnManeuver;

    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        if (car.hasWheelContact)
            this.setDone();
        groundTurnManeuver = new TurnManeuver();
        boostTurnManeuver = new TurnManeuver();
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
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

        Vector3 start = new Vector3(carCollisionInfo.impact().start());
        Vector3 direction = new Vector3(carCollisionInfo.impact().direction());
        float simulationTime = carCollisionInfo.carData().elapsedSeconds();
        Matrix3x3 orientation = Matrix3x3.eulerToRotation(new Vector3(carCollisionInfo.carData().eulerRotation()));
        Vector3 targetDirection = ballData.position.sub(car.position).normalized();

        groundTurnManeuver.target = Matrix3x3.roofTo(direction, targetDirection);
        groundTurnManeuver.step(dt, controlsOutput);

        renderer.drawCentered3dCube(Color.RED, car.position, 50);
        renderer.drawLine3d(Color.YELLOW, start, start.add(direction.mul(150)));

        if (simulationTime >= 2f / 60f)
            renderer.drawString2d(String.format("Arriving in: %.1f", simulationTime), Color.WHITE, new Point(400, 400), 2, 2);
        // Draw red hitbox on ground
        {
            BoxShape real_hitbox = car.hitbox;
            Vector3 hitbox = new Vector3(real_hitbox.length(), real_hitbox.width(), real_hitbox.height()).mul(1.5f);
            Color c = Color.RED;
            Vector3 p = start;
            Vector3 hitboxOffset = new Vector3(13.88f, 0f, 20.75f);
            Vector3 f = orientation.forward();
            Vector3 u = orientation.up();
            Vector3 r = orientation.left();
            p = p.add(f.mul(hitboxOffset.x)).add(r.mul(hitboxOffset.y)).add(u.mul(hitboxOffset.z));
            Vector3 fL = f.mul(hitbox.x / 2);
            Vector3 rW = r.mul(hitbox.y / 2);
            Vector3 uH = u.mul(hitbox.z / 2);
            renderer.drawLine3d(c, p.add(fL).add(uH).add(rW), p.add(fL).add(uH).sub(rW));
            renderer.drawLine3d(c, p.add(fL).sub(uH).add(rW), p.add(fL).sub(uH).sub(rW));
            renderer.drawLine3d(c, p.sub(fL).add(uH).add(rW), p.sub(fL).add(uH).sub(rW));
            renderer.drawLine3d(c, p.sub(fL).sub(uH).add(rW), p.sub(fL).sub(uH).sub(rW));
            renderer.drawLine3d(c, p.add(fL).add(uH).add(rW), p.sub(fL).add(uH).add(rW));
            renderer.drawLine3d(c, p.add(fL).sub(uH).add(rW), p.sub(fL).sub(uH).add(rW));
            renderer.drawLine3d(c, p.add(fL).add(uH).sub(rW), p.sub(fL).add(uH).sub(rW));
            renderer.drawLine3d(c, p.add(fL).sub(uH).sub(rW), p.sub(fL).sub(uH).sub(rW));
            renderer.drawLine3d(c, p.add(fL).add(uH).add(rW), p.add(fL).sub(uH).add(rW));
            renderer.drawLine3d(c, p.sub(fL).add(uH).add(rW), p.sub(fL).sub(uH).add(rW));
            renderer.drawLine3d(c, p.add(fL).add(uH).sub(rW), p.add(fL).sub(uH).sub(rW));
            renderer.drawLine3d(c, p.sub(fL).add(uH).sub(rW), p.sub(fL).sub(uH).sub(rW));
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.of(new DefaultStrategy());
    }
}
