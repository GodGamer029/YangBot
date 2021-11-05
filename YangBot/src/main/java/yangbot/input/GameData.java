package yangbot.input;

import rlbot.flat.GameInfo;
import yangbot.input.interrupt.InterruptManager;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameData {

    public static float timeOfMatchStart = -1f;

    protected CarData carData = null;
    protected ImmutableBallData ballData = null;
    protected List<CarData> allCars = null;
    protected float gravityZ = -650;
    protected float elapsedSeconds = 0;
    protected float dt = 1 / 60f;
    protected GameInfoData gameInfoData = null;
    protected AdvancedRenderer advancedRenderer = null;
    private int botIndex = 0;
    private static final Map<Long, GameData> botLoopMap = new ConcurrentHashMap<>();
    private YangBallPrediction ballPrediction = null;

    public GameData(Long threadId) {
    }

    public static GameData current() {
        botLoopMap.computeIfAbsent(Thread.currentThread().getId(), GameData::new);
        return botLoopMap.get(Thread.currentThread().getId());
    }

    public boolean isFoolGameData() {
        return false;
    }

    public FoolGameData fool() {
        FoolGameData foo = new FoolGameData(0L);
        foo.update(carData, ballData, allCars, gravityZ, dt, advancedRenderer, ballPrediction);
        return foo;
    }

    public void update(CarData carData, ImmutableBallData ballData, List<CarData> allCars, GameInfo gameInfo, float dt, AdvancedRenderer advancedRenderer, YangBallPrediction ballPrediction) {
        this.carData = carData;
        this.elapsedSeconds = carData.elapsedSeconds;
        this.ballData = ballData;
        this.allCars = allCars;
        this.allCars.forEach((c) -> c.getPlayerInfo().update(c));
        assert this.allCars.contains(carData);
        this.gravityZ = gameInfo.worldGravityZ();
        assert gravityZ <= 0;
        this.gameInfoData = new GameInfoData(gameInfo);
        this.dt = dt;
        this.advancedRenderer = advancedRenderer;
        this.ballPrediction = ballPrediction;
        this.botIndex = carData.playerIndex;

        if (this.ballData.hasBeenTouched() && !this.isFoolGameData())
            InterruptManager.ballTouchInterrupt(this.ballData.getLatestTouch());
    }

    public void update(CarData carData, ImmutableBallData ballData, List<CarData> allCars, float gravity, float dt, AdvancedRenderer advancedRenderer, YangBallPrediction ballPrediction) {
        this.carData = carData;
        this.elapsedSeconds = carData.elapsedSeconds;
        this.ballData = ballData;
        this.allCars = allCars;
        assert this.allCars.contains(carData);
        this.gravityZ = gravity;
        assert gravity <= 0;
        this.dt = dt;
        this.advancedRenderer = advancedRenderer;
        this.ballPrediction = ballPrediction;
        this.botIndex = carData.playerIndex;
    }

    public void update(CarData carData, ImmutableBallData ballData, List<CarData> allCars, float gravity, float dt, AdvancedRenderer advancedRenderer) {
        this.update(carData, ballData, allCars, gravity, dt, advancedRenderer, YangBallPrediction.get());
    }

    public void update(CarData car, ImmutableBallData ball) {
        this.update(car, ball, List.of(car), RLConstants.gravity.z, RLConstants.tickFrequency, null);
    }

    public YangBallPrediction getBallPrediction() {
        return this.ballPrediction;
    }

    public void setBallPrediction(YangBallPrediction ballPrediction) {
        this.ballPrediction = ballPrediction;
    }

    public Vector3 getGravity() {
        return new Vector3(0f, 0f, gravityZ);
    }

    public CarData getCarData() {
        return carData;
    }

    public ImmutableBallData getBallData() {
        return ballData;
    }

    public List<CarData> getAllCars() {
        return allCars;
    }

    public float getDt() {
        return dt;
    }

    public AdvancedRenderer getAdvancedRenderer() {
        return advancedRenderer;
    }

    public int getBotIndex() {
        return this.botIndex;
    }

    public float getElapsedSeconds() {
        return elapsedSeconds;
    }
}
