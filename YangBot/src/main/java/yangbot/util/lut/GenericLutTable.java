package yangbot.util.lut;

import java.io.Serializable;
import java.util.HashMap;

public class GenericLutTable<K extends Serializable, V extends Serializable> implements Serializable {

    private HashMap<K, V> lutTable;


}
