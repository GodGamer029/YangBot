package yangbot.cpp;

import yangbot.vector.Vector3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

public class YangBotCppInterop {

    static {
        try {
            // https://stackoverflow.com/questions/2937406/how-to-bundle-a-native-library-and-a-jni-library-inside-a-jar
            boolean is64Bit = System.getProperty("os.arch").contains("64");

            final String libName = is64Bit ? "YangBotCpp64.dll" : "YangBotCpp32.dll";
            final URL lib = ClassLoader.getSystemClassLoader().getResource("cpp/" + libName);
            final URL rlut = ClassLoader.getSystemClassLoader().getResource("cpp/rlutilities.dll");

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

    public static native float[] ballstep(Vector3 pos, Vector3 vel);
    public static native float hello();
}
