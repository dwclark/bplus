package bplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static bplus.Node.insertIndex;

public interface Leaf<K extends Comparable<K>,V> extends Node<K,V> {
    V value(int index);
    Leaf<K,V> put(int index, K k, V v);

    default Branch<K,V> asBranch() {
        throw new ClassCastException("not a branch");
    }

    default Leaf<K,V> asLeaf() {
        return this;
    }

    default Leaf<K,V> insert(K k, V v) {
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

        return this;
    }
    
    default V delete(final K k) {
        final int index = search(k);
        if(index < 0) {
            return null;
        }

        final V v = value(index);
        shiftLeft(index, 1).sizeDown(0);
        return v;
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
}
