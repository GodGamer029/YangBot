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

    public boolean isFoolGameDate() {
        return false;
    }

    public FoolGameData fool() {
        FoolGameData foo = new FoolGameData(0L);
        foo.update(carData, ballData, allCars, gravityZ, dt, advancedRenderer);
        return foo;
    }

    public void update(CarData carData, ImmutableBallData ballData, List<CarData> allCars, GameInfo gameInfo, float dt, AdvancedRenderer advancedRenderer) {
        this.carData = carData;
        this.elapsedSeconds = carData.elapsedSeconds;
        this.ballData = ballData;
        this.allCars = allCars;
        this.allCars.forEach((c) -> c.getPlayerInfo().update(c));
        this.gravityZ = gameInfo.worldGravityZ();
        this.gameInfoData = new GameInfoData(gameInfo);
        this.dt = dt;
        this.advancedRenderer = advancedRenderer;
        this.ballPrediction = YangBallPrediction.get();
        this.botIndex = carData.playerIndex;

        if (this.ballData.hasBeenTouched())
            InterruptManager.ballTouchInterrupt(this.ballData.getLatestTouch());
    }

    public void update(CarData carData, ImmutableBallData ballData, List<CarData> allCars, float gravity, float dt, AdvancedRenderer advancedRenderer) {
        this.carData = carData;
        this.elapsedSeconds = carData.elapsedSeconds;
        this.ballData = ballData;
        this.allCars = allCars;
        this.gravityZ = gravity;
        this.dt = dt;
        this.advancedRenderer = advancedRenderer;
        this.ballPrediction = YangBallPrediction.get();
        this.botIndex = carData.playerIndex;
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
