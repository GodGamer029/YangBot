package yangbot.strategy.abstraction;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.strategy.manuever.AerialGroundPrepManeuver;
import yangbot.strategy.manuever.AerialManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.Comparator;
import java.util.stream.Collectors;

public class AerialAbstraction extends Abstraction {

    private static final float MAX_TIME_GROUND_ADJUSTMENTS = 1f;
    private final AerialManeuver aerialManeuver;
    private final AerialGroundPrepManeuver groundPrepManeuver;
    public Vector3 targetPos;
    public Vector3 targetOrientPos = null;
    public float arrivalTime = 0;
    private float timer = 0;
    private State state;
    private float nextOptimizeAt = -1;

    private YangBallPrediction.YangPredictionFrame targetSlice = null;

    public AerialAbstraction(Vector3 targetPos) {
        this.targetPos = targetPos;
        this.state = State.DRIVE;
        this.aerialManeuver = new AerialManeuver();
        this.aerialManeuver.target = this.targetPos;
        this.groundPrepManeuver = new AerialGroundPrepManeuver();
        this.groundPrepManeuver.targetPos = this.targetPos;
    }

    public void setTargetSlice(YangBallPrediction.YangPredictionFrame targetSlice) {
        assert Math.abs(this.arrivalTime - targetSlice.absoluteTime) < 0.05f;
        this.targetSlice = targetSlice;
        this.nextOptimizeAt = this.arrivalTime - targetSlice.relativeTime;
    }

    // Does a full simulation of the flight path including ground preparations
    public static boolean isViable(CarData carData, Vector3 target, float absoluteArrival) {
        float t = 0;
        final float dt = RLConstants.tickFrequency;
        final float relativeArrival = absoluteArrival - carData.elapsedSeconds;

        var simCar = new CarData(carData);

        if (simCar.hasWheelContact) { // Do on-ground adjustments
            var simGround = new AerialGroundPrepManeuver();
            simGround.arrivalTime = absoluteArrival;
            simGround.targetPos = target;
            var fool = GameData.current().fool();
            simGround.fool(fool);
            do {

                var controls = new ControlsOutput();
                fool.foolCar(simCar);
                simGround.step(dt, controls);

                float vF = simCar.forwardSpeed();
                float vR = simCar.velocity.dot(simCar.right());
                var angUp = simCar.angularVelocity.dot(simCar.up());
                float forwardForce = CarData.driveForceForward(controls, vF, vR, angUp);
                float leftForce = CarData.driveForceLeft(controls, vF, vR, angUp);
                var upTorque = simCar.up().mul(CarData.driveTorqueUp(controls, vF, angUp));
                simCar.velocity = simCar.velocity
                        .add(simCar.forward().mul(forwardForce).mul(dt))
                        .add(simCar.right().mul(leftForce).mul(dt))
                        .withZ(0);
                simCar.position = simCar.position.add(simCar.velocity.mul(dt));
                simCar.angularVelocity = simCar.angularVelocity.add(upTorque.mul(dt));
                simCar.orientation = Matrix3x3.axisToRotation(simCar.angularVelocity.mul(dt)).matrixMul(simCar.orientation);
                simCar.elapsedSeconds += dt;
                t += dt;

            } while (!simGround.isDone() && t < MAX_TIME_GROUND_ADJUSTMENTS && t < relativeArrival);
        }
        if (t >= relativeArrival)
            return false;

        var simAerial = new AerialManeuver();
        simAerial.arrivalTime = absoluteArrival;
        simAerial.target = target;

        return simAerial.isViable(simCar, 100);
    }

