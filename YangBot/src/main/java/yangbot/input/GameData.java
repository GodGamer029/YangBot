package yangbot.input;

import rlbot.flat.GameInfo;
import yangbot.vector.Vector3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameData {

    public static float timeOfMatchStart = -1f;

    private static Map<Long, GameData> botLoopMap = new ConcurrentHashMap<>();

    protected CarData carData = null;
    protected BallData ballData = null;
    protected List<CarData> allCars = null;
    protected float gravityZ = -650;
    protected float dt = 1 / 60f;

    public GameData(Long threadId) {
    }

    public static GameData current() {
        botLoopMap.computeIfAbsent(Thread.currentThread().getId(), GameData::new);
        return botLoopMap.get(Thread.currentThread().getId());
    }

    public FoolGameData fool() {
        FoolGameData foo = new FoolGameData(0L);
        foo.update(carData, ballData, allCars, gravityZ, dt);
        return foo;
    }

    public void update(CarData carData, BallData ballData, List<CarData> allCars, GameInfo gameInfo, float dt) {
        this.carData = carData;
        this.ballData = ballData;
        this.allCars = allCars;
        this.gravityZ = gameInfo.worldGravityZ();
        this.dt = dt;
    }

    public void update(CarData carData, BallData ballData, List<CarData> allCars, float gravity, float dt) {
        this.carData = carData;
        this.ballData = ballData;
        this.allCars = allCars;
        this.gravityZ = gravity;
        this.dt = dt;
    }

    public Vector3 getGravity() {
        return new Vector3(0f, 0f, gravityZ);
    }

    public CarData getCarData() {
        return carData;
    }

    public BallData getBallData() {
        return ballData;
    }

    public List<CarData> getAllCars() {
        return allCars;
    }

    public float getDt() {
        return dt;
    }
}
