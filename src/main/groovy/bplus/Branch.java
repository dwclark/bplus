package bplus;

public interface Branch<K extends Comparable<K>,V> extends Node<K,V> {
    Node<K,V> nullNode();
    Branch<K,V> put(int index, Node<K,V> left, K k, Node<K,V> right);
    Node<K,V> left(int index);
    Node<K,V> right(int index);
    Node<K,V> child(int index);
    
    default int nodeSize() {
        return size() + 1;
    }
    
    default Branch<K,V> asBranch() {
        return this;
    }

    default Leaf<K,V> asLeaf() {
        throw new ClassCastException("not a leaf");
    }

    default int getMinLimit() {
        return order() >>> 1;
    }
    
    default boolean isBranch() {
        return true;
    }

    default Branch<K,V> copy(int srcPos, int destPos, int length) {
        copy(srcPos, this, destPos, length);
        return this;
    }

    default Branch<K,V> put(int index, K k) {
        return put(index, nullNode(), k, nullNode());
    }

    default Branch<K,V> put(int index, Node<K,V> left, K k) {
        return put(index, left, k, nullNode());
    }

    default Branch<K,V> put(int index, K k, Node<K,V> right) {
        return put(index, nullNode(), k, right);
    }

    /**
     * Note, this returns the node index, not the key index
     */
    default int insert(final Node<K,V> node) {
        final K nodeMinKey = node.getMinKey();
        final int searchPoint = search(nodeMinKey);
        if(searchPoint >= 0) {
            throw new RuntimeException("duplicate key violation");
        }

        if(isFull()) {
            throw new RuntimeException("branch is full");
        }

        final int index = Node.insertIndex(searchPoint);
        final int currentSize = size();
        sizeUp(1);

        if(index == currentSize) {
            //is last node, set far right and node min key is new key
            put(index, nodeMinKey, node);
            return index;
        }
        else if(index == 0) {
            //node could be either on the left of 0 or the right of 0
            //that's why we have to test and possibly move the current left
            shiftRight(index, 1);
            final K leftMinKey = left(0).getMinKey();
            if(leftMinKey.compareTo(nodeMinKey) < 0) {
                put(0, nodeMinKey, node);
                return 1;
            }
            else {
                put(0, node, leftMinKey, left(0));
                return 0;
            }
        }
        else {
            //is a middle insert, after shifting,node becomes right key
            shiftRight(index, 1);
            put(index, nodeMinKey, node);
            return index + 1;
        }
    }

    default Branch<K,V> split(final Node<K,V> node) {
        final K nodeMinKey = node.getMinKey();
        final int searchPoint = search(nodeMinKey);
        if(searchPoint >= 0) {
            throw new RuntimeException("duplicate key violation");
        }

        if(!isFull()) {
            throw new RuntimeException("branch is not full");
        }

        int leftSize, rightSize, splitIndex;
        Branch<K,V> target;
        final int insertIndex = Node.insertIndex(searchPoint);
        final int half = size() >>> 1;
        final Branch<K,V> right = newBranch();
        
        if(isEvenSized()) {
            final Node<K,V> mid = left(half);
            if(nodeMinKey.compareTo(mid.key(mid.size() - 1)) < 0) {
                splitIndex = half;
                rightSize = half;
                leftSize = half - 1;
                target = this;
            }
            else {
                splitIndex = half + 1;
                rightSize = half - 1;
                leftSize = half;
                target = right;
            }
        }
        else {
            leftSize = half;
            rightSize = half;
            splitIndex = half + 1;
            target = nodeMinKey.compareTo(left(splitIndex).getMinKey()) < 0 ? this : right;
        }

        right.size(rightSize);
        right.copy(splitIndex, this, 0, rightSize);
        size(leftSize);
        target.insert(node);
        return right;
    }

    default K getMinKey() {
        Node<K,V> left = left(0);
        while(left.isBranch()) {
            left = left.asBranch().left(0);
        }

        return left.asLeaf().getMinKey();
    }

    /*default void remove(final int index, final Direction direction) {
        final int shiftIndex = index + 1;
        if(shiftIndex == 1 || shiftIndex < size()) {
            shiftLeft(shiftIndex, 1);
        }
        
        sizeDown(1);
        }*/

    default void remove(final int index, final Direction direction) {
        final int shiftIndex = index + (direction == Direction.LEFT ? 1 : 2);
        if(shiftIndex == 1 || shiftIndex < size()) {
            shiftLeft(shiftIndex, 1);
        }
        
        sizeDown(1);
    }
}
