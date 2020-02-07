package bplus;

public interface Leaf<K extends Comparable<K>,V> extends Node<K> {
    V value(int index);
    Leaf<K,V> put(int index, K k, V v);
    
    default boolean isBranch() {
        return false;
    }
}
