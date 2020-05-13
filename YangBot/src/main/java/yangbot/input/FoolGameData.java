package yangbot.input;

public class FoolGameData extends GameData {
    public FoolGameData(Long threadId) {
        super(threadId);
    }

    public void foolCar(CarData car) {
        this.carData = car;
    }

    @Override
    public boolean isFoolGameDate() {
        return true;
    }

}
