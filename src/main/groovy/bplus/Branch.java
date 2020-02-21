package bplus;

public interface Branch<K extends Comparable<K>,V> extends Node<K,V> {
    Node<K,V> nullNode();
    Branch<K,V> put(int index, Node<K,V> child);
    Node<K,V> child(int index);
    
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
    
    default int insert(final Node<K,V> node) {
        if(isFull()) {
            throw new RuntimeException("branch is full");
        }
        
        final K nodeMinKey = node.getMinKey();
        final int searchPoint = search(nodeMinKey);

        if(searchPoint >= 0) {
            throw new RuntimeException("duplicate key violation");
        }

        final int index = Node.insertIndex(searchPoint);
        final int currentSize = size();
        sizeUp(1);
        shiftRight(index, 1);
        put(index, node);
        return index;
    }

    default Branch<K,V> split(final Node<K,V> node) {
        final int searchPoint = search(node.getMinKey());
        if(searchPoint >= 0) {
            throw new RuntimeException("duplicate key violation");
        }

        if(!isFull()) {
            throw new RuntimeException("branch is not full");
        }

        final int leftSize = size() >>> 1;
        final int index = Node.insertIndex(searchPoint);
        final Branch<K,V> newRight = newBranch();
        final int rightSize = size() - leftSize;
        newRight.copy(leftSize, this, 0, rightSize).size(rightSize);
        size(leftSize);

        if(index <= leftSize) {
            insert(node);
        }
        else {
            newRight.insert(node);
        }

        return newRight;
    }
    
    default K getMinKey() {
        return key(0);
    }

    default void delete(final int index) {
        if(index + 1 < size()) {
            shiftLeft(index + 1, 1);
        }

        sizeDown(0);
            
    }

    default int navigateIndex(final K k) {
        final int searchIndex = search(k);
        if(searchIndex >= 0) {
            return searchIndex;
        }

        final int _index = Node.insertIndex(searchIndex);
        if(_index == 0) {
            return _index;
        }
        else {
            return _index - 1;
        }
    }
}
