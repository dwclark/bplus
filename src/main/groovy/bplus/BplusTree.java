package bplus;

import java.util.*;
import java.util.function.Consumer;
import static bplus.Node.insertIndex;

public class BplusTree<K extends Comparable<K>,V> implements Map<K,V>  {

    private final NodeStore<K,V> store;
    private final ThreadLocal<Traversal<K,V>> tlTraversal = ThreadLocal.withInitial(Traversal::newMutable);
    
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

    public long longSize() {
        final long[] ary = new long[1];
        
        walkNodes(store.getRoot(), (node) -> {
                if(node.isLeaf()) {
                    ary[0] += node.size();
                }
            });
                        
        return ary[0];
    }

    public V value(final K k) {
        final Traversal<K,V> tr = tlTraversal.get().execute(store.getRoot(), k);
        final Leaf<K,V> leaf = tr.current().getNode().asLeaf();
        final int index = tr.current().getIndex();
        return index >= 0 ? leaf.value(index) : null;
    }

    public V put(final K k, final V v) {
        final Traversal<K,V> traversal = tlTraversal.get().execute(store.getRoot(), k);
        final Traversal<K,V>.Entry foundEntry = traversal.current();
        V ret = null;
        
        if(foundEntry.getIndex() >= 0) {
            final Leaf<K,V> leaf = foundEntry.getNode().asLeaf();
            ret = leaf.value(foundEntry.getIndex());
        }
        
        while(traversal.level() >= 0) {
            final Node<K,V> current = traversal.current().getNode();

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
                return ret;
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

        return ret;
    }

    public V delete(final K k) {
        final Node<K,V> root = store.getRoot();
        final Traversal<K,V> traversal = tlTraversal.get().execute(store.getRoot(), k);
        final Leaf<K,V> leaf = traversal.current().getNode().asLeaf();
        final int index = leaf.search(k);
        final V ret = (index >= 0) ? leaf.value(index) : null;
        if(index >= 0) {
            while(traversal.level() >= 0) {
                final Node<K,V> current = traversal.current().getNode();
                
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

            if(root.isBranch() && root.size() == 1) {
                traversal.addDone(root);
                store.setRoot(root.asBranch().child(0));
            }

            traversal.done();
        }
        
        return ret;
    }
    
    private void putLeaf(final Traversal<K,V> traversal, final K k, final V v) {
        final Leaf<K,V> leaf = traversal.current().getNode().asLeaf();

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
            sibling.put(sibling.lastIndex(), leaf.key(0), leaf.value(0));
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
                sibling.put(0, leaf.lastKey(), leaf.lastValue());
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
        final Traversal<K,V>.Entry currentEntry = traversal.current();
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
            sibling.put(sibling.lastIndex(), current.child(0));
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
            final int searchIndex = current.search(orphan.key(0));
            final int insertIndex = searchIndex >= 0 ? searchIndex : Node.insertIndex(searchIndex);
            if(insertIndex == current.size()) {
                sibling.put(0, orphan);
            }
            else {
                sibling.put(0, current.child(current.lastIndex()));
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
        final Leaf<K,V> current = traversal.current().getNode().asLeaf();

        //case: base, can always delete the leaf key/value
        current.remove(index);
        if(index == 0) {
            traversal.resetAncestorKeys();
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
                final int lastIndex = sibling.lastIndex();
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
                final Traversal<K,V>.Entry parentEntry = traversal.parent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex());
                if(parentEntry.getIndex() == 0) {
                    traversal.resetAncestorKeys();
                }
                
                traversal.addDone(current);
            }

            return;
        }

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
                rightRel.resetAncestorKeys();
            }
            else {
                //case: merge with right sibling
                sibling.sizeUp(current.size());
                sibling.shiftRight(0, current.size());
                sibling.copy(0, current, 0, current.size());
                rightRel.resetAncestorKeys();
                final Traversal<K,V>.Entry parentEntry = traversal.parent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex());
                if(parentEntry.getIndex() == 0) {
                    traversal.resetAncestorKeys();
                }
                
                traversal.addDone(current);
            }
        }
    }
    
    private void removeBranch(final Traversal<K,V> traversal) {
        //current is below limit, child was already removed previously
        final Branch<K,V> current = traversal.current().getNode().asBranch();

        final Traversal.SiblingRelation<K,V> leftRel = traversal.getLeftSibling();
        if(leftRel != null) {
            final Branch<K,V> sibling = leftRel.getSibling().asBranch();
            if(sibling.isAboveMinLimit()) {
                //case: borrow from the left
                current.sizeUp(1);
                current.shiftRight(0, 1);
                current.put(0, sibling.child(sibling.lastIndex()));
                sibling.sizeDown(1);
                traversal.resetAncestorKeys();
            }
            else {
                //case: merge with the left
                final int insertIndex = sibling.size();
                sibling.sizeUp(current.size());
                sibling.copy(0, current, insertIndex, current.size());
                final Traversal<K,V>.Entry parentEntry = traversal.parent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex());
                if(parentEntry.getIndex() == 0) {
                    traversal.resetAncestorKeys();
                }
                
                traversal.addDone(current);
            }
            
            return;
        }

        final Traversal.SiblingRelation<K,V> rightRel = traversal.getRightSibling();
        if(rightRel != null) {
            final Branch<K,V> sibling = rightRel.getSibling().asBranch();
            if(sibling.isAboveMinLimit()) {
                //case: borrow from the right
                final int insertIndex = current.size();
                current.sizeUp(1);
                current.put(insertIndex, sibling.child(0));
                sibling.shiftLeft(1, 1);
                sibling.sizeDown(1);
                rightRel.resetAncestorKeys();
            }
            else {
                //case: merge withthe right
                sibling.sizeUp(current.size());
                sibling.shiftRight(0, current.size());
                sibling.copy(0, current, 0, current.size());
                rightRel.resetAncestorKeys();

                final Traversal<K,V>.Entry parentEntry = traversal.parent();
                final Branch<K,V> parent = parentEntry.getNode().asBranch();
                parent.remove(parentEntry.getIndex());
                if(parentEntry.getIndex() == 0) {
                    traversal.resetAncestorKeys();
                }

                traversal.addDone(current);
            }
        }
    }

    //jdk interface methods
    public void clear() {
        walkNodes(store.getRoot(), Node<K,V>::done);
        store.setRoot(store.getRoot().newLeaf());
    }

    public boolean containsKey(final Object o) {
        final K k = store.getKeyType().cast(o);
        final Traversal<K,V> tr = tlTraversal.get().execute(store.getRoot(), k);
        final int index = tr.current().getIndex();
        return index >= 0;
    }

    public boolean containsValue(final Object val) {
        final V v = store.getValueType().cast(val);
        for(V toTest : values()) {
            if(toTest.equals(v)) {
                return true;
            }
        }

        return false;
    }

    public V get(final Object o) {
        return value(store.getKeyType().cast(o));
    }

    public void putAll(final Map<? extends K, ? extends V> map) {
        for(Map.Entry<? extends K,? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public V remove(final Object o) {
        return delete(store.getKeyType().cast(o));
    }

    public int size() {
        return (int) longSize();
    }

    public boolean isEmpty() {
        return store.getRoot().size() == 0;
    }

    public Set<K> keySet() {
        return new KeysSet();
    }

    public Set<Map.Entry<K,V>> entrySet() {
        return new EntriesSet();
    }
    
    public Collection<V> values() {
        return new ValuesCollection();
    }

    public Comparator<? super K> comparator() {
        return null;
    }

    public K firstKey() {
        if(isEmpty()) {
            throw new NoSuchElementException("tree is empty");
        }
        else {
            return store.getRoot().key(0);
        }        
    }

    public K lastKey() {
        if(isEmpty()) {
            throw new NoSuchElementException("tree is empty");
        }

        final Traversal.Traverser<K,V> traverser = tlTraversal.get().rightTraverser(store.getRoot());
        traverser.previous();
        return traverser.getLeaf().key(traverser.getIndex());
    }

    public boolean equals(final Object o) {
        if(!(o instanceof Map)) {
            return false;
        }

        final Map rhs = (Map) o;
        return entrySet().equals(rhs.entrySet());
    }

    public int hashCode() {
        return entrySet().hashCode();
    }

    //views
    //TODO: add more efficient versions of methods where possible (mainly find based methods)
    private class KeysIterator implements Iterator<K> {
        private final Traversal.Traverser<K,V> traverser;
        
        KeysIterator() {
            this.traverser = Traversal.<K,V>newMutable().leftTraverser(store.getRoot());
        }

        public boolean hasNext() { return traverser.hasNext(); }

        public K next() {
            traverser.next();
            return traverser.getLeaf().key(traverser.getIndex());
        }
    }

    private class KeysSet extends AbstractSet<K> {
        public int size() { return BplusTree.this.size(); }
        public Iterator<K> iterator() { return new KeysIterator(); }
    }
    
    private class ValuesIterator implements Iterator<V> {

        private final Traversal.Traverser<K,V> traverser;
        
        ValuesIterator() {
            this.traverser = Traversal.<K,V>newMutable().leftTraverser(store.getRoot());
        }

        public boolean hasNext() { return traverser.hasNext(); }
        
        public V next() {
            traverser.next();
            return traverser.getLeaf().value(traverser.getIndex());
        }
    }

    private class ValuesCollection extends AbstractCollection<V> {
        public int size() { return BplusTree.this.size(); }
        public Iterator<V> iterator() { return new ValuesIterator(); }
    }

    private class BoundValuesIterator implements Iterator<V> {
        private final Traversal<K,V> lower;
        private final Traversal<K,V> upper;
        private final Traversal.Traverser<K,V> traverser;

        BoundValuesIterator(final Traversal<K,V> passedLower, final Traversal<K,V> passedUpper) {
            lower = passedLower.mutable();
            upper = passedUpper;
            traverser = lower.traverser();
        }

        public boolean hasNext() {
            return traverser.hasNext() && lower.compareTo(upper) < 0;
        }
        
        public V next() {
            traverser.next();
            return traverser.getLeaf().value(traverser.getIndex());
        }
    }

    //TODO: need to allow for include/exclusive options in NavigableMap
    private class BoundValuesCollection extends AbstractCollection<V> {
        private final Traversal<K,V> lower;
        private final Traversal<K,V> upper;
        
        BoundValuesCollection(final Traversal<K,V> lower, final Traversal<K,V> upper) {
            this.lower = lower;
            this.upper = upper;
        }

        public int size() {
            throw new UnsupportedOperationException();
        }
        
        public Iterator<V> iterator() {
            return new BoundValuesIterator(lower, upper);
        }
    }

    private class EntriesIterator implements Iterator<Map.Entry<K,V>> {
        private final Traversal.Traverser<K,V> traverser;

        EntriesIterator() {
            this.traverser = Traversal.<K,V>newMutable().leftTraverser(store.getRoot());
        }

        public boolean hasNext() { return traverser.hasNext(); }

        public Map.Entry<K,V> next() {
            return traverser.getLeaf().entry(traverser.getIndex());
        }
    }

    private class EntriesSet extends AbstractSet<Map.Entry<K,V>> {
        public int size() { return BplusTree.this.size(); }
        public Iterator<Map.Entry<K,V>> iterator() { return new EntriesIterator(); }
    }

    private class BoundMap {
        private final Traversal<K,V> lower;
        private final Traversal<K,V> upper;
        
        BoundMap(final Traversal<K,V> lower, final Traversal<K,V> upper) {
            this.lower = fixLowerBounds(lower).immutable();
            this.upper = fixUpperBounds(upper).immutable();
        }

        private Traversal<K,V> fixLowerBounds(final Traversal<K,V> lower) {
            final Traversal<K,V>.Entry entry = lower.current();
            if(entry.getIndex() < 0) {
                entry.setIndex(insertIndex(entry.getIndex()));
            }

            entry.setIndex(entry.getIndex() - 1);
            return lower;
        }

        private Traversal<K,V> fixUpperBounds(final Traversal<K,V> upper) {
            final Traversal<K,V>.Entry entry = upper.current();
            if(entry.getIndex() < 0) {
                entry.setIndex(insertIndex(entry.getIndex()));
            }
            else {
                entry.setIndex(entry.getIndex() + 1);
            }

            return upper;
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }

        public boolean containsKey(final Object o) {
            final K k = store.getKeyType().cast(o);
            final Traversal<K,V> tr = tlTraversal.get().execute(store.getRoot(), k);
            final int index = tr.current().getIndex();
            return (index >= 0 &&
                    lower.compareTo(tr) < 0 &&
                    tr.compareTo(upper) < 0);
        }
    }

    public boolean assertOrders() {
        final Node<K,V> root = store.getRoot();
        if(root.isLeaf()) {
            return true;
        }

        final Branch<K,V> branch = root.asBranch();
        for(int i = 0; i < branch.size(); ++i) {
            walkNodes(branch.child(i), this::assertOrder);
        }
        
        return true;
    }

    private void assertOrder(final Node<K,V> node) {
        if(node.isBelowLimit()) {
            final String type = node.isBranch() ? "branch" : "leaf";
            final String s = String.format("%s with min value %s is below min %d",
                                           type, node.key(0), node.getMinLimit());
            throw new RuntimeException(s);
        }
    }

    public boolean assertValidKeys() {
        Node<K,V> root = store.getRoot();
        if(root.isLeaf()) {
            return true;
        }

        walkNodes(root, this::assertValidKeys);
        return true;
    }

    private void assertValidKeys(final Node<K,V> node) {
        if(!node.isBranch()) {
            return;
        }

        final Branch<K,V> branch = node.asBranch();
        for(int i = 0; i < branch.size(); ++i) {
            if(branch.key(i).compareTo(branch.child(i).key(0)) != 0) {
                String msg = String.format("messed up parent: %s, child: %s",
                                           branch.key(i), branch.child(i).key(0));
                throw new RuntimeException(msg);
            }
        }
    }
    
    public List<K> keyList() {
        final List<K> ret = new ArrayList<>();
        final Traversal.Traverser<K,V> traverser = Traversal.<K,V>newMutable().leftTraverser(store.getRoot());
        while(traverser.hasNext()) {
            traverser.next();
            ret.add(traverser.getLeaf().key(traverser.getIndex()));
        }

        return ret;
    }

    private void walkNodes(final Node<K,V> root, final Consumer<Node<K,V>> consumer) {
        consumer.accept(root);
        if(root.isBranch()) {
            final Branch<K,V> branch = root.asBranch();
            for(int i = 0; i < branch.size(); ++i) {
                walkNodes(branch.child(i), consumer);
            }
        }
    }
}
