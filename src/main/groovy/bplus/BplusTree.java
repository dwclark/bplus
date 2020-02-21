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

    /*public List<K> nodeKeyList() {
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
            final Branch<K,V> sibling = leftRel.getSibling().asBranch();
            final Node<K,V> toMove = current.left(0);
            sibling.sizeUp(1);
            sibling.put(sibling.size() - 1, toMove.getMinKey(), toMove);
            current.shiftLeft(1, 1).sizeDown(1);
            current.insert(orphan);
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

    public V remove(final K k) {
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

    private void removeLeaf(final Traversal<K,V> traversal, final int index) {
        final Leaf<K,V> current = traversal.getCurrent().getNode().asLeaf();
        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSibling();
        current.remove(index);
        if(index == 0 && leftRel != null) {
            leftRel.getParent().put(leftRel.getIndex(), current.getMinKey());
        }
        
        if(!current.isBelowLimit()) {
            return;
        }
        
        if(leftRel != null) {
            final Leaf<K,V> sibling = leftRel.getSibling().asLeaf();
            if(sibling.isAboveMinLimit()) {
                final int lastIndex = sibling.size() - 1;
                current.sizeUp(1);
                current.shiftRight(0, 1);
                current.put(0, sibling.key(lastIndex), sibling.value(lastIndex));
                sibling.sizeDown(1);
                leftRel.getParent().put(leftRel.getIndex(), current.getMinKey());
            }
            else {
                final int copyIndex = sibling.size();
                sibling.sizeUp(current.size());
                sibling.copy(0, current, copyIndex, current.size());
                final Traversal.Entry<K,V> parentEntry = traversal.getParent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex(), parentEntry.getDirection());
                traversal.addDone(current);
            }

            return;
        }

        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSibling();
        if(rightRel != null) {
            final Leaf<K,V> sibling = rightRel.getSibling().asLeaf();
            if(sibling.isAboveMinLimit()) {
                final int insertIndex = current.size();
                current.sizeUp(1);
                current.put(insertIndex, sibling.key(0), sibling.value(0));
                sibling.shiftLeft(1, 1);
                sibling.sizeDown(1);
                rightRel.getParent().put(rightRel.getIndex(), sibling.getMinKey());
            }
            else {
                sibling.sizeUp(current.size());
                sibling.shiftRight(0, current.size());
                sibling.copy(0, current, 0, current.size());
                final Traversal.Entry<K,V> parentEntry = traversal.getParent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex(), parentEntry.getDirection());
                traversal.addDone(current);
            }

            return;
        }
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
