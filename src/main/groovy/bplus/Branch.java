package bplus;

public interface Branch<K extends Comparable<K>> extends Node<K> {
    Branch<K> put(int index, Node<K> left, K k, Node<K> right);
    Branch<K> copy(int srcPos, Branch<K> src, int pos, int length);
    Node<K> left(int index);
    Node<K> right(int index);
    
    default boolean isBranch() {
        return true;
    }

    default Branch<K> copy(int srcPos, int pos, int length) {
        return copy(srcPos, this, pos, length);
    }

    default void slowCopy(final int argSrcPos, final Branch<K> src, final int argDestPos, final int argLength) {
        for(int i = 0; i < argLength; ++i) {
            final int destIndex = argDestPos + i;
            final int srcIndex = argSrcPos + i;
            put(destIndex, src.left(srcIndex), src.key(srcIndex), src.right(srcIndex));
        }
    }

    default Branch<K> shiftLeft(int at, int by) {
        if(by > at) {
            throw new IndexOutOfBoundsException("by must be >= at");
        }
        else {
            return copy(at, at - by, size() - by);
        }
    }
}
