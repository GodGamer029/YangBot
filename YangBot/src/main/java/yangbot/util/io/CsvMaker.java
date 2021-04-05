package yangbot.util.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class CsvMaker {

    private final File file;
    private final BufferedWriter writer;

    public CsvMaker(File file) {
        this.file = file;
        if (!this.file.exists()) {
            try {
                assert this.file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            this.writer = new BufferedWriter(new FileWriter(this.file, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static CsvMaker findFreeFile(String filename) {
        for (int i = 0; i < 999; i++) {
            var file = new File("D:\\Projekte\\JavaProjekte\\YangBot\\data", String.format(Locale.US, "%s-%03d.csv", filename, i));
            if (file.exists())
                continue;

            return new CsvMaker(file);
        }
        throw new RuntimeException();
    }

    public void close() {
        try {
            this.writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void flush() {
        try {
            this.writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveData(Float... values) {
        StringBuilder sb = new StringBuilder();
        sb.append(values[0]);
        for (int i = 1; i < values.length; i++)
            sb.append("\t").append(values[i]);
        sb.append(System.lineSeparator());

        try {
            this.writer.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
