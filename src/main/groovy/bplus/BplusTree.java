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
            node = node.asBranch().child(0);
        }

        return ret;
    }

    public V get(final K k) {
        final Traversal<K,V> tr = tlTraversal.get().execute(store.getRoot(), k);
        final Leaf<K,V> leaf = tr.getCurrent().getNode().asLeaf();
        final int index = leaf.search(k);
        return index >= 0 ? leaf.value(index) : null;
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
            final Node<K,V> orphan = traversal.adoptOrphan();
            final Branch<K,V> newRoot = store.getRoot().newBranch();
            newRoot.sizeUp(2);
            newRoot.put(0, store.getRoot());
            newRoot.put(1, orphan);
            store.setRoot(newRoot);
        }
    }
    
    private void putLeaf(final Traversal<K,V> traversal, final K k, final V v) {
        final Leaf<K,V> leaf = traversal.getCurrent().getNode().asLeaf();

        //case: can insert in current leaf
        if(!leaf.isFull()) {
            if(leaf.insert(k, v) == 0) {
                traversal.resetAncestorKeys();
            }
            
            return;
        }

        //case: can borrow space in left sibling
        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSibling();
        if(leftRel != null && !leftRel.getSibling().isFull()) {
            final Leaf<K,V> sibling = leftRel.getSibling().asLeaf();
            sibling.sizeUp(1);
            sibling.put(sibling.size() - 1, leaf.key(0), leaf.value(0));
            leaf.shiftLeft(1, 1).sizeDown(1);
            leaf.insert(k, v);
            traversal.resetAncestorKeys();
            return;
        }

        //case: can borrow space in right sibling
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
                if(leaf.insert(k, v) == 0) {
                    traversal.resetAncestorKeys();
                }
            }

            rightRel.resetAncestorKeys();
            return;
        }

        //case: split node
        final Leaf<K,V> newRightSibling = leaf.split(k, v);
        traversal.disown(newRightSibling);
        traversal.resetAncestorKeys();
    }

    private void putBranch(final Traversal<K,V> traversal) {
        final Traversal.Entry<K,V> currentEntry = traversal.getCurrent();
        final Branch<K,V> current = currentEntry.getNode().asBranch();
        final Node<K,V> orphan = traversal.adoptOrphan();

        //case: current node is not full
        if(!current.isFull()) {
            final int nodeIndex = current.insert(orphan);
            if(nodeIndex == 0) {
                traversal.resetAncestorKeys();
            }

            return;
        }

        //case: share space with left sibling
        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSibling();
        if(leftRel != null && !leftRel.getSibling().isFull()) {
            final Branch<K,V> sibling = leftRel.getSibling().asBranch();
            sibling.sizeUp(1);
            sibling.put(sibling.size() - 1, current.child(0));
            current.shiftLeft(1, 1).sizeDown(1);
            current.insert(orphan);
            traversal.resetAncestorKeys();
            return;
        }

        //case: share space with right sibling
        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSibling();
        if(rightRel != null && !rightRel.getSibling().isFull()) {
            final Branch<K,V> sibling = rightRel.getSibling().asBranch();
            sibling.sizeUp(1).shiftRight(0, 1);
            final int searchIndex = current.search(orphan.getMinKey());
            final int insertIndex = searchIndex >= 0 ? searchIndex : Node.insertIndex(searchIndex);
            if(insertIndex == current.size()) {
                sibling.put(0, orphan);
            }
            else {
                sibling.put(0, current.child(current.size() - 1));
                current.sizeDown(1);
                if(current.insert(orphan) == 0) {
                    traversal.resetAncestorKeys();
                }
            }

            rightRel.resetAncestorKeys();
            return;
        }

        //case: split branch
        final Node<K,V> newRight = current.split(orphan);
        traversal.disown(newRight);
        traversal.resetAncestorKeys();
    }

    private void removeLeaf(final Traversal<K,V> traversal, final int index) {
        final Leaf<K,V> current = traversal.getCurrent().getNode().asLeaf();

        //case: base, can always remove the leaf key/value
        current.remove(index);
        if(index == 0) {
            resetAncestorKeys();
        }

        //case: leaf is still above limit, we are done
        if(!current.isBelowLimit()) {
            return;
        }

        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSibling();
        if(leftRel != null) {
            final Leaf<K,V> sibling = leftRel.getSibling().asLeaf();
            if(sibling.isAboveMinLimit()) {
                //case: borrow something from left sibling
                final int lastIndex = sibling.size() - 1;
                current.sizeUp(1);
                current.shiftRight(0, 1);
                current.put(0, sibling.key(lastIndex), sibling.value(lastIndex));
                sibling.sizeDown(1);
                traversal.resetAncestorKeys();
            }
            else {
                //case: merge with left sibling
                final int copyIndex = sibling.size();
                sibling.sizeUp(current.size());
                sibling.copy(0, current, copyIndex, current.size());
                final Traversal.Entry<K,V> parentEntry = traversal.getParent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex());
                if(parentEntry.getIndex() == 0) {
                    leftRel.resetAncestorKeys();
                }
                
                traversal.addDone(current);
            }

            return;
        }

        //TODO: start here
        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSibling();
        if(rightRel != null) {
            final Leaf<K,V> sibling = rightRel.getSibling().asLeaf();
            if(sibling.isAboveMinLimit()) {
                //case: borrow something from right sibling
                final int insertIndex = current.size();
                current.sizeUp(1);
                current.put(insertIndex, sibling.key(0), sibling.value(0));
                sibling.shiftLeft(1, 1);
                sibling.sizeDown(1);
                rightRel.getParent().put(rightRel.getIndex(), sibling.getMinKey());
            }
            else {
                //case: merge with right sibling
                sibling.sizeUp(current.size());
                sibling.shiftRight(0, current.size());
                sibling.copy(0, current, 0, current.size());
                final Traversal.Entry<K,V> parentEntry = traversal.getParent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex(), parentEntry.getDirection());
                traversal.addDone(current);
            }
        }
    }

    public void assertValidKeys() {
        Node<K,V> root = store.getRoot();
        if(root.isLeaf()) {
            return;
        }
        else {
            assertValidKeys(root.asBranch());
        }
    }

    private void assertValidKeys(final Branch<K,V> branch) {
        for(int i = 0; i < branch.size(); ++i) {
            if(branch.key(i).compareTo(branch.child(i).key(0)) != 0) {
                String msg = String.format("messed up parent: %s, child: %s",
                                           branch.key(i), branch.child(i).key(0));
                throw new RuntimeException(msg);
            }

            if(branch.child(i).isBranch()) {
                assertValidKeys(branch.child(i).asBranch());
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
        for(int i = 0; i < branch.size(); ++i) {
            final Node<K,V> child = branch.child(i);
            if(child.isBranch()) {
                keyListBranch(list, child.asBranch());
            }
            else {
                keyListLeaf(list, child.asLeaf());
            }
        }
    }

    private void keyListLeaf(final List<K> list, final Leaf<K,V> leaf) {
        for(int i = 0; i < leaf.size(); ++i) {
            list.add(leaf.key(i));
        }
    }

    /*public V remove(final K k) {
        final Node<K,V> root = store.getRoot();
        final Traversal<K,V> traversal = tlTraversal.get().execute(store.getRoot(), k);
        final Leaf<K,V> leaf = traversal.getCurrent().getNode().asLeaf();
        final int index = leaf.search(k);
        final V ret = (index >= 0) ? leaf.value(index) : null;
        if(index >= 0) {
            while(traversal.level() >= 0) {
                final Node<K,V> current = traversal.getCurrent().getNode();
                
                if(current.isLeaf()) {
                    removeLeaf(traversal, index);
                    traversal.pop();
                }
                else if(current != root && current.isBelowLimit()) {
                    removeBranch(traversal);
                    traversal.pop();
                }
                else {
                    break;
                }
            }

            if(root.isBranch() && root.size() == 0) {
                traversal.addDone(root);
                store.setRoot(root.asBranch().child(0));
            }

            traversal.done();
        }
        
        return ret;
    }


    private void removeBranch(final Traversal<K,V> traversal) {
        final Branch<K,V> current = traversal.getCurrent().getNode().asBranch();

        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSibling();
        if(leftRel != null) {
            final Branch<K,V> sibling = leftRel.getSibling().asBranch();
            if(sibling.isAboveMinLimit()) {
                current.sizeUp(1);
                current.shiftRight(0, 1);
                current.put(0, sibling.right(sibling.size() - 1), current.left(0).getMinKey());
                sibling.sizeDown(1);
                leftRel.getParent().put(leftRel.getIndex(), current.getMinKey());
            }
            else {
                final int keyIndex = sibling.size();
                final int insertIndex = keyIndex + 1;
                sibling.sizeUp(current.size() + 1);
                sibling.copy(0, current, insertIndex, current.size());
                sibling.put(keyIndex, sibling.right(keyIndex).getMinKey());
                final Traversal.Entry<K,V> parentEntry = traversal.getParent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex(), parentEntry.getDirection());

                //need to reset the grandparent key if it exists
                if(parentEntry.getIndex() == 0) {
                    final Traversal.Entry<K,V> grandEntry = traversal.getGrandparent();
                    if(grandEntry != null) {
                        if(grandEntry.getDirection() == Direction.RIGHT) {
                            grandEntry.getNode().asBranch().put(grandEntry.getIndex(), parent.getMinKey());
                        }
                        else if(grandEntry.getDirection() == Direction.LEFT &&
                                grandEntry.getIndex() > 0) {
                            grandEntry.getNode().asBranch().put(grandEntry.getIndex() - 1, parent.getMinKey());
                        }
                    }
                }

                traversal.addDone(current);
            }
            
            return;
        }

        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSibling();
        if(rightRel != null) {
            final Branch<K,V> sibling = rightRel.getSibling().asBranch();
            if(sibling.isAboveMinLimit()) {
                final int insertIndex = current.size();
                current.sizeUp(1);
                final Node<K,V> toInsert = sibling.left(0);
                current.put(insertIndex, toInsert.getMinKey(), toInsert);
                sibling.shiftLeft(1, 1);
                sibling.sizeDown(1);
                rightRel.getParent().put(rightRel.getIndex(), sibling.getMinKey());
            }
            else {
                sibling.sizeUp(current.size() + 1);
                sibling.shiftRight(0, current.size() + 1);
                sibling.copy(0, current, 0, current.size());
                sibling.put(current.size(), sibling.right(current.size()).getMinKey());
                rightRel.getParent().put(rightRel.getIndex(), sibling.getMinKey());

                final Traversal.Entry<K,V> parentEntry = traversal.getParent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex(), parentEntry.getDirection());

                //since we are the left-most node, there is no need to attempt to
                //reset the min key since we are to the right of nobody 
                traversal.addDone(current);
            }

            return;
        }
        }*/
}
