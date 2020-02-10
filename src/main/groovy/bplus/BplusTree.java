package bplus;

public class BplusTree<K extends Comparable<K>,V>  {

    private enum Direction { Left, Right }
    
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

    private final NodeStore<K,V> store;
    
    public BplusTree(final NodeStore<K,V> store) {
        this.store = store;
    }

    public void put(final K k, final V v) {
        final InsertResult result = insert(store.getRoot(), k, v);
        if(result.isSplit()) {
            final Branch<K,V> newRoot = result.left.newBranch();
            newRoot.sizeUp(1);
            newRoot.put(0, result.left, result.right.key(0), result.right);
            store.setRoot(newRoot);
        }
    }

    public V get(final K k) {
        return search(store.getRoot(), k);
    }

    public V remove(final K k) {
        final Node<K,V> root = store.getRoot();
        final V ret = delete(root, k);
        if(ret == null) {
            return null;
        }
        
        if(root.size() > 1 || root.isLeaf()) {
            return ret;
        }

        final Branch<K,V> branch = root.asBranch();
        final Node<K,V> left = branch.left(0);
        final Node<K,V> right = branch.right(0);
        if(left.size() == 0) {
            left.done();
            store.setRoot(right);
            root.done();
        }

        if(right.size() == 0) {
            right.done();
            store.setRoot(left);
            root.done();
        }

        return ret;
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
            child = (insertPoint == branch.size()) ? branch.right(insertPoint-1) : branch.left(insertPoint);
        }
        
        final InsertResult result = insert(child, k, v);
        if(result.isSplit()) {
            branch.sizeUp(1);
            if(insertPoint + 1 < branch.size()) {
                branch.shiftRight(insertPoint, 1);
            }
            
            branch.put(insertPoint, result.left, result.right.key(0), result.right);
        }
        
        return branch.isFull() ? new InsertResult(branch, branch.split()) : NO_SPLIT;
    }

    private InsertResult insertLeaf(final Leaf<K,V> leaf, final K k, final V v) {
        leaf.insert(k, v);
        return leaf.isFull() ? new InsertResult(leaf, leaf.split()) : NO_SPLIT;
    }

    private V search(final Node<K,V> node, final K k) {
        if(node.isLeaf()) {
            final Leaf<K,V> leaf = node.asLeaf();
            final int index = leaf.search(k);
            return index >= 0 ? leaf.value(index) : null;
        }
        else {
            final Branch<K,V> branch = node.asBranch();
            final int searchIndex = branch.search(k);
            final Direction dir = direction(searchIndex, branch);
            final int navIndex = navigateIndex(searchIndex, branch);
            final Node<K,V> navNode = (dir == Direction.Left) ? branch.left(navIndex) : branch.right(navIndex);
            return search(navNode, k);
        }
    }

    private V delete(final Node<K,V> node, final K k) {
        if(node.isLeaf()) {
            return node.asLeaf().delete(k);
        }
        else {
            final Branch<K,V> branch = node.asBranch();
            final int searchIndex = branch.search(k);
            final Direction dir = direction(searchIndex, branch);
            final int navIndex = navigateIndex(searchIndex, branch);
            final Node<K,V> navNode = (dir == Direction.Left) ? branch.left(navIndex) : branch.right(navIndex);
            final V v = delete(navNode, k);

            if(v == null || navNode.size() > 0) {
                return v;
            }
            
            //navNode has nothing, need to splice together keys.
        }

        throw new UnsupportedOperationException();
    }

    private Direction direction(final int index, final Branch<K,V> branch) {
        if(index >= 0) {
            return Direction.Right;
        }
        
        final int retIndex = Node.insertIndex(index);
        if(retIndex < branch.size()) {
            return Direction.Left;
        }
        else {
            return Direction.Right;
        }
    }

    private int navigateIndex(final int index, final Branch<K,V> branch) {
        if(index >= 0) {
            return index;
        }
        
        int retIndex = Node.insertIndex(index);
        if(retIndex < branch.size()) {
            return retIndex;
        }
        else {
            return retIndex - 1;
        }
    }
}
