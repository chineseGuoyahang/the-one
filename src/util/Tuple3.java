package util;

public class Tuple3<K,M,V> {  
    private K key;
    private M para;
    private V value;

    /**
     * Creates a new tuple.
     * @param key The key of the tuple
     * @param value The value of the tuple
     */
    public Tuple3(K key, M para, V value) {
        this.key = key;
        this.para=para;
        this.value = value;
    }

    /**
     * Returns the key
     * @return the key
     */
    public K getKey() {
        return key;
    }

    /**
     * Returns the value
     * @return the value
     */
    public V getValue() {
        return value;
    }
    public M getPara() {
        return para;
    }

    /**
     * Returns a string representation of the tuple
     * @return a string representation of the tuple
     */
    public String toString() {
        return key.toString() + ":" + para.toString() +" "+ value.toString();
    }
}
