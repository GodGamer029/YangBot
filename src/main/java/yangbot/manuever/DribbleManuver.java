package yangbot.manuever;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.BallPrediction;
import rlbot.flat.PredictionSlice;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

public class DribbleManuver extends Manuver {

    public Vector2 direction = null;
    private BallState state;
    private Vector3 ballPredictionPos = null;
    private Vector3 ballPredictionVelocity = null;
    private float ballArrival = 0;
    private Vector3 impact = null;
    public boolean hasBallPossession = false;
    private RecoverToGroundManuver recoverToGroundManuver;
    private DriveToPointManuver driveToPointManuver;

    enum BallState {
        FINDBALL,
        GOTOBALL,
        DRIBBLING
    }

    public DribbleManuver(){
        recoverToGroundManuver = new RecoverToGroundManuver();
        driveToPointManuver = new DriveToPointManuver();
        state = BallState.FINDBALL;
    }

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final BallData ball = gameData.getBallData();
        
        new AdvancedRenderer(3244).eraseFromScreen();
        dt = Math.max(dt, 1f/60f);
        float vf = (float) car.velocity.dot(car.forward());
        if(state == BallState.FINDBALL){
            try{
                BallPrediction ballPrediction = RLBotDll.getBallPrediction();
                for (int i = 0; i < ballPrediction.slicesLength(); i ++) {
                    PredictionSlice slice = ballPrediction.slices(i);
                    Vector3 loc = new Vector3(slice.physics().location());
                    Vector3 vel = new Vector3(slice.physics().velocity());

                    if(slice.gameSeconds() - car.elapsedSeconds < 0.2f)
                        continue;

                    if(vel.z < 0 &&
                            loc.z - 92.75f > car.position.z + 18f &&
                            loc.z - 92.75f < car.position.z + 30f){
                        ballPredictionPos = loc.add(vel.mul(dt));
                        ballPredictionVelocity = vel;
                        ballArrival = slice.gameSeconds();

                        state = BallState.GOTOBALL;
                        //System.out.println("Got a ball "+(slice.gameSeconds() - car.elapsedSeconds)+" seconds into the future");
                        break;
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }else if(state == BallState.GOTOBALL){

            float dist = (float) car.position.flatten().distance(ballPredictionPos.flatten());
            if(car.elapsedSeconds >= ballArrival || (dist < 15 && ball.position.z - 92.75f > car.position.z + 17f)){
                if(dist < 50){
                    //System.out.println("Starting dribble; "+dist);
                    state = BallState.DRIBBLING;
                }else{
                    //System.out.println("Refinding ball; "+dist);
                    state=BallState.FINDBALL;
                }
                return;
            }
            try{
                BallPrediction ballPrediction = RLBotDll.getBallPrediction();
                boolean weFoundTheBall = false;
                for (int i = 0; i < ballPrediction.slicesLength(); i ++) {
                    PredictionSlice slice = ballPrediction.slices(i);
                    Vector3 loc = new Vector3(slice.physics().location());
                    Vector3 vel = new Vector3(slice.physics().velocity());
                    if(vel.z < 0 && loc.z - 92.75f > car.position.z + 17f && loc.z - 92.75f < car.position.z + 40f && Math.abs(ballArrival - slice.gameSeconds()) < 0.2f){
                        weFoundTheBall = true;
                        ballPredictionPos = loc;
                        ballPredictionVelocity = vel;
                        ballArrival = slice.gameSeconds();
                        break;
                    }
                }
                if(!weFoundTheBall){
                    state = BallState.FINDBALL;
                   // System.out.println("Couldn't get to ball timeLeft: "+(ballArrival - car.elapsedSeconds));
                    return;
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            {
                float targetVelocity = Math.max(((float) ballPredictionVelocity.flatten().magnitude() - 30), 0);

                driveToPointManuver.targetPosition = ballPredictionPos.add(car.forward().mul(-10));
                driveToPointManuver.targetVelocity = 0;
                driveToPointManuver.step(dt, controlsOutput);
            }
        }else if(state == BallState.DRIBBLING){

            float toBallDistance = (float) car.position.flatten().distance(ball.position.flatten());
            if(toBallDistance > 200 || (ball.position.z - 92.75f < car.position.z + 10f && Math.abs(ball.velocity.z) < 0.05f)){
                state = BallState.FINDBALL;
                hasBallPossession = false;
                return;
            }

            if(car.hasWheelContact && car.up().angle(new Vector3(0, 0, 1)) < Math.PI / 10)
            {

                driveToPointManuver.targetPosition = new Vector3(ball.position.flatten().add(ball.velocity.flatten().mul(dt)), car.position.z);
                driveToPointManuver.targetVelocity = (float) ball.velocity.flatten().magnitude() - 30f;
                driveToPointManuver.step(dt, controlsOutput);
            }else{
                hasBallPossession = false;
                if(!car.hasWheelContact){
                    recoverToGroundManuver.orientationTarget = ball.position.flatten().sub(car.position.flatten()).normalized();
                    recoverToGroundManuver.step(dt, controlsOutput);
                }
                return;
            }

        }

        hasBallPossession = state == BallState.DRIBBLING && ball.position.z - 92.75f > car.position.z + 15f;
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
