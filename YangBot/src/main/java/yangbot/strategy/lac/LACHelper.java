package yangbot.strategy.lac;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.optimizers.graders.ValueNetworkGrader;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.abstraction.*;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.PosessionUtil;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.ScenarioUtil;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class LACHelper {

    public static class StrikeInfo {
        public final float timeAtStrike;
        public final StrikeInfo.StrikeType strikeType;
        private final Function<Object, Abstraction> executor;

        private StrikeInfo(float timeAtStrike, StrikeInfo.StrikeType strikeType, Function<Object, Abstraction> executor) {
            this.timeAtStrike = timeAtStrike;
            this.strikeType = strikeType;
            this.executor = executor;
        }

        public Abstraction execute() {
            return this.executor.apply(null);
        }

        enum StrikeType {
            AERIAL,
            DODGE,
            CHIP
        }
    }

    public static Optional<CarData> getAttackingCar(){
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final var ballPrediction = gameData.getBallPrediction();
        final var ball = gameData.getBallData();
        var teamSign = car.getTeamSign();
        return gameData.getAllCars().stream()
                .filter(c -> c.team == car.team)
                //.filter(c -> c.boost > 30)
                .filter(c -> c.hasWheelContact)
                .filter(c -> c.getPlayerInfo().isActiveShooter())
                .filter(c -> (Math.abs(c.velocity.y) < 300 || Math.signum(c.velocity.y) == -teamSign || Math.signum(c.position.y) == teamSign))
                .filter(c -> c.position.y * teamSign > ball.position.y * teamSign || (ball.position.y == teamSign && teamSign * ball.position.y > RLConstants.goalDistance * 0.5f))
                /*.map(c ->{
                    var clone = new CarData(c);
                    clone.smartPrediction(0.3f);
                    return new Tuple<>(c, clone);
                })
                .min(Comparator.comparingDouble(c -> c.getValue().position.y * teamSign))*/
                .map(c -> new Tuple<>(c, PosessionUtil.timeToBall(c, ballPrediction)))
                .min(Comparator.comparingDouble(Tuple::getValue))
                .map(Tuple::getKey);
    }

    public static Optional<Abstraction> planGoForBoost() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        var boostAbstraction = new GetBoostAbstraction();
        boostAbstraction.ignoreTeammates = false;
        if (!boostAbstraction.isViable()) {
            return Optional.empty();
        }

        RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_NeedBoost);
        return Optional.of(boostAbstraction);
    }

    public static  Optional<StrikeInfo> planAerialIntercept(YangBallPrediction ballPrediction, boolean debug) {
        if(ballPrediction.isEmpty())
            return Optional.empty();
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final Vector2 ownGoal = new Vector2(0, car.getTeamSign() * RLConstants.goalDistance);
        final Vector2 enemyGoal = new Vector2(0, -car.getTeamSign() * RLConstants.goalDistance);

        float t = DodgeManeuver.max_duration + 0.1f;

        // Find intercept
        do {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = ballPrediction.getFrameAfterRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
            if (interceptFrame.ballData.isInAnyGoal())
                break;

            final Vector3 ballPos = interceptFrame.ballData.position;
            final var carToBall = ballPos.sub(car.position).normalized();
            final var goalToBall = (ballPos.sub(ownGoal.withZ(100)).normalized())
                    .add(enemyGoal.withZ(100).sub(ballPos).normalized())
                    .normalized();
            final Vector3 targetOffset = carToBall.add(goalToBall).normalized();
            final Vector3 targetPos = ballPos.sub(targetOffset.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent() * 0.95f - 10));

            boolean isPossible = AerialAbstraction.isViable(car, targetPos, interceptFrame.absoluteTime);
            if (isPossible) {
                return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.AERIAL, (o) -> {
                    System.out.println(car.playerIndex+": Executing Aerial << at t="+interceptFrame.relativeTime+" z="+ballPos.z+" >> "+ ScenarioUtil.getEncodedGameState(gameData));

                    var aerialAbstraction = new AerialAbstraction(targetPos);
                    aerialAbstraction.targetOrientPos = ballPos;
                    aerialAbstraction.arrivalTime = interceptFrame.absoluteTime;
                    aerialAbstraction.setTargetSlice(interceptFrame);
                    return aerialAbstraction;
                }));
            }

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s
        } while (t < ballPrediction.relativeTimeOfLastFrame());

        return Optional.empty();
    }

    public static Optional<StrikeInfo> planGroundStrike(YangBallPrediction strikePrediction) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        int teamSign = car.getTeamSign();
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 200));
        float maxT = strikePrediction.relativeTimeOfLastFrame() - RLConstants.simulationTickFrequency * 2;
        float t = strikePrediction.firstFrame().relativeTime;

        final float goalCenterToPostDistance = RLConstants.goalCenterToPost - BallData.COLLISION_RADIUS * 2 - 50 /* tolerance */;
        assert goalCenterToPostDistance > 100; // Could fail with smaller goals
        assert enemyGoal.x == 0; // Could fail with custom goals
        final Line2 enemyGoalLine = new Line2(enemyGoal.sub(goalCenterToPostDistance, 0), enemyGoal.add(goalCenterToPostDistance, 0));

        final boolean verboseDebug = false;

        if (verboseDebug)
            System.out.println("##### start path finder");

        // Path finder
        while (t < maxT) {
            final var interceptFrameOptional = strikePrediction.getFrameAtRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;
            final var interceptFrame = interceptFrameOptional.get();

            t = interceptFrame.relativeTime;

            if (t > 1.5f) // Speed it up, not as important
                t += RLConstants.simulationTickFrequency * 4; // 15hz
            else // default
                t += RLConstants.simulationTickFrequency * 2; // 30hz
            if (interceptFrame.ballData.isInAnyGoal())
                break;

            if (interceptFrame.ballData.velocity.magnitude() > 4000)
                continue;

            final Vector3 targetBallPos = interceptFrame.ballData.position;

            //if(car.position.distance(targetBallPos) - BallData.COLLISION_RADIUS - car.hitbox.getForwardExtent() - 50 > CarData.MAX_VELOCITY * interceptFrame.relativeTime * 1.1f)
            //    continue; // impossible to get there in time

            float jumpDelay;
            {
                float zDiff = targetBallPos.z - BallData.COLLISION_RADIUS * 0.2f - car.position.z;

                if (zDiff < 5)
                    jumpDelay = 0.2f;
                else
                    jumpDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z) + 0.1f, 0.2f, 1.1f);

                if (interceptFrame.relativeTime < jumpDelay)
                    continue;
            }

            final Vector2 closestScoringPosition = enemyGoalLine.center();
            final Vector3 carToBall = targetBallPos.sub(car.position).normalized().withZ(0);
            final Vector3 ballTargetToGoalTarget = closestScoringPosition.sub(targetBallPos.flatten()).normalized().withZ(0);

            final Vector3 ballHitTarget = targetBallPos.sub(
                    carToBall.add(ballTargetToGoalTarget.mul(2))
                                    .normalized()
                                    .mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent())
            ).withZ(RLConstants.carElevation);

            final Vector3 carToDriveTarget = ballHitTarget.sub(car.position).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(1).add(ballTargetToGoalTarget.mul(1)).withZ(0).normalized();

            Function<Optional<SegmentedPath>, Optional<StrikeInfo>> testPath = (Optional<SegmentedPath> p) -> {
                if (p.isEmpty())
                    return Optional.empty();

                var currentPath = p.get();

                if(currentPath.getEndSpeed() < 400)
                    return Optional.empty();

                float tEstimate = currentPath.getTotalTimeEstimate();

                if (tEstimate > interceptFrame.relativeTime || Math.abs(tEstimate - interceptFrame.relativeTime) > 1.5f){
                    if (verboseDebug) {
                        StringBuilder s = new StringBuilder("continue path finder at t=" + interceptFrame.relativeTime + " with path total=" + currentPath.getTotalTimeEstimate() + " ");
                        for (var seg : currentPath.getSegmentList()) {
                            var est = seg.getTimeEstimate();
                            s.append(seg.getClass().getSimpleName()).append(" est=").append(est).append(" ");
                        }

                        System.out.println(s);
                    }
                    return Optional.empty();
                }

                if (verboseDebug){
                    System.out.print("##### end path finder at t=" + interceptFrame.relativeTime + " with path total=" + currentPath.getTotalTimeEstimate() + " ");
                    for (var seg : currentPath.getSegmentList()) {
                        var est = seg.getTimeEstimate();
                        System.out.print(seg.getClass().getSimpleName() + " est="+est+" ");
                    }
                    System.out.println();
                }
                return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.DODGE, (o) -> {

                    var dodgeStrikeAbstraction = new DriveDodgeStrikeAbstraction(currentPath, new ValueNetworkGrader());

                    dodgeStrikeAbstraction.arrivalTime = interceptFrame.absoluteTime;
                    dodgeStrikeAbstraction.originalTargetBallPos = targetBallPos;

                    dodgeStrikeAbstraction.jumpBeforeStrikeDelay = jumpDelay;

                    System.out.println(car.playerIndex+": Executing DodgeStrike with jumpBeforeStrikeDelay=" + dodgeStrikeAbstraction.jumpBeforeStrikeDelay + "<< "+ ScenarioUtil.getEncodedGameState(gameData));

                    dodgeStrikeAbstraction.strikeAbstraction.optimizer.maxJumpDelay = MathUtils.clip(dodgeStrikeAbstraction.jumpBeforeStrikeDelay + 0.3f, 0.6f, DodgeManeuver.startTimeout);
                    dodgeStrikeAbstraction.strikeAbstraction.optimizer.jumpDelayStep = 0.1f;
                    return dodgeStrikeAbstraction;
                }));
            };

            var potentialStrike = testPath.apply(
                    new EpicMeshPlanner()
                        .withStart(car, 0.05f)
                        .withEnd(ballHitTarget, endTangent)
                        .withArrivalTime(interceptFrame.absoluteTime)
                        .withArrivalSpeed(2300)
                        .allowOptimize(false)
                        .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANG_ARC)
                    .plan()
            ).or(() -> testPath.apply(
                    new EpicMeshPlanner()
                        .withStart(car, 0.05f)
                        .withEnd(ballHitTarget, endTangent)
                        .withArrivalTime(interceptFrame.absoluteTime)
                        .withArrivalSpeed(2300)
                        .allowOptimize(false)
                        .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                        .plan()
            ));

            if(potentialStrike.isPresent())
                return potentialStrike;
        }
        if (verboseDebug)
            System.out.println("##### end path finder");
        return Optional.empty();
    }

    public static Optional<StrikeInfo> planChipStrike(YangBallPrediction strikePrediction) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        int teamSign = car.getTeamSign();
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));
        float maxT = strikePrediction.relativeTimeOfLastFrame();
        float t = strikePrediction.firstFrame().relativeTime;

        final float goalCenterToPostDistance = RLConstants.goalCenterToPost - BallData.COLLISION_RADIUS * 2 - 50 /* tolerance */;
        assert goalCenterToPostDistance > 100; // Could fail with smaller goals
        assert enemyGoal.x == 0; // Could fail with custom goals
        final Line2 enemyGoalLine = new Line2(enemyGoal.sub(goalCenterToPostDistance, 0), enemyGoal.add(goalCenterToPostDistance, 0));

        // Path finder
        while (t < maxT) {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = strikePrediction.getFrameAtRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            if (t > 1.5f) // Speed it up, not as important
                t += RLConstants.simulationTickFrequency * 4; // 15hz
            else // default
                t += RLConstants.simulationTickFrequency * 2; // 30hz

            final var interceptFrame = interceptFrameOptional.get();
            if (interceptFrame.ballData.isInAnyGoal())
                break;

            var ballVel = interceptFrame.ballData.velocity;

            if (ballVel.z < -10)
                continue;

            if (ballVel.magnitude() > 4000)
                continue;

            final Vector3 targetBallPos = interceptFrame.ballData.position;

            final Vector2 closestScoringPosition = enemyGoalLine.closestPointOnLine(targetBallPos.flatten());
            final Vector3 ballTargetToGoalTarget = closestScoringPosition.sub(targetBallPos.flatten()).normalized().withZ(0);

            var ballExtension = DriveChipAbstraction.getBallExtension(targetBallPos.z);
            if (ballExtension == -1)
                continue;

            Vector3 ballHitTarget = targetBallPos.sub(ballTargetToGoalTarget.mul(ballExtension + car.hitbox.getForwardExtent()));
            ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);

            final Vector3 carToDriveTarget = ballHitTarget.sub(car.position).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(3).add(ballTargetToGoalTarget).withZ(0).normalized();

            var currentPathOptional = new EpicMeshPlanner()
                    .withStart(car, 0.05f)
                    .withEnd(ballHitTarget, endTangent)
                    .withArrivalTime(interceptFrame.absoluteTime)
                    .withArrivalSpeed(2300)
                    .allowOptimize(car.boost < 30)
                    .withCreationStrategy(car.position.distance(ballHitTarget) > 2000 ? EpicMeshPlanner.PathCreationStrategy.YANG_ARC : EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .plan();

            if (currentPathOptional.isEmpty())
                continue;

            var currentPath = currentPathOptional.get();

            if (ballTargetToGoalTarget.dot(currentPath.getEndTangent()) < 0)
                continue;

            final Vector2 hitTangent = targetBallPos.flatten().sub(ballHitTarget.flatten()).normalized();
            if (hitTangent.dot(currentPath.getEndTangent().flatten().normalized()) < 0.6f)
                continue; // We should be driving at the ball, otherwise the code fails horribly

            if(currentPath.getEndSpeed() < 800)
                continue;

            var relativeVel = currentPath.getEndTangent().mul(currentPath.getEndSpeed()).sub(interceptFrame.ballData.velocity).flatten();
            if (relativeVel.magnitude() < 1400)
                continue; // We want boomers, not atbas

            // Check if path is valid
            {
                if (currentPath.getTotalTimeEstimate() <= interceptFrame.relativeTime) {
                    return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.CHIP, (o) -> {
                        System.out.println(car.playerIndex+": Executing Chip << relative vel: "+relativeVel.magnitude() + " endSpeed="+currentPath.getEndSpeed() + " endTang="+currentPath.getEndTangent()+" ballV="+interceptFrame.ballData.velocity+" ballP="+interceptFrame.ballData.position);
                        var chipStrikeAbstraction = new DriveChipAbstraction(currentPath);

                        chipStrikeAbstraction.arrivalTime = interceptFrame.absoluteTime;
                        chipStrikeAbstraction.originalTargetBallPos = targetBallPos;

                        return chipStrikeAbstraction;
                    }));
                }
            }
        }
        return Optional.empty();
    }

}
