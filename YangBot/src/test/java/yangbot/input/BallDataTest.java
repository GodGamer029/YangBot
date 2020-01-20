package yangbot.input;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import yangbot.cpp.YangBotCppInterop;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BallDataTest {

    @BeforeAll
    public static void setup() {
        YangBotCppInterop.init((byte) 0, (byte) 0);
    }

    @Test
    public void predictCollisionTest1() {
        BallData ballData = new BallData(new Vector3(0, -4594.94, 142.56999), new Vector3(0, 28.821f, 10.931f), new Vector3(0.27231002f, 0.0f, 0.0f));
        CarData carData = new CarData(new Vector3(-0.0, -4607.73, 16.97), new Vector3(-0.0, 0.311, -0.951), new Vector3(), Matrix3x3.lookAt(new Vector3(4.370481E-8, 0.99984944, -0.017352287), new Vector3(0, 0, 1)));

        ballData.stepWithCollide(0.008331299f, carData);

        assert ballData.collidesWith(carData) : "Ball should collide with car";
        assertEquals(ballData.position.x, 0, 0.1f);
        assertEquals(ballData.position.y, -4594.6997, 0.2f);
        assertEquals(ballData.position.z, 142.62f, 0.2f);

        assertEquals(ballData.velocity.x, 0, 0.1f);
        assertEquals(ballData.velocity.y, 32.650997, 0.2f);
        assertEquals(ballData.velocity.z, 25.210999, 1.2f);

        assertEquals(ballData.angularVelocity.x, 0.27231002, 0.01f);
        assertEquals(ballData.angularVelocity.y, 0, 0.01f);
        assertEquals(ballData.angularVelocity.z, 0, 0.01f);

        /*System.out.println("Pos: "+ballData.position);
        System.out.println("Vel: "+ballData.velocity);
        System.out.println("Ang: "+ballData.angularVelocity);*/

    }

    @Test
    public void predictCollisionTest2() {
        BallData ballData = new BallData(new Vector3(-0.0, -4595.39, 142.5), new Vector3(-0.0, 25.860998, -12.501), new Vector3(0.25031, 0.0, 0.0));
        CarData carData = new CarData(new Vector3(-0.0, -4607.73, 16.99), new Vector3(-0.0, -0.071, 0.86099994), new Vector3(-0.00531, 0.0, 0.0), Matrix3x3.lookAt(new Vector3(4.370481E-8, 0.99984944, -0.017352287), new Vector3(7.584926E-10, 0.017352287, 0.99984944)));

        ballData.stepWithCollide(0.008331299f, carData);

        assert ballData.collidesWith(carData) : "Ball should collide with car";
        /*assertEquals(ballData.position.x, 0, 0.1f);
        assertEquals(ballData.position.y, -4594.6997, 0.2f);
        assertEquals(ballData.position.z, 142.62f, 0.2f);

        assertEquals(ballData.velocity.x, 0, 0.1f);
        assertEquals(ballData.velocity.y, 32.650997, 0.2f);
        assertEquals(ballData.velocity.z, 25.210999, 0.2f);

        assertEquals(ballData.angularVelocity.x, 0.27231002, 0.01f);
        assertEquals(ballData.angularVelocity.y, 0, 0.01f);
        assertEquals(ballData.angularVelocity.z, 0, 0.01f);*/

        /*System.out.println("Pos: "+ballData.position);
        System.out.println("Vel: "+ballData.velocity);
        System.out.println("Ang: "+ballData.angularVelocity);
*/
    }

    @Test
    public void predictCollisionTest3() {
        BallData ballData = new BallData(new Vector3(-0.0, -4607.4297, 148), new Vector3(-0.0, 1.451, -102.631), new Vector3(0.02321, 0.0, 0.0));
        CarData carData = new CarData(new Vector3(-0.0, -4607.84, 17.01), new Vector3(-0.0, 0.0, 0.211), new Vector3(5.1E-4, 0.0, 0.0),
                Matrix3x3.lookAt(new Vector3(4.370938E-8, 0.99995404, -0.009587233), new Vector3(4.1907128E-10, 0.009587233, 0.99995404)));

        float dt = 0.008333206f;

        //assert ballData.collidesWith(carData) : "Ball should collide with car";
        ballData.stepWithCollide(dt, carData);

        System.out.println("Java collide:");
        System.out.println("Pos: " + ballData.position);
        System.out.println("Vel: " + ballData.velocity);
        System.out.println("Ang: " + ballData.angularVelocity);
    }

    @Test
    public void predictCollisionTest4() {
        BallData ballData = new BallData(new Vector3(-0.0, -4607.4297, 148), new Vector3(-0.0, 1.451, -102.631), new Vector3(0.02321, 0.0, 0.0));
        CarData carData = new CarData(new Vector3(-0.0, -4607.84, 17.01), new Vector3(-0.0, 0.0, 0.211), new Vector3(5.1E-4, 0.0, 0.0),
                Matrix3x3.lookAt(new Vector3(4.370938E-8, 0.99995404, -0.009587233), new Vector3(4.1907128E-10, 0.009587233, 0.99995404)));

        float dt = 0.008333206f;

        //assert ballData.collidesWith(carData) : "Ball should collide with car";
        ballData.stepWithCollideChip(dt, carData);

        System.out.println("Chip collide:");
        System.out.println("Pos: " + ballData.position);
        System.out.println("Vel: " + ballData.velocity);
        System.out.println("Ang: " + ballData.angularVelocity);
    }
}