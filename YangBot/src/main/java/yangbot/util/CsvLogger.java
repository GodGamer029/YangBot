package yangbot.util;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CsvLogger {

    private static final String seperator = "\t";
    private final String[] attributes;
    private List<float[]> values;

    public CsvLogger(String[] attributes) {
        this.attributes = attributes;
        assert attributes.length > 0;
        this.values = new ArrayList<>();
    }

    public void log(float[] vals) {
        assert vals.length == attributes.length;

        this.values.add(vals);
    }

    public void save(String fileName) {
        try {
            FileWriter myWriter = new FileWriter(fileName);
            myWriter.write(attributes[0]);
            for (int i = 1; i < attributes.length; i++) {
                myWriter.write(seperator);
                myWriter.write(attributes[i]);
            }
            myWriter.write(System.lineSeparator());

            for (var vals : this.values) {
                myWriter.write(Float.toString(vals[0]));
                for (int i = 1; i < attributes.length; i++) {
                    myWriter.write(seperator);
                    myWriter.write(Float.toString(vals[i]));
                }
                myWriter.write(System.lineSeparator());
            }

            myWriter.close();

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

}
