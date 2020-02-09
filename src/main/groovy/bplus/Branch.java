package bplus;

public interface Branch<K extends Comparable<K>,V> extends Node<K,V> {
    Branch<K,V> put(int index, Node<K,V> left, K k, Node<K,V> right);
    Node<K,V> left(int index);
    Node<K,V> right(int index);

    default Branch<K,V> asBranch() {
        return this;
    }

    default Leaf<K,V> asLeaf() {
        throw new ClassCastException("not a leaf");
    }
    
    default boolean isBranch() {
        return true;
    }

    default Branch<K,V> copy(int srcPos, int destPos, int length) {
        copy(srcPos, this, destPos, length);
        return this;
    }

    default void slowCopy(final int argSrcPos, final Branch<K,V> src, final int argDestPos, final int argLength) {
        for(int i = 0; i < argLength; ++i) {
            final int destIndex = argDestPos + i;
            final int srcIndex = argSrcPos + i;
            put(destIndex, src.left(srcIndex), src.key(srcIndex), src.right(srcIndex));
        }
    }
}
