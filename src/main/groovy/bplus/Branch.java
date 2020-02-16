package bplus;

public interface Branch<K extends Comparable<K>,V> extends Node<K,V> {
    Node<K,V> nullNode();
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

    default Branch<K,V> put(int index, K k) {
        return put(index, nullNode(), k, nullNode());
    }

    default Branch<K,V> put(int index, Node<K,V> left, K k) {
        return put(index, left, k, nullNode());
    }

    default Branch<K,V> put(int index, K k, Node<K,V> right) {
        return put(index, nullNode(), k, right);
    }

    default void insert(final Node<K,V> node) {
        final int searchPoint = search(node.getMinKey());
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
            put(index, node.getMinKey(), node);
        }
        else if(index == 0) {
            //node could be either on the left of 0 or the right of 0
            //that's why we have to test and possibly move the current left
            shiftRight(index, 1);
            if(left(0).getMinKey().compareTo(node.getMinKey()) < 0) {
                put(0, node.getMinKey(), node);
            }
            else {
                put(0, node, left(0).getMinKey(), left(0));
            }
        }
        else {
            //is a middle insert, after shifting,node becomes right key
            shiftRight(index, 1);
            put(index, node.getMinKey(), node);
        }
    }

    default Branch<K,V> split(final Node<K,V> node) {
        final int searchPoint = search(node.getMinKey());
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
            if(node.getMinKey().compareTo(mid.getMaxKey()) < 0) {
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
            target = node.getMinKey().compareTo(left(splitIndex).getMinKey()) < 0 ? this : right;
        }

        right.size(rightSize);
        right.copy(splitIndex, this, 0, rightSize);
        size(leftSize);
        target.insert(node);
        return right;
    }

    default K getMinLeafKey() {
        Node<K,V> left = left(0);
        while(left.isBranch()) {
            left = left.asBranch().left(0);
        }

        return left.asLeaf().getMinKey();
    }
}
