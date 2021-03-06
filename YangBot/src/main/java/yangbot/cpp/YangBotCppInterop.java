package yangbot.cpp;

import yangbot.util.Ray;
import yangbot.util.math.vector.Vector3;

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


            final String libName = System.mapLibraryName(is64Bit ? "YangBotCpp64" : "YangBotCpp32");
            final String rluName = System.mapLibraryName(is64Bit ? "rlutilities64" : "rlutilities32");
            final URL lib = ClassLoader.getSystemClassLoader().getResource("cpp/" + libName);
            final URL rlut = ClassLoader.getSystemClassLoader().getResource("cpp/" + rluName);

            final File tmpDir = Files.createTempDirectory("yangbot").toFile();
            tmpDir.deleteOnExit();

            final File nativeLibTmpFile = new File(tmpDir, libName);
            nativeLibTmpFile.deleteOnExit();

            final File nativeRlutTmpFile = new File(tmpDir, System.mapLibraryName("rlutilities"));
            nativeRlutTmpFile.deleteOnExit();

            assert lib != null;
            try (InputStream in = lib.openStream()) {
                Files.copy(in, nativeLibTmpFile.toPath());
            }
            assert rlut != null;
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

    public static native float[] aerialML(Vector3 orientEuler, Vector3 angularVel, Vector3 targetEuler, float dt);

    public static native void init(byte mode, byte map);

    private static native float[] getSurfaceCollision(Vector3 pos, float sphereSize);

}