    public RunState updateOptimizeAerial(){

        assert this.targetSlice != null;

        // find original ball back
        final GameData g = GameData.current();
        final YangBallPrediction bp = g.getBallPrediction();
        final CarData c = g.getCarData();

        float relative = this.targetSlice.absoluteTime - c.elapsedSeconds;
        assert relative >= 0;
        if(relative < 0.3f)
            return RunState.CONTINUE;

        float bound = MathUtils.clip(relative * 0.15f, 0.2f, 0.4f);
        var frames = bp.getFramesBetweenRelative(relative - bound, relative + bound);
        assert !frames.isEmpty();

        var sortedFrames = frames.stream()
                .map(f -> new Tuple<>(f, f.ballData.position.distance(this.targetSlice.ballData.position)))
                .sorted(Comparator.comparingDouble(Tuple::getValue))
                .collect(Collectors.toList());
        var bestFrame = sortedFrames.get(0);
        float tolerance = 0.1f;
        if(bestFrame.getValue() < tolerance * Math.max(500, bestFrame.getKey().ballData.velocity.magnitude())
        && Math.abs(bestFrame.getKey().absoluteTime - this.targetSlice.absoluteTime) < tolerance){
            return RunState.CONTINUE;
        }

        var it = sortedFrames.iterator();
        float lastT = -1;
        while(it.hasNext()){
            var e = it.next();
            if(e.getKey().relativeTime - lastT < RLConstants.simulationTickFrequency * 0.9f * 2){
                it.remove();
                continue;
            }
            lastT = e.getKey().relativeTime;
        }

        var newFrame = sortedFrames.stream()
                .map(Tuple::getKey)
                .map(f -> {
                    var oldOffset = this.targetPos.sub(this.targetSlice.ballData.position);
                    var newTarget = f.ballData.position.add(oldOffset);
                    return new Tuple<>(f, newTarget);
                })
                .filter(f -> AerialAbstraction.isViable(c, f.getValue(), f.getKey().absoluteTime))
                .findFirst();

        if(newFrame.isEmpty()){
            System.out.println(c.playerIndex+": > Aerial No longer viable t="+relative+" o="+bestFrame.getValue()+" tDiff="+(Math.abs(bestFrame.getKey().absoluteTime - this.targetSlice.absoluteTime)/ RLConstants.tickFrequency)+" tol="+(tolerance * Math.max(1000, bestFrame.getKey().ballData.velocity.magnitude()) * Math.max(RLConstants.tickFrequency, bp.tickFrequency)));
            return RunState.FAILED;
        }
        System.out.println(c.playerIndex+": > Optimized aerial target t="+targetSlice.relativeTime);

        this.targetSlice = newFrame.get().getKey();
        this.arrivalTime = targetSlice.absoluteTime;
        this.targetPos = newFrame.get().getValue();
        this.targetOrientPos = targetSlice.ballData.position;
        return RunState.CONTINUE;
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        this.timer += dt;
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        this.aerialManeuver.target = this.targetPos;
        this.aerialManeuver.arrivalTime = this.arrivalTime;
        this.groundPrepManeuver.targetPos = this.targetPos;
        this.groundPrepManeuver.arrivalTime = this.arrivalTime;

        switch (this.state) {
            case DRIVE: {
                this.groundPrepManeuver.step(dt, controlsOutput);

                if(this.groundPrepManeuver.isDone() || this.timer > MAX_TIME_GROUND_ADJUSTMENTS){
                    this.state = State.FLY; // fallthrough to fly case
                    if (this.targetOrientPos != null)
                        this.aerialManeuver.setTarget_orientation(Matrix3x3.lookAt(targetOrientPos.sub(this.targetPos), car.up()));
                }else
                    return RunState.CONTINUE;
            }
            case FLY:
                this.aerialManeuver.step(dt, controlsOutput);
                if (this.aerialManeuver.isDone() || car.elapsedSeconds > this.arrivalTime + 0.1f)
                    return RunState.DONE;

                if(!car.hasWheelContact && car.doubleJumped && car.elapsedSeconds >= this.nextOptimizeAt && this.nextOptimizeAt > 0){
                    var newState = this.updateOptimizeAerial();
                    if(newState.isDone())
                        return newState;
                    if(this.arrivalTime - car.elapsedSeconds >= 0.5f)
                        this.nextOptimizeAt = car.elapsedSeconds + 0.1f;
                    else
                        this.nextOptimizeAt = -1;
                }

                return RunState.CONTINUE;
        }
        return RunState.DONE;
    }

    @Override
    public void draw(AdvancedRenderer renderer) {
        var car = GameData.current().getCarData();
        var deltaX = AerialManeuver.getDeltaX(car, this.targetPos, arrivalTime);
        renderer.drawString2d("State: " + this.state.name(), Color.WHITE, new Point(400, 300), 2, 2);
        var bean = AerialManeuver.getDeltaX(car, new Vector3(), arrivalTime, 0.2f - this.aerialManeuver.doubleJump.timer).mul(-1);
        renderer.drawCentered3dCube(Color.RED, bean, 50);
        renderer.drawCentered3dCube(Color.RED, bean, 120);

        renderer.drawCentered3dCube(Color.GREEN, targetPos, 50);
        renderer.drawCentered3dCube(Color.GREEN, targetPos, 120);

        if(targetOrientPos != null){
            renderer.drawCentered3dCube(Color.MAGENTA, targetOrientPos, 50);
            renderer.drawCentered3dCube(Color.MAGENTA, targetOrientPos, 120);
        }

        renderer.drawLine3d(Color.RED, car.position, car.position.add(deltaX.normalized().mul(100)));
        var lookPos = this.targetPos;
        if (this.targetOrientPos != null)
            lookPos = this.targetOrientPos;
        renderer.drawLine3d(Color.BLUE, car.position, car.position.add(lookPos.sub(this.targetPos.sub(deltaX)).normalized().mul(100)));

    }

    enum State {
        DRIVE,
        FLY
    }
}
