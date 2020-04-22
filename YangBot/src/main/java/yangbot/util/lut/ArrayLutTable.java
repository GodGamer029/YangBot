package yangbot.util.lut;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.function.ToIntFunction;

public class ArrayLutTable<K extends Serializable, V extends Serializable, F extends Serializable & ToIntFunction<K>> implements Serializable {

    private static final long serialVersionUID = 8537331592114560519L;
    public V[] table = null;
    private F keyToIndex;

    public ArrayLutTable(F keyToIndexFunc) {
        this.keyToIndex = keyToIndexFunc;
    }

    private void ensureSize(Class<? extends Serializable> valueType, int size) {
        if (this.table == null) {
            this.table = (V[]) Array.newInstance(valueType, size);
        } else if (table.length < size) {
            int newSize = Math.max(size, table.length + 16);
            //System.out.println("Growing LUT from size "+this.table.length+" to "+newSize+ " (requested: "+size+")");
            var tempTable = (V[]) Array.newInstance(valueType, newSize);
            System.arraycopy(this.table, 0, tempTable, 0, this.table.length);
            this.table = tempTable;
        }
    }

    public F getKeyToIndexFunction() {
        return this.keyToIndex;
    }

    public void setKeyToIndexFunction(F keyToIndex) {
        this.keyToIndex = keyToIndex;
    }

    public V get(K key) {
        var val = table[this.keyToIndex.applyAsInt(key)];
        assert val != null : key;
        return val;
    }

    public V getWithIndex(int index) {
        var val = table[index];
        //assert val != null : index;
        return val;
    }

    public void setWithIndex(int index, V value) {
        this.ensureSize(value.getClass(), index + 1);
        this.table[index] = value;
    }

    public void set(K key, V value) {
        int ind = this.keyToIndex.applyAsInt(key);
        this.setWithIndex(ind, value);
    }
}
