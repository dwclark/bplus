package bplus;

import java.util.List;
import java.util.ArrayList;

public interface Node<K extends Comparable<K>,V> {
    int size();
    void size(int sz);
    int order();
    K key(int index);
    boolean isBranch();
    void done();
    Node<K,V> copy(int srcPos, int destPos, int length);
    Branch<K,V> asBranch();
    Leaf<K,V> asLeaf();
    Node<K,V> copy(int srcPos, Node<K,V> src, int pos, int length);
    Branch<K,V> newBranch();
    Leaf<K,V> newLeaf();
    K getMinKey();
    int getMinLimit();
    
    default boolean isLeaf() {
        return !isBranch();
    }

    default boolean isEvenSized() {
        return (size() & 1) == 0;
    }

    default boolean isEvenOrdered() {
        return (order() & 1) == 0;
    }

    default boolean isFull() {
        return size() == order();
    }

    default boolean isBelowLimit() {
        return size() < getMinLimit();
    }

    default boolean isAtMinLimit() {
        return size() == getMinLimit();
    }

    default boolean isAboveMinLimit() {
        return size() > getMinLimit();
    }
    
    default int compare(int index, K rhs) {
        return key(index).compareTo(rhs);
    }

    default int search(final K lookFor) {
        int low = 0;
        int high = size() - 1;

        while(low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = compare(mid, lookFor);

            if(cmp < 0) {
                low = mid + 1;
            }
            else if(cmp > 0) {
                high = mid - 1;
            }
            else {
                return mid;
            }
        }

        return insertIndex(low);
    }

    default void remove(final int index) {
        final int shiftIndex = index + 1;
        if(shiftIndex < size()) {
            shiftLeft(shiftIndex, 1);
        }
        
        sizeDown(1);
    }

    default Node<K,V> sizeUp(final int by) {
        size(size() + by);
        return this;
    }

    default Node<K,V> sizeDown(final int by) {
        size(size() - by);
        return this;
    }

    default List<K> keys() {
        List<K> ret = new ArrayList<>(size());
        for(int i = 0; i < size(); i++) {
            ret.add(key(i));
        }

        return ret;
    }

    default Node<K,V> shiftLeft(int at, int by) {
        return copy(at, at - by, size() - at);
    }
    
    default Node<K,V> shiftRight(int at, int by) {
        return copy(at, at + by, size() - (at + by));
    }

    static boolean found(final int index) {
        return index >= 0;
    }

    static int insertIndex(final int index) {
        return -(index + 1);
    }
}
