package bplus;

public interface Branch<K extends Comparable<K>> extends Node<K> {
    Branch<K> put(int index, Node<K> left, K k, Node<K> right);
    Branch<K> copy(int srcPos, Branch<K> src, int pos, int length);
    Node<K> left(int index);
    Node<K> right(int index);
    
    default boolean isBranch() {
        return true;
    }

    default void copy(int srcPos, int pos, int length) {
        copy(srcPos, this, pos, length);
    }

    default void slowCopy(final int argSrcPos, final Branch<K> src, final int argDestPos, final int argLength) {
        for(int i = 0; i < argLength; ++i) {
            final int destIndex = argDestPos + i;
            final int srcIndex = argSrcPos + i;
            put(destIndex, src.left(srcIndex), src.key(srcIndex), src.right(srcIndex));
        }
    }
}
