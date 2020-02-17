package bplus;

import java.util.List;
import java.util.ArrayList;

public class BplusTree<K extends Comparable<K>,V>  {

    private final NodeStore<K,V> store;
    private final ThreadLocal<Traversal<K,V>> tlTraversal = ThreadLocal.withInitial(Traversal::new);
    
    public BplusTree(final NodeStore<K,V> store) {
        this.store = store;
    }

    public List<K> keyList() {
        final List<K> ret = new ArrayList<>();
        Node<K,V> node = store.getRoot();
        if(node.isBranch()) {
            keyListBranch(ret, node.asBranch());
        }
        else {
            keyListLeaf(ret, node.asLeaf());
        }

        return ret;
    }

    private void keyListBranch(final List<K> list, final Branch<K,V> branch) {
        Node<K,V> child;
        for(int i = 0; i < branch.size(); ++i) {
            child = branch.left(i);
            if(child.isBranch()) {
                keyListBranch(list, child.asBranch());
            }
            else {
                keyListLeaf(list, child.asLeaf());
            }
        }

        child = branch.right(branch.size() - 1);
        if(child.isBranch()) {
            keyListBranch(list, child.asBranch());
        }
        else {
            keyListLeaf(list, child.asLeaf());
        }
    }

    private void keyListLeaf(final List<K> list, final Leaf<K,V> leaf) {
        for(int i = 0; i < leaf.size(); ++i) {
            list.add(leaf.key(i));
        }
    }

    public void put(final K k, final V v) {
        final Traversal<K,V> traversal = tlTraversal.get().execute(store.getRoot(), k);
        while(traversal.level() >= 0) {
            final Node<K,V> current = traversal.getCurrent().getNode();

            //handle leaf
            if(current.isLeaf()) {
                putLeaf(traversal, k, v);
                traversal.pop();
            }
            else if(traversal.hasOrphan()) {
                putBranch(traversal);
                traversal.pop();
            }
            else {
                return;
            }
        }

        //if there is a remaining orphan, we need a new root
        if(traversal.hasOrphan()) {
            final Branch<K,V> newRoot = store.getRoot().newBranch();
            newRoot.sizeUp(1);
            newRoot.put(0, store.getRoot(), traversal.getOrphan().getMinKey(), traversal.getOrphan());
            store.setRoot(newRoot);
        }
    }

    private void putLeaf(final Traversal<K,V> traversal, final K k, final V v) {
        final Node<K,V> current = traversal.getCurrent().getNode();
        final Leaf<K,V> leaf = current.asLeaf();
        
        if(!leaf.isFull()) {
            leaf.insert(k, v);
            return;
        }
        
        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSiblingWithRoom();
        if(leftRel != null) {
            final Leaf<K,V> sibling = leftRel.getSibling().asLeaf();
            sibling.sizeUp(1);
            sibling.put(sibling.size() - 1, leaf.getMinKey(), leaf.getMinValue());
            leaf.removeMin();
            leaf.insert(k, v);
            leftRel.getCommonParent().put(leftRel.getParentIndex(), leaf.getMinKey());
            return;
        }
        
        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSiblingWithRoom();
        if(rightRel != null) {
            final Leaf<K,V> sibling = rightRel.getSibling().asLeaf();
            sibling.sizeUp(1).shiftRight(0, 1);
            
            final int searchIndex = leaf.search(k);
            final int insertIndex = searchIndex >= 0 ? searchIndex : Node.insertIndex(searchIndex);
            if(insertIndex == leaf.size()) {
                sibling.put(0, k, v);
            }
            else {
                sibling.put(0, leaf.getMaxKey(), leaf.getMaxValue());
                leaf.removeMax();
                leaf.insert(k, v);
            }

            rightRel.getCommonParent().put(rightRel.getParentIndex(), sibling.getMinKey());
            return;
        }

        //this is not correct, branch version probably isn't either
        //it definitely fails in the case of insert at zero branch index
        final Leaf<K,V> newRightSibling = leaf.split(k, v);
        final Traversal.Entry<K,V> parentEntry = traversal.parent();
        if(parentEntry != null) {
            final Branch<K,V> parentBranch = parentEntry.getNode().asBranch();
            parentBranch.put(parentEntry.getIndex(), parentBranch.right(parentEntry.getIndex()).getMinKey());
        }
        
        traversal.setOrphan(newRightSibling);
    }

    private void putBranch(final Traversal<K,V> traversal) {
        final Traversal.Entry<K,V> currentEntry = traversal.getCurrent();
        final Branch<K,V> current = currentEntry.getNode().asBranch();
        final Node<K,V> orphan = traversal.getOrphan();
        traversal.setOrphan(null);

        if(!current.isFull()) {
            final int newIndex = currentEntry.getIndex() + 1;
            current.sizeUp(1);
            current.shiftRight(newIndex, 1);
            current.put(newIndex, orphan.getMinKey(), orphan);
            return;
        }

        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSiblingWithRoom();
        if(leftRel != null) {
            //move min to left sibling
            final Branch<K,V> sibling = leftRel.getSibling().asBranch();
            final Node<K,V> toMove = current.left(0);
            sibling.sizeUp(1);
            sibling.put(sibling.size() - 1, toMove.getMinKey(), toMove);

            //insert into current
            current.shiftLeft(1, 1).sizeDown(1);
            current.insert(orphan);

            //reset parent key
            leftRel.getCommonParent().put(leftRel.getParentIndex(), current.getMinLeafKey());
            return;
        }
        
        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSiblingWithRoom();
        if(rightRel != null) {
            //move max to right sibling
            final Branch<K,V> sibling = rightRel.getSibling().asBranch();
            final Node<K,V> toMove = current.right(current.size() - 1);
            sibling.sizeUp(1);
            sibling.shiftRight(0, 1);
            sibling.put(0, toMove, sibling.right(0).getMinKey());

            //reset parent key
            rightRel.getCommonParent().put(rightRel.getParentIndex(), sibling.getMinLeafKey());

            //insert into current
            current.sizeDown(1);
            current.insert(orphan);
        }

        final Node<K,V> newRight = current.split(orphan);
        traversal.setOrphan(newRight);
    }
    
    public V get(final K k) {
        final Traversal<K,V> tr = tlTraversal.get().execute(store.getRoot(), k);
        final Leaf<K,V> leaf = tr.getCurrent().getNode().asLeaf();
        final int index = leaf.search(k);
        return index >= 0 ? leaf.value(index) : null;
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

    private V delete(final Node<K,V> node, final K k) {
        throw new UnsupportedOperationException();
    }
}
