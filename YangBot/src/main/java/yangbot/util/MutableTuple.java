package yangbot.util;

import java.io.Serializable;
import java.util.Objects;

public class MutableTuple<K, V> implements Serializable {

    private K key;
    private V value;

    public MutableTuple(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey() {
        return key;
    }

    public void setKey(K key) {
        this.key = key;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public void set(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public Tuple<K, V> toImmutable() {
        return new Tuple<>(key, value);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 13 + (value == null ? 0 : value.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof MutableTuple) {
            MutableTuple pair = (MutableTuple) o;
            if (!Objects.equals(key, pair.key)) return false;
            if (!Objects.equals(value, pair.value)) return false;
            return true;
        }
        return false;
    }
}