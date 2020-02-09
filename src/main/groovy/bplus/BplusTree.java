package bplus;

public class BplusTree<K extends Comparable<K>,V>  {

    private class InsertResult {
        final Node<K,V> left;
        final Node<K,V> right;

        InsertResult(final Node<K,V> left, final Node<K,V> right) {
            this.left = left;
            this.right = right;
        }

        boolean isSplit() {
            return left != null;
        }
    }

    private final InsertResult NO_SPLIT = new InsertResult(null, null);

    private final NodeFactory<K,V> factory;
    
    public BplusTree(final NodeFactory<K,V> factory) {
        this.factory = factory;
    }

    public void put(final K k, final V v) {
        final InsertResult result = insert(factory.getRoot(), k, v);
        if(result.isSplit()) {
            final Branch<K,V> newRoot = factory.newBranch();
            newRoot.put(0, result.left, result.right.key(0), result.right);
        }
    }

    private InsertResult insert(final Node<K,V> node, final K k, final V v) {
        if(node.isLeaf()) {
            return insertLeaf(node.asLeaf(), k, v);
        }
        else {
            return insertBranch(node.asBranch(), k, v);
        }
    }

    private InsertResult insertBranch(final Branch<K,V> branch, final K k, final V v) {
        int insertPoint;
        Node<K,V> child;
        
        final int searchPoint = branch.search(k);
        
        if(searchPoint >= 0) {
            child = branch.right(searchPoint);
            insertPoint = searchPoint + 1;
        }
        else {
            insertPoint = Node.insertIndex(searchPoint);
            child = (insertPoint == branch.size()) ? branch.right(insertPoint) : branch.left(insertPoint);
        }
        
        final InsertResult result = insert(child, k, v);
        if(result.isSplit()) {
            branch.sizeUp(1);
            if(insertPoint + 1 < branch.size()) {
                branch.shiftRight(insertPoint, 1);
            }
            
            branch.put(insertPoint, result.left, result.right.key(0), result.right);
        }
        
        return branch.isFull() ? splitNode(factory.newBranch(), branch) : NO_SPLIT;
    }

    private InsertResult insertLeaf(final Leaf<K,V> leaf, final K k, final V v) {
        leaf.insert(k, v);
        return leaf.isFull() ? splitNode(factory.newLeaf(), leaf) : NO_SPLIT;
    }

    private InsertResult splitNode(final Node<K,V> left, final Node<K,V> right) {
        final int leftSize = right.size() >>> 1;
        final int rightSize = right.size() - leftSize;
        left.copy(0, right, 0, leftSize).size(leftSize);
        right.shiftLeft(leftSize, leftSize).size(rightSize);
        return new InsertResult(left, right);
    }
}
