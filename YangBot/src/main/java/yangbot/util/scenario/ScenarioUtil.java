package yangbot.util.scenario;

import rlbot.gamestate.BallState;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.math.vector.Vector3;

import java.io.IOException;
import java.io.StringReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ScenarioUtil {

    public static String getEncodedGameState(GameData gameData) {
        StringBuilder sb = new StringBuilder();
        sb.append("yangv1:");

        // local car
        {
            var car = gameData.getCarData();
            sb.append("c(");

            sb.append("b=" + car.boost + ",");
            sb.append("p=" + car.position.toYangEncodedString() + ",");
            sb.append("v=" + car.velocity.toYangEncodedString() + ",");
            sb.append("a=" + car.angularVelocity.toYangEncodedString() + ",");
            sb.append("o=" + car.orientation.toEuler().toYangEncodedString());

            sb.append("),");
        }

        // ball-
        {
            var ball = gameData.getBallData();
            sb.append("b(");

            sb.append("p=" + ball.position.toYangEncodedString() + ",");
            sb.append("v=" + ball.velocity.toYangEncodedString() + ",");
            sb.append("a=" + ball.angularVelocity.toYangEncodedString());
            sb.append(")");
        }

        sb.append(";");

        return Base64.getEncoder().withoutPadding().encodeToString(sb.toString().getBytes());
    }

    public static void decodeApplyToGameData(GameData gameData, String encoded) {
        var gState = decodeToGameState(encoded);

        CarData localCar = new CarData(gState.getCarState(0));
        BallData ball = new BallData(gState.getBallState());

        gameData.update(localCar, ball.makeImmutable());
    }

    public static GameState decodeToGameState(String encoded) {
        encoded = new String(Base64.getDecoder().decode(encoded));
        assert encoded.startsWith("yangv1:");

        try {
            StringReader reader = new StringReader(encoded);
            reader.skip("yangv1:".length());

            GameState state = new GameState();

            Supplier<Character> readCh = () -> {
                int read = 0;
                try {
                    read = reader.read();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (read < 0)
                    throw new RuntimeException("done");
                char ch = (char) read;
                if (ch == ';')
                    throw new RuntimeException("done");

                return ch;
            };

            Function<Character, String> getName = seperator -> {
                String name = "";
                char characterRead = readCh.get();
                if (characterRead == seperator)
                    throw new RuntimeException("invalid syntax, seperator " + seperator);
                do {
                    name += characterRead;
                    characterRead = readCh.get();
                } while (characterRead != seperator);
                return name;
            };

            final Pattern numberPattern = Pattern.compile("[-0-9.]+");
            Supplier<Float> getNumber = () -> {
                String number = "";
                char characterRead = readCh.get();
                if (!numberPattern.matcher(characterRead + "").matches())
                    throw new RuntimeException("invalid syntax, number");
                do {
                    number += characterRead;
                    characterRead = readCh.get();
                } while (numberPattern.matcher(characterRead + "").matches());
                try {
                    reader.skip(-1);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return Float.parseFloat(number);
            };

            Supplier<Vector3> getVec3 = () -> {
                char c;
                assert (c = readCh.get()) == '(' : c;
                float[] vals = new float[3];
                for (int i = 0; i < 3; i++) {
                    vals[i] = getNumber.get();
                    if (i < 2)
                        assert (c = readCh.get()) == ',' : c;
                }
                assert (c = readCh.get()) == ')' : c;
                return new Vector3(vals);
            };

            try {
                while (true) {
                    String objName = getName.apply('(');

                    // build physics obj
                    PhysicsState physics = new PhysicsState();
                    Map<String, Float> additionalArgs = new HashMap<>();
                    while (true) {
                        String attrName = getName.apply('=');
                        switch (attrName) {
                            case "p":
                                physics.withLocation(getVec3.get().toDesiredVector());
                                break;
                            case "v":
                                physics.withVelocity(getVec3.get().toDesiredVector());
                                break;
                            case "a":
                                physics.withAngularVelocity(getVec3.get().toDesiredVector());
                                break;
                            case "o":
                                physics.withRotation(getVec3.get().toDesiredRotation());
                                break;
                            default:
                                additionalArgs.put(attrName, getNumber.get());
                        }
                        char nextChar = readCh.get();
                        if (nextChar == ',')
                            continue;
                        assert nextChar == ')';
                        break;
                    }

                    switch (objName) {
                        case "c":
                            CarState carState = new CarState().withPhysics(physics);
                            for (var entry : additionalArgs.entrySet()) {
                                switch (entry.getKey()) {
                                    case "b": // boost
                                        carState.withBoostAmount(entry.getValue());
                                        break;
                                    default:
                                        throw new RuntimeException("invalid arg " + entry.getKey());
                                }
                            }
                            state.withCarState(0, carState);
                            break;
                        case "b":

                            state.withBallState(new BallState().withPhysics(physics));
                            break;
                    }

                    char nextChar = readCh.get();
                    if (nextChar == ',')
                        continue;
                    assert nextChar == ';';
                    break;
                }
            } catch (RuntimeException e1) {
                if (!e1.getMessage().equals("done"))
                    throw e1;
            }

            return state;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
