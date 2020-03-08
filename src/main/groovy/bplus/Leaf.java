package bplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleImmutableEntry;
import static bplus.Node.insertIndex;

public interface Leaf<K extends Comparable<K>,V> extends Node<K,V> {
    V value(int index);
    Leaf<K,V> put(int index, K k, V v);
    
    default void remove(final int index) {
        final int shiftIndex = index + 1;
        if(shiftIndex < size()) {
            shiftLeft(shiftIndex, 1);
        }
        
        sizeDown(1);
    }

    default K lastKey() {
        return key(lastIndex());
    }

    default V lastValue() {
        return value(lastIndex());
    }

    default Map.Entry<K,V> entry(final int index) {
        return new SimpleImmutableEntry<>(key(index), value(index));
    }
    
    default Branch<K,V> asBranch() {
        throw new ClassCastException("not a branch");
    }

    default Leaf<K,V> asLeaf() {
        return this;
    }

    default int insert(K k, V v) {
        final int searchPoint = search(k);
        if(searchPoint >= 0) {
            throw new RuntimeException("duplicate key violation");
        }

        if(isFull()) {
            throw new RuntimeException("leaf is full");
        }
        
        final int index = insertIndex(searchPoint);
        sizeUp(1);
        if(index + 1 == size()) {
            put(index, k, v);
        }
        else {
            shiftRight(index, 1);
            put(index, k, v);
        }

        return index;
    }

    default Leaf<K,V> split(final K k, final V v) {
        final int searchPoint = search(k);
        if(searchPoint >= 0) {
            throw new RuntimeException("duplicate key violation");
        }

        if(!isFull()) {
            throw new RuntimeException("leaf is not full");
        }

        final int index = insertIndex(searchPoint);
        final Leaf<K,V> newRight = newLeaf();
        final int totalElements = size() + 1;
        final int leftSize = totalElements >>> 1; //+1 to include new element
        final int rightSize = totalElements - leftSize;

        if(index < leftSize) {
            newRight.size(rightSize);
            newRight.copy(leftSize - 1, this, 0, rightSize);
            size(leftSize - 1);
            insert(k, v);
        }
        else {
            newRight.size(rightSize - 1);
            newRight.copy(leftSize, this, 0, rightSize - 1);
            newRight.insert(k, v);
            size(leftSize);
        }

        return newRight;
    }
    
    default boolean isBranch() {
        return false;
    }

    default Leaf<K,V> copy(final int srcPos, final int destPos, final int length) {
        copy(srcPos, this, destPos, length);
        return this;
    }

    default Map<K,V> toMap() {
        Map<K,V> ret = new LinkedHashMap<>(size());
        for(int i = 0; i < size(); ++i) {
            ret.put(key(i), value(i));
        }

        return ret;
    }

    default List<V> values() {
        List<V> ret = new ArrayList<>(size());
        for(int i = 0; i < size(); ++i) {
            ret.add(value(i));
        }

        return ret;
    }
    
    default void traverse(NodeTraversal<K,V> traversal, K k) {
        traversal.add(this, search(k));
    }
    
    default void leftTraverse(NodeTraversal<K,V> traversal) {
        traversal.add(this, -1);
    }
    
    default void rightTraverse(NodeTraversal<K,V> traversal) {
        traversal.add(this, size());
    }
}
