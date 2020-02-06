package bplus;

public interface Branch<K extends Comparable<K>> extends Node<K> {
    void put(int index, Node<K> left, K k, Node<K> right);
    void copy(int srcPos, Node<K> dest, int destPos, int length);
    Node<K> left(int index);
    Node<K> right(int index);
    
    default boolean isBranch() {
        return true;
    }

    default void copy(int srcPos, int destPos, int length) {
        copy(srcPos, this, destPos, length);
    }
}
