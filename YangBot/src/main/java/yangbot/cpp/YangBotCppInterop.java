package yangbot.cpp;

import yangbot.util.Ray;
import yangbot.vector.Vector3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Optional;

public class YangBotCppInterop {

    static {
        try {
            // https://stackoverflow.com/questions/2937406/how-to-bundle-a-native-library-and-a-jni-library-inside-a-jar
            boolean is64Bit = System.getProperty("os.arch").contains("64");

            final String libName = is64Bit ? "YangBotCpp64.dll" : "YangBotCpp32.dll";
            final String rluName = is64Bit ? "rlutilities64.dll" : "rlutilities32.dll";
            final URL lib = ClassLoader.getSystemClassLoader().getResource("cpp/" + libName);
            final URL rlut = ClassLoader.getSystemClassLoader().getResource("cpp/" + rluName);

            final File tmpDir = Files.createTempDirectory("yangbot").toFile();
            tmpDir.deleteOnExit();

            final File nativeLibTmpFile = new File(tmpDir, libName);
            nativeLibTmpFile.deleteOnExit();

            final File nativeRlutTmpFile = new File(tmpDir, "rlutilities.dll");
            nativeRlutTmpFile.deleteOnExit();

            try (InputStream in = lib.openStream()) {
                Files.copy(in, nativeLibTmpFile.toPath());
            }
            try (InputStream in = rlut.openStream()) {
                Files.copy(in, nativeRlutTmpFile.toPath());
            }
            System.load(nativeRlutTmpFile.getAbsolutePath());
            System.load(nativeLibTmpFile.getAbsolutePath());
        } catch (IOException | UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    public YangBotCppInterop() {
    }

    // To make sure the static init is called
    public static void doNothing() {
    }

    public static Optional<Ray> checkForSurfaceCollision(Vector3 position, float collisionRadius) {
        float[] data = getSurfaceCollision(position, collisionRadius);
        if (data.length > 0) {
            Vector3 start = new Vector3(data[0], data[1], data[2]);
            Vector3 direction = new Vector3(data[3], data[4], data[5]);
            return Optional.of(new Ray(start, direction));
        }
        return Optional.empty();
    }

    public static native float[] ballstep(Vector3 pos, Vector3 vel, Vector3 ang);

    public static native float[] aerialML(Vector3 orientEuler, Vector3 angularVel, Vector3 targetEuler, float dt);

    public static native void init(byte mode, byte map);

    private static native float[] getSurfaceCollision(Vector3 pos, float sphereSize);

}
