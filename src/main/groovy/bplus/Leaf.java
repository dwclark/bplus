package bplus;

public interface Leaf<K extends Comparable<K>,V> extends Node<K> {
    V value(int index);
    
    default boolean isBranch() {
        return false;
    }
}
