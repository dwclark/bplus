package bplus;

import static bplus.Node.insertIndex;

public interface Branch<K extends Comparable<K>,V> extends Node<K,V> {
    Node<K,V> nullNode();
    Branch<K,V> put(int index, Node<K,V> child);
    Node<K,V> child(int index);
    void resetKey(int indexx);
    
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
        
        final K nodeMinKey = node.key(0);
        final int searchPoint = search(nodeMinKey);

        if(searchPoint >= 0) {
            throw new RuntimeException("duplicate key violation");
        }

        final int index = insertIndex(searchPoint);
        final int currentSize = size();
        sizeUp(1);
        shiftRight(index, 1);
        put(index, node);
        return index;
    }

    default Branch<K,V> split(final Node<K,V> node) {
        final int searchPoint = search(node.key(0));
        if(searchPoint >= 0) {
            throw new RuntimeException("duplicate key violation");
        }

        if(!isFull()) {
            throw new RuntimeException("branch is not full");
        }

        final int index = Node.insertIndex(searchPoint);
        final Branch<K,V> newRight = newBranch();
        final int totalElements = size() + 1;
        final int leftSize = (totalElements) >>> 1; //+1 to include new element
        final int rightSize = totalElements - leftSize;

        if(index < leftSize) {
            newRight.size(rightSize);
            newRight.copy(leftSize - 1, this, 0, rightSize);
            size(leftSize - 1);
            insert(node);
        }
        else {
            newRight.size(rightSize - 1);
            newRight.copy(leftSize, this, 0, rightSize - 1);
            newRight.insert(node);
            size(leftSize);
        }

        return newRight;
    }
    
    default void remove(final int index) {
        if(index + 1 < size()) {
            shiftLeft(index + 1, 1);
        }

        sizeDown(1);
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

    default void traverse(Traversal<K,V> traversal, K k) {
        final int navIndex = navigateIndex(k);
        traversal.add(this, navIndex);
        child(navIndex).traverse(traversal, k);
    }
    
    default void leftTraverse(Traversal<K,V> traversal) {
        traversal.add(this, 0);
        child(0).leftTraverse(traversal);
    }
    
    default void rightTraverse(Traversal<K,V> traversal) {
        traversal.add(this, size() - 1);
        child(size() - 1).rightTraverse(traversal);
    }
}
