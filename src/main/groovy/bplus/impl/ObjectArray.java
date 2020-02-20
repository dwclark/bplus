package bplus.impl;

import bplus.*;
import java.util.Arrays;

public class ObjectArray<K extends Comparable<K>,V> implements NodeStore<K,V> {

    private final Class<K> keyType;
    private final Class<V> valueType;
    private final int branchOrder;
    private final int leafOrder;
    
    private Node<K,V> root;

    public ObjectArray(final Class<K> keyType, final Class<V> valueType, final int order) {
        this(keyType, valueType, order, order);
    }

    public ObjectArray(final Class<K> keyType, final Class<V> valueType, final int branchOrder, final int leafOrder) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.branchOrder = branchOrder;
        this.leafOrder = leafOrder;
        this.root = new _Leaf();
    }

    public Node<K,V> getRoot() {
        return root;
    }

    public void setRoot(final Node<K,V> val) {
        root = val;
    }

    private class Base {
        private int _size = 0;
        protected final Object[] ary;

        protected Base(final int arySize) {
            this.ary = new Object[arySize];
        }
        
        public int size() { return _size; }
        public void size(final int sz) { _size = sz; }

        public Branch<K,V> newBranch() {
            return new _Branch();
        }

        public Leaf<K,V> newLeaf() {
            return new _Leaf();
        }

        public void done() {
            Arrays.fill(ary, null);
        }

        public K key(final int index) { return keyType.cast(ary[keyIndex(index)]); }
        protected int keyIndex(final int index) { return index >>> 1; }
        protected int pairIndex(final int index) { return keyIndex(index) + 1; }

        protected Object[] sourceArray(final Node<K,V> node) {
            if(node instanceof ObjectArray.Base) {
                return ((ObjectArray.Base) node).ary;
            }
            else {
                throw new IllegalArgumentException("source node is not the correct type");
            }
        }

        public void sharedCopy(final int argSrcPos, final Node<K,V> argSrc, final int argDestPos, final int argLength) {
            final int srcIndex = keyIndex(argSrcPos);
            final int destIndex = keyIndex(argDestPos);
            final int length = argLength << 1;
            System.arraycopy(sourceArray(argSrc.asBranch()), srcIndex, ary, destIndex, length);
        }

        public void resizeClear(final int newSize) {
            if(newSize < size()) {
                Arrays.fill(ary, keyIndex(newSize), ary.length, null);
            }
        }
    }

    private class _Branch extends Base implements Branch<K,V> {
        protected _Branch() { super(2 * branchOrder); }

        public Branch<K,V> put(final int index, final Node<K,V> child) {
            ary[keyIndex(index)] = child.getMinKey();
            ary[pairIndex(index)] = child;
            return this;
        }

        public int order() { return branchOrder; }

        public Node<K,V> nullNode() {
            return null;
        }

        public Node<K,V> child(final int index) { return extractNode(pairIndex(index)); }
        
        public Branch<K,V> copy(final int argSrcPos, final Node<K,V> argSrc, final int argDestPos, final int argLength) {
            sharedCopy(argSrcPos, argSrc, argDestPos, argLength);
            return this;
        }

        @Override
        public void size(final int newSize) {
            resizeClear(newSize);
            super.size(newSize);
        }
        
        @SuppressWarnings("unchecked")
        private Node<K,V> extractNode(final int actualIndex) {
            return (Node<K,V>) ary[actualIndex];
        }
    }
    
    private class _Leaf extends Base implements Leaf<K,V> {
        protected _Leaf() { super(2 * leafOrder); }

        public Leaf<K,V> put(final int index, final K k, final V v) {
            ary[keyIndex(index)] = k;
            ary[pairIndex(index)] = v;
            return this;
        }

        public Leaf<K,V> copy(final int argSrcPos, final Node<K,V> argSrc, final int argDestPos, final int argLength) {
            sharedCopy(argSrcPos, argSrc, argDestPos, argLength);
            return this;
        }

        @Override
        public void size(final int newSize) {
            resizeClear(newSize);
            super.size(newSize);
        }

        public int order() { return leafOrder; }
        
        public V value(final int index) { return valueType.cast(ary[pairIndex(index)]); }
    }
}
