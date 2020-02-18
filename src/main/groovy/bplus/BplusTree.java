package bplus;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class BplusTree<K extends Comparable<K>,V>  {

    private final NodeStore<K,V> store;
    private final ThreadLocal<Traversal<K,V>> tlTraversal = ThreadLocal.withInitial(Traversal::new);
    
    public BplusTree(final NodeStore<K,V> store) {
        this.store = store;
    }

    public int height() {
        Node<K,V> node = store.getRoot();
        int ret = 1;
        while(node.isBranch()) {
            ++ret;
            node = node.asBranch().left(0);
        }

        return ret;
    }

    public List<K> nodeKeyList() {
        final List<K> ret = new ArrayList<>();
        Node<K,V> node = store.getRoot();
        if(node.isBranch()) {
            nodeKeyListBranch(ret, node.asBranch());
        }
        else {
            return Collections.emptyList();
        }

        return ret;
    }

    public void nodeKeyListBranch(final List<K> list, final Branch<K,V> branch) {
        if(branch.left(0).isLeaf()) {
            list.addAll(branch.keys());
            return;
        }

        for(int i = 0; i < branch.nodeSize(); ++i) {
            nodeKeyListBranch(list, branch.child(i).asBranch());
            if(i != branch.size()) {
                list.add(branch.key(i));
            }
        }
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
            final Node<K,V> orphan = traversal.adoptOrphan();
            newRoot.put(0, store.getRoot(), orphan.getMinKey(), orphan);
            store.setRoot(newRoot);
        }
    }

    private void putLeaf(final Traversal<K,V> traversal, final K k, final V v) {
        final Node<K,V> current = traversal.getCurrent().getNode();
        final Leaf<K,V> leaf = current.asLeaf();
        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSibling();
        
        if(!leaf.isFull()) {
            if(leaf.insert(k, v) == 0 && leftRel != null) {
                leftRel.getParent().put(leftRel.getIndex(), leaf.getMinKey());
            }
            
            return;
        }

        if(leftRel != null && !leftRel.getSibling().isFull()) {
            final Leaf<K,V> sibling = leftRel.getSibling().asLeaf();
            sibling.sizeUp(1);
            sibling.put(sibling.size() - 1, leaf.getMinKey(), leaf.getMinValue());
            leaf.shiftLeft(1, 1).sizeDown(1);
            leaf.insert(k, v);
            leftRel.getParent().put(leftRel.getIndex(), leaf.getMinKey());
            return;
        }
        
        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSibling();
        if(rightRel != null && !rightRel.getSibling().isFull()) {
            final Leaf<K,V> sibling = rightRel.getSibling().asLeaf();
            sibling.sizeUp(1).shiftRight(0, 1);
            
            final int searchIndex = leaf.search(k);
            final int insertIndex = searchIndex >= 0 ? searchIndex : Node.insertIndex(searchIndex);
            if(insertIndex == leaf.size()) {
                sibling.put(0, k, v);
            }
            else {
                sibling.put(0, leaf.getMaxKey(), leaf.getMaxValue());
                leaf.sizeDown(1);
                if(leaf.insert(k, v) == 0 && leftRel != null) {
                    leftRel.getParent().put(leftRel.getIndex(), leaf.getMinKey());
                }
            }

            rightRel.getParent().put(rightRel.getIndex(), sibling.getMinKey());
            return;
        }

        final Leaf<K,V> newRightSibling = leaf.split(k, v);
        traversal.disown(newRightSibling);
        if(leftRel != null) {
            leftRel.getParent().put(leftRel.getIndex(), leaf.getMinKey());
        }
    }

    private void putBranch(final Traversal<K,V> traversal) {
        final Traversal.Entry<K,V> currentEntry = traversal.getCurrent();
        final Branch<K,V> current = currentEntry.getNode().asBranch();
        final Node<K,V> orphan = traversal.adoptOrphan();

        if(!current.isFull()) {
            final int nodeIndex = current.insert(orphan);
            if(nodeIndex == 0) {
                final Traversal.Entry<K,V> parentEntry = traversal.getParent();
                if(parentEntry != null && parentEntry.getDirection() == Direction.RIGHT) {
                    final Branch<K,V> parent = parentEntry.getNode().asBranch();
                    parent.put(parentEntry.getIndex(), orphan.getMinKey());
                }
            }

            return;
        }

        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSibling();
        if(leftRel != null && !leftRel.getSibling().isFull()) {
            //move min to left sibling
            final Branch<K,V> sibling = leftRel.getSibling().asBranch();
            final Node<K,V> toMove = current.left(0);
            sibling.sizeUp(1);
            sibling.put(sibling.size() - 1, toMove.getMinKey(), toMove);

            //insert into current
            current.shiftLeft(1, 1).sizeDown(1);
            current.insert(orphan);

            //reset getParent key
            leftRel.getParent().put(leftRel.getIndex(), current.getMinKey());
            return;
        }
        
        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSibling();
        if(rightRel != null && !rightRel.getSibling().isFull()) {
            final Branch<K,V> sibling = rightRel.getSibling().asBranch();
            sibling.sizeUp(1).shiftRight(0, 1);
            final int searchIndex = current.search(orphan.getMinKey());
            final int insertIndex = searchIndex >= 0 ? searchIndex : Node.insertIndex(searchIndex);
            if(insertIndex == current.size()) {
                sibling.put(0, orphan, sibling.right(0).getMinKey());
            }
            else {
                sibling.put(0, current.right(current.size() - 1), sibling.right(0).getMinKey());
                current.sizeDown(1);
                if(current.insert(orphan) == 0 && leftRel != null) {
                    leftRel.getParent().put(leftRel.getIndex(), orphan.getMinKey());
                }
            }

            rightRel.getParent().put(rightRel.getIndex(), sibling.getMinKey());
            return;
        }

        final Node<K,V> newRight = current.split(orphan);
        traversal.disown(newRight);
        if(leftRel != null) {
            leftRel.getParent().put(leftRel.getIndex(), current.getMinKey());
        }
    }
    
    public V get(final K k) {
        final Traversal<K,V> tr = tlTraversal.get().execute(store.getRoot(), k);
        final Leaf<K,V> leaf = tr.getCurrent().getNode().asLeaf();
        final int index = leaf.search(k);
        return index >= 0 ? leaf.value(index) : null;
    }

}
