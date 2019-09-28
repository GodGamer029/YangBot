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

    public GameData(Long threadId){ }

    public FoolGameData fool(){
        FoolGameData foo = new FoolGameData(0L);
        foo.update(carData, ballData, allCars, gravityZ);
        return foo;
    }

    public void update(CarData carData, BallData ballData, List<CarData> allCars, GameInfo gameInfo){
        this.carData = carData;
        this.ballData = ballData;
        this.allCars = allCars;
        this.gravityZ = gameInfo.worldGravityZ();
    }

    public void update(CarData carData, BallData ballData, List<CarData> allCars, float gravity){
        this.carData = carData;
        this.ballData = ballData;
        this.allCars = allCars;
        this.gravityZ = gravity;
    }

    public Vector3 getGravity(){
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

    public static GameData current() {
        botLoopMap.computeIfAbsent(Thread.currentThread().getId(), GameData::new);
        return botLoopMap.get(Thread.currentThread().getId());
    }
}
