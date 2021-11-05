package yangbot.strategy.lac;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.graders.ValueNetworkGrader;
import yangbot.path.EpicMeshPlanner;
import yangbot.strategy.DefaultStrategy;
import yangbot.strategy.RecoverStrategy;
import yangbot.strategy.Strategy;
import yangbot.strategy.abstraction.*;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.PosessionUtil;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.ScenarioUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class LACStrategy extends Strategy {

    private State state = State.INVALID;
    private Abstraction boostAbstraction;
    private Abstraction strikeAbstraction;
    private IdleAbstraction idleAbstraction;
    private boolean chargeAtBall = false;
    public boolean spoofNoBoost = false;
    public static final float MIN_HEIGHT_AERIAL = 550;
    public static  final float MAX_HEIGHT_AERIAL = RLConstants.arenaHeight - 200;
    private BallTouchInterrupt ballTouchInterrupt;

    private boolean planStrategyAttack() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 100));

        // Make sure we don't hit the ball back to our goal
        List<LACHelper.StrikeInfo> possibleStrikes = new ArrayList<>();

        var myPosession = PosessionUtil.getPossession(gameData, ballPrediction);

        if (car.boost > 10) {
            var aerialStrikeFrames = YangBallPrediction.from(ballPrediction.getFramesBetweenRelative(0.3f, 3.5f)
                    .stream()
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                    .filter((frame) -> (frame.ballData.position.z >= MIN_HEIGHT_AERIAL && frame.ballData.position.z < MAX_HEIGHT_AERIAL)
                            && !frame.ballData.makeMutable().isInAnyGoal())
                    .collect(Collectors.toList()), ballPrediction.tickFrequency);

            var airStrike = LACHelper.planAerialIntercept(aerialStrikeFrames, false);
            airStrike.ifPresent(possibleStrikes::add);
        }

        if(myPosession.isPresent() && myPosession.get() > -0.5f)
        {
            List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.25f, 3.5f)
                    .stream()
                    .filter((frame) -> (frame.ballData.position.z <= DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT))
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                    .collect(Collectors.toList());

            if (!strikeableFrames.isEmpty()) {
                YangBallPrediction strikePrediction = YangBallPrediction.from(strikeableFrames, RLConstants.tickFrequency);
                var groundStrike = LACHelper.planGroundStrike(strikePrediction);
                groundStrike.ifPresent(possibleStrikes::add);
            }
        }

        if(myPosession.isPresent() && myPosession.get() > 0.25f)
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
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        this.state = State.INVALID;
        this.chargeAtBall = false;
        this.idleAbstraction = null;
        this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt();

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        if (!car.getPlayerInfo().isActiveShooter() || car.boost < 70){
            var o = LACHelper.planGoForBoost();
            if(o.isPresent() && !this.spoofNoBoost){
                this.state = State.GET_BOOST;
                this.boostAbstraction = o.get();
                return;
            }
        }
        // determine the car that is supposed to shoot
        var attackingCar = LACHelper.getAttackingCar();

        if (attackingCar.isPresent() && attackingCar.get().playerIndex == car.playerIndex) {
            if (this.planStrategyAttack()) {
               return;
            }
            // Determine if we should rather rotate back
            if (car.velocity.magnitude() < 1300 && car.position.flatten().distance(ball.position.flatten()) < 1000 && Math.signum(car.position.y) == -teamSign){
                RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_AllYours);
                car.getPlayerInfo().setInactiveShooterUntil(car.elapsedSeconds + 2f);
            }
            //else
              //  this.chargeAtBall = true;
        }

        this.state = State.IDLE;
        if(!this.chargeAtBall){
            this.idleAbstraction = new IdleAbstraction();
            this.idleAbstraction.targetSpeed = 2000;
            this.idleAbstraction.minIdleDistance = 800;
            this.idleAbstraction.maxIdleDistance = RLConstants.arenaLength * 0.65f;
            this.idleAbstraction.retreatPercentage = 0.8f;
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

                if (this.reevaluateStrategy((this.chargeAtBall || this.idleAbstraction.canInterrupt()) ? 0.05f : 2.5f))
                    return;

                if (this.reevaluateStrategy(ballTouchInterrupt, (this.chargeAtBall || this.idleAbstraction.canInterrupt()) ? 0.05f : 2.5f))
                    return;

                if(this.chargeAtBall){
                    DefaultStrategy.smartBallChaser(dt, controlsOutput, true);
                }else{
                    this.idleAbstraction.step(dt, controlsOutput);
                }

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