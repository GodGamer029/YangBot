package yangbot.strategy.lac;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.model.ModelUtils;
import yangbot.strategy.DefaultStrategy;
import yangbot.strategy.RecoverStrategy;
import yangbot.strategy.Strategy;
import yangbot.strategy.abstraction.*;
import yangbot.util.AdvancedRenderer;
import yangbot.util.PosessionUtil;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class LACStrategy extends Strategy {

    private State state = State.INVALID;
    private Abstraction boostAbstraction;
    private Abstraction strikeAbstraction;
    private IdleAbstraction idleAbstraction = null;
    private boolean chargeAtBall = false;
    public boolean spoofNoBoost = false;
    public static final float MIN_HEIGHT_AERIAL = 600;
    public static  final float MAX_HEIGHT_AERIAL = RLConstants.arenaHeight - 200;
    private BallTouchInterrupt ballTouchInterrupt;

    private boolean planStrategyAttack() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final var ball = gameData.getBallData();
        final int teamSign = car.getTeamSign();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 100));
        final Vector2 ourGoal = new Vector2(0, teamSign * (RLConstants.goalDistance + 100));

        // Make sure we don't hit the ball back to our goal
        List<LACHelper.StrikeInfo> possibleStrikes = new ArrayList<>();

        AtomicReference<Float> myPosession = new AtomicReference<>(-5f);
        if(ballPrediction.getFrameAtRelativeTime(0.25f).map(f -> f.ballData).or(() -> Optional.of(ball))
                .get().position.flatten().distance(ourGoal) < 2500)
            myPosession.set(0f); // allow defensive action

        PosessionUtil.getPossession(gameData, ballPrediction)
                .ifPresent(p -> myPosession.set(Math.max(myPosession.get(), p)));

        if (car.boost > 10) {
            var aerialStrikeFrames = YangBallPrediction.from(ballPrediction.getFramesBetweenRelative(0.3f, 3.5f)
                    .stream()
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                    .filter((frame) -> frame.ballData.position.z >= MIN_HEIGHT_AERIAL && frame.ballData.position.z < MAX_HEIGHT_AERIAL)
                    .filter(f -> {
                        if(Math.signum(f.ballData.position.y) == car.getTeamSign())
                            return true; // defense
                        return !RLConstants.isPosOnBackWall(f.ballData.position, 50 + 2 * BallData.COLLISION_RADIUS); // don't pinch the ball on the enemy wall
                    })
                    .collect(Collectors.toList()), ballPrediction.tickFrequency);

            var airStrike = LACHelper.planAerialIntercept(aerialStrikeFrames, false);
            airStrike.ifPresent(possibleStrikes::add);
        }

        if(myPosession.get() > -1f)
        {
            List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.25f, 3.5f)
                    .stream()
                    .filter((frame) -> (frame.ballData.position.z <= DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT))
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                    .filter(f -> {
                        if(Math.signum(f.ballData.position.y) == car.getTeamSign())
                            return true; // defense
                        if(f.ballData.velocity.magnitudeF() < 100 && f.ballData.position.z < BallData.COLLISION_RADIUS * 2)
                            return true;
                        return !RLConstants.isPosOnBackWall(f.ballData.position, 50 + 2 * BallData.COLLISION_RADIUS); // don't pinch the ball on the enemy wall
                    })
                    .collect(Collectors.toList());

            if (!strikeableFrames.isEmpty()) {
                YangBallPrediction strikePrediction = YangBallPrediction.from(strikeableFrames, RLConstants.tickFrequency);
                var groundStrike = LACHelper.planGroundStrike(strikePrediction);
                groundStrike.ifPresent(possibleStrikes::add);
            }
        }

        if(myPosession.get() > 0)
        {
            List<YangBallPrediction.YangPredictionFrame> chipableFrames = ballPrediction.getFramesBetweenRelative(0.25f, 3.5f)
                    .stream()
                    .filter((frame) -> frame.ballData.position.z <= DriveChipAbstraction.MAX_CHIP_HEIGHT && (frame.ballData.velocity.z > 0 || Math.abs(frame.ballData.velocity.z) < 10))
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                    .filter(frame -> frame.ballData.position.distance(car.position) > BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent() + 100)
                    .collect(Collectors.toList());

            if (!chipableFrames.isEmpty()) {
                YangBallPrediction strikePrediction = YangBallPrediction.from(chipableFrames, RLConstants.tickFrequency);
                var groundStrike = LACHelper.planChipStrike(strikePrediction);
                groundStrike.ifPresent(possibleStrikes::add);
            }
        }

        if (possibleStrikes.isEmpty()) {
            this.state = State.IDLE;
            return false;
        }

        assert this.state == State.INVALID;

        var nextStrike = possibleStrikes
                .stream()
                .map(s -> {
                    float val = s.timeAtStrike - car.elapsedSeconds;
                    var frame = ballPrediction.getFrameAtAbsoluteTime(s.timeAtStrike);

                    switch (s.strikeType) {
                        case CHIP:
                            if (frame.isPresent() && frame.get().ballData.position.flatten().distance(enemyGoal) < 2000) {
                                val *= 1.3f;
                                break; // Don't favor shots that are very close to their goal, we might overshoot it!
                            }

                            val *= 0.9f;
                            val -= 0.3f;
                            break;
                        case DODGE:
                            if(frame.isPresent()){
                                var b = frame.get();
                                if(Math.abs(b.ballData.position.y) > RLConstants.arenaHalfLength - 80 - BallData.COLLISION_RADIUS &&
                                        Math.abs(b.ballData.position.x) > RLConstants.goalCenterToPost)
                                    val *= 1.3f; // dont pinch it near the goal wall *duh*
                            }
                            break;
                    }

                    return new Tuple<>(s, val);
                })
                .min(Comparator.comparingDouble(Tuple::getValue))
                .map(Tuple::getKey).get();

        this.strikeAbstraction = nextStrike.execute();
        this.state = State.STRIKE;

        assert this.state != State.INVALID;
        return true;
    }

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();

        this.state = State.INVALID;
        this.chargeAtBall = false;
        this.idleAbstraction = null;
        this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt();

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        var gameError = Math.abs(ModelUtils.gameStateToPrediction(gameData, true, true) - car.team);
        if (!car.getPlayerInfo().isActiveShooter() || (car.boost < 60 && gameError < 0.5f)){
            var o = LACHelper.planGoForBoost();
            if(o.isPresent() && !this.spoofNoBoost){
                this.state = State.GET_BOOST;
                this.boostAbstraction = o.get();
                return;
            }
        }
        // determine the car that is supposed to shoot
        var attackingCar = LACHelper.getAttackingCar();

        if (attackingCar.isPresent() && attackingCar.get().playerIndex == car.playerIndex || true) {
            if (this.planStrategyAttack()) {
               return;
            }
            // Determine if we should rather rotate back
            if (car.velocity.magnitude() < 1300 && car.position.flatten().distance(ball.position.flatten()) < 1000 && Math.signum(car.position.y) == -teamSign){
                RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_AllYours);
                car.getPlayerInfo().setInactiveShooterUntil(car.elapsedSeconds + 2f);
            }
        }

        this.state = State.IDLE;
        if(!this.chargeAtBall){
            if(this.idleAbstraction == null) {
                this.idleAbstraction = new IdleAbstraction();
                this.idleAbstraction.targetSpeed = 2000;
                this.idleAbstraction.minIdleDistance = 2000;
                this.idleAbstraction.maxIdleDistance = RLConstants.arenaLength * 0.5f;
                this.idleAbstraction.retreatPercentage = 0.8f;
            }

            if(!car.getPlayerInfo().isActiveShooter() || car.boost < 30)
                this.idleAbstraction.forceRetreatTimeout = 0.5f;
        }
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        var oldState = state;
        assert !this.reevaluateStrategy(4f) : "States/Abstractions didn't finish automatically! (" + oldState.name() + ")";
        assert this.state != State.INVALID : "Invalid state! ";

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        switch (this.state) {
            case GET_BOOST: {
                if (this.boostAbstraction.canInterrupt() && this.reevaluateStrategy(this.ballTouchInterrupt, 3.5f))
                    return;

                if (this.reevaluateStrategy(3.9f))
                    return;

                if (this.boostAbstraction.step(dt, controlsOutput).isDone()) {
                    car.getPlayerInfo().setInactiveShooterUntil(1.5f);
                    RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_AllYours);

                    this.reevaluateStrategy(0);
                    return;
                }
            }
            break;
            case STRIKE: {
                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(3.5f)){
                    car.getPlayerInfo().setInactiveShooterUntil(car.elapsedSeconds + 2f);
                    RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_AllYours);
                    return;
                }

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt)){
                    return;
                }

                this.strikeAbstraction.draw(renderer);
                this.strikeAbstraction.step(dt, controlsOutput);

                if (this.strikeAbstraction.isDone() && this.reevaluateStrategy(0)) {
                    return;
                }
            }
            break;
            case IDLE: {

                if(this.chargeAtBall){
                    DefaultStrategy.smartBallChaser(dt, controlsOutput, true);
                }else{
                    this.idleAbstraction.step(dt, controlsOutput);
                }

                if (this.reevaluateStrategy((this.chargeAtBall || this.idleAbstraction.canInterrupt()) ? 0.05f : 2.5f))
                    return;

                if (this.reevaluateStrategy(ballTouchInterrupt, (this.chargeAtBall || this.idleAbstraction.canInterrupt()) ? 0.05f : 2.5f))
                    return;
            }
            break;
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        if (!GameData.current().getCarData().hasWheelContact)
            return Optional.of(new RecoverStrategy());

        return Optional.empty();
    }

    @Override
    public String getAdditionalInformation() {
        float active = (GameData.current().getCarData().getPlayerInfo().getInactiveShooterUntil() - GameData.current().getCarData().elapsedSeconds);
        if (active < 0)
            active = 0;
        var attacking = LACHelper.getAttackingCar();
        String attStr = attacking.map(carData -> (GameData.current().getCarData().playerIndex == carData.playerIndex ? "me" : ""+carData.playerIndex))
                .orElse("none");
        return "State: " + this.state + "\nActive in: " + active+"\nAttacker: "+attStr;
    }

    /***
     * Ok here's the plan:
     *  - Grab Boost
     *  - Shoot
     *  - Return to defensive third
     *  - Repeat
     * Profit!
     *
     * Almost guarantees that at least 1 bot will always be moving towards the ball in 3v3
     */

    enum State {
        IDLE,
        GET_BOOST,
        STRIKE,
        INVALID,
    }


}
