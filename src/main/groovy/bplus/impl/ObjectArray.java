package bplus.impl;

import bplus.*;

public class ObjectArray<K extends Comparable<K>,V> {

    final Class<K> keyType;
    final Class<V> valueType;
    final int order;

    public ObjectArray(final Class<K> keyType, final Class<V> valueType, final int order) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.order = order;
    }

    class _Branch implements Branch<K> {
        final Object[] ary = new Object[1 + 2 * order];
        private int _size = 0;
        
        public int size() { return _size; }
        public int order() { return order; }

        public void put(final int index, final Node<K> left, final K k, final Node<K> right) {
            ary[leftIndex(index)] = left;
            ary[keyIndex(index)] = k;
            ary[rightIndex(index)] = right;
        }

        public K key(final int index) { return keyType.cast(ary[keyIndex(index)]); }
        public Node<K> left(final int index) { return (Node<K>) ary[leftIndex(index)]; }
        public Node<K> right(final int index) { return (Node<K>) ary[rightIndex(index)]; }

        public void copy(final int argSrcPos, final Node<K> dest, final int argDestPos, final int argLength) {
            if(dest instanceof ObjectArray._Branch) {
                final int srcIndex = leftIndex(argSrcPos);
                final int destIndex = leftIndex(argDestPos);
                final int length = (2 * argLength) + ((argDestPos + argLength == size()) ? 1 : 0);
                System.arraycopy(this, srcIndex, dest, destIndex, length);
            }
            else {
                for(int i = 0; i < argLength; ++i) {
                    final int destIndex = argDestPos + i;
                    final int srcIndex = argSrcPos + i;
                    put(destIndex, left(srcIndex), key(srcIndex), right(srcIndex));
                }
            }
        }
        
        private int leftIndex(final int index) { return index << 1; }
        private int keyIndex(final int index) { return leftIndex(index) + 1; }
        private int rightIndex(final int index) { return leftIndex(index) + 2; }
    }
    
    class _Leaf implements Leaf<K,V> {
        final Object[] ary = new Object[2 * order];
        private int _size = 0;
        
        public int size() { return _size; }
        public int order() { return order; }

        public K key(final int index) { return keyType.cast(ary[keyIndex(index)]); }
        public V value(final int index) { return valueType.cast(ary[valueIndex(index)]); }
        private int keyIndex(final int index) { return index << 1; }
        private int valueIndex(final int index) { return keyIndex(index) + 1; }
    }
}
