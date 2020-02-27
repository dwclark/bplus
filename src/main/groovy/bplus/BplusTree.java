package bplus;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import static bplus.Node.insertIndex;

public class BplusTree<K extends Comparable<K>,V> implements Map<K,V>, SortedMap<K,V> {

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
        
        depthFirst(store.getRoot(), (node) -> {
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
        depthFirst(store.getRoot(), Node<K,V>::done);
        store.setRoot(store.getRoot().newLeaf());
    }

    public Comparator<? super K> comparator() {
        return null;
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

    public K firstKey() {
        if(isEmpty()) {
            throw new NoSuchElementException("tree is empty");
        }
        else {
            return store.getRoot().key(0);
        }        
    }
    
    public SortedMap<K,V> headMap(final K toKey) {
        final Traversal<K,V> lower = Traversal.<K,V>newMutable().leftTraversal(store.getRoot());
        final Traversal<K,V> upper = Traversal.<K,V>newMutable().execute(store.getRoot(), toKey);
        return boundMap(lower, upper); 
    }

    public SortedMap<K,V> tailMap(final K fromKey) {
        final Traversal<K,V> lower = Traversal.<K,V>newMutable().execute(store.getRoot(), fromKey);
        final Traversal<K,V> upper = Traversal.<K,V>newMutable().rightTraversal(store.getRoot());
        return boundMap(lower, upper);
    }

    public SortedMap<K,V> subMap(final K fromKey, final K toKey) {
        checkRange(fromKey, toKey);
        final Traversal<K,V> lower = Traversal.<K,V>newMutable().execute(store.getRoot(), fromKey);
        final Traversal<K,V> upper = Traversal.<K,V>newMutable().execute(store.getRoot(), toKey);
        return boundMap(lower, upper);
    }

    public K lastKey() {
        if(isEmpty()) {
            throw new NoSuchElementException("tree is empty");
        }

        final Traversal.Traverser<K,V> traverser = tlTraversal.get().rightTraversal(store.getRoot()).traverser();
        traverser.previous();
        return traverser.getLeaf().key(traverser.getIndex());
    }

    @Override
    public boolean equals(final Object o) {
        if(!(o instanceof Map)) {
            return false;
        }

        final Map rhs = (Map) o;
        return entrySet().equals(rhs.entrySet());
    }

    @Override
    public int hashCode() {
        return entrySet().hashCode();
    }

    //views
    //TODO: add more efficient versions of methods where possible (mainly find based methods)
    protected static <T> int iteratorCount(final Iterator<T> iter) {
        int count = 0;
        while(iter.hasNext()) {
            ++count;
            iter.next();
        }
        
        return count;
    }

    private void checkRange(final K fromKey, final K toKey) {
        if(fromKey.compareTo(toKey) > 0) {
            throw new IllegalArgumentException("fromKey is greater than toKey");
        }
    }

    private class KeysIterator implements Iterator<K> {
        private final Traversal.Traverser<K,V> traverser;
        
        KeysIterator() {
            this.traverser = Traversal.<K,V>newMutable().leftTraversal(store.getRoot()).traverser();
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

    private class BoundKeysIterator implements Iterator<K> {
        private final Traversal<K,V> lower;
        private final Traversal<K,V> upper;
        private final Traversal.Traverser<K,V> traverser;
        
        BoundKeysIterator(final BoundMap map) {
            this.lower = map.lower.mutable();
            this.upper = map.upper;
            this.traverser = lower.traverser();
        }

        public boolean hasNext() {
            return traverser.hasNext() && lower.compareTo(upper) < 0;
        }

        public K next() {
            traverser.next();
            return traverser.getLeaf().key(traverser.getIndex());
        }
    }

    private class BoundKeysSet extends AbstractSet<K> {
        private final BoundMap map;

        BoundKeysSet(final BoundMap map) {
            this.map = map;
        }
        
        public int size() {
            return iteratorCount(iterator());
        }
        
        public Iterator<K> iterator() {
            return new BoundKeysIterator(map);
        }
    }
    
    private class ValuesIterator implements Iterator<V> {

        private final Traversal.Traverser<K,V> traverser;
        
        ValuesIterator() {
            this.traverser = Traversal.<K,V>newMutable().leftTraversal(store.getRoot()).traverser();
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

        BoundValuesIterator(final BoundMap map) {
            lower = map.lower.mutable();
            upper = map.upper;
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

    private class BoundValuesCollection extends AbstractCollection<V> {
        private final BoundMap map;
        
        BoundValuesCollection(final BoundMap map) {
            this.map = map;
        }

        public int size() {
            return iteratorCount(iterator());
        }
        
        public Iterator<V> iterator() {
            return new BoundValuesIterator(map);
        }
    }

    private class EntriesIterator implements Iterator<Map.Entry<K,V>> {
        private final Traversal.Traverser<K,V> traverser;

        EntriesIterator() {
            this.traverser = Traversal.<K,V>newMutable().leftTraversal(store.getRoot()).traverser();
        }

        public boolean hasNext() { return traverser.hasNext(); }

        public Map.Entry<K,V> next() {
            traverser.next();
            return traverser.getLeaf().entry(traverser.getIndex());
        }
    }

    private class EntriesSet extends AbstractSet<Map.Entry<K,V>> {
        public int size() { return BplusTree.this.size(); }
        public Iterator<Map.Entry<K,V>> iterator() { return new EntriesIterator(); }
    }

    private class BoundEntriesIterator implements Iterator<Map.Entry<K,V>> {
        private final Traversal<K,V> lower;
        private final Traversal<K,V> upper;
        private final Traversal.Traverser<K,V> traverser;

        BoundEntriesIterator(final BoundMap map) {
            lower = map.lower.mutable();
            upper = map.upper;
            traverser = lower.traverser();
        }

        public boolean hasNext() {
            return traverser.hasNext() && lower.compareTo(upper) < 0;
        }

        public Map.Entry<K,V> next() {
            traverser.next();
            return traverser.getLeaf().entry(traverser.getIndex());
        }
    }

    private class BoundEntriesSet extends AbstractSet<Map.Entry<K,V>> {
        private final BoundMap map;

        public BoundEntriesSet(final BoundMap map) {
            this.map = map;
        }
        
        public int size() {
            return iteratorCount(iterator());
        }
        
        public Iterator<Map.Entry<K,V>> iterator() {
            return new BoundEntriesIterator(map);
        }
    }

    private BoundMap boundMap(final Traversal<K,V> lower, final Traversal<K,V> upper) {
        return boundMap(lower, true, upper, false);
    }
    
    private BoundMap boundMap(final Traversal<K,V> lower, final boolean lowerInclusive,
                              final Traversal<K,V> upper, final boolean upperInclusive) {
        return new BoundMap(fixLowerBounds(lower, lowerInclusive).immutable(),
                            fixUpperBounds(upper, upperInclusive).immutable());
    }

    private Traversal<K,V> fixLowerBounds(final Traversal<K,V> lower, final boolean inclusive) {
        final Traversal<K,V>.Entry entry = lower.current();

        if(entry.getIndex() < 0) {
            entry.setIndex(insertIndex(entry.getIndex()) - 1);
        }
        else if(inclusive) {
            //found an exact match. if exclusive, keep the index where it is
            //as doing this excludes the lower bound. otherwise, back up one
            //to include the exact match.
            entry.setIndex(entry.getIndex() - 1);
        }

        return lower;
    }

    private Traversal<K,V> fixUpperBounds(final Traversal<K,V> upper, final boolean inclusive) {
        final Traversal<K,V>.Entry entry = upper.current();
        final boolean goPrevious = entry.getIndex() < 0 || (!inclusive && entry.getIndex() >= 0);
        if(entry.getIndex() < 0) {
            entry.setIndex(insertIndex(entry.getIndex()));
        }

        if(goPrevious) {
            upper.traverser().previous();
        }
        
        return upper;
    }

    private class BoundMap implements Map<K,V>, SortedMap<K,V> {
        private final Traversal<K,V> lower;
        private final Traversal<K,V> upper;

        BoundMap(final Traversal<K,V> lower, final Traversal<K,V> upper) {
            this.lower = lower;
            this.upper = upper;
        }

        public BoundMap changeLower(final Traversal<K,V> newLower, final boolean inclusive) {
            return new BoundMap(fixLowerBounds(newLower, inclusive), upper);
        }

        public BoundMap changeLower(final Traversal<K,V> newLower) {
            return changeLower(newLower, true);
        }

        public BoundMap changeUpper(final Traversal<K,V> newUpper, final boolean inclusive) {
            return new BoundMap(lower, fixUpperBounds(newUpper, inclusive));
        }

        public BoundMap changeUpper(final Traversal<K,V> newUpper) {
            return changeUpper(newUpper, false);
        }

        private <R> R checkBounds(final K k, final Function<Traversal<K,V>, R> func) {
            final Traversal<K,V> tr = Traversal.<K,V>newMutable().execute(store.getRoot(), k);
            if(lower.compareTo(tr) < 0 && tr.compareTo(upper) <= 0) {
                return func.apply(tr);
            }
            else {
                throw new IllegalArgumentException("key not within bounds of map");
            }
        }

        private <R> R checkBounds(final K k, final R ret, final Function<Traversal<K,V>, R> func) {
            final Traversal<K,V> tr = Traversal.<K,V>newMutable().execute(store.getRoot(), k);
            if(lower.compareTo(tr) < 0 && tr.compareTo(upper) <= 0) {
                return func.apply(tr);
            }
            else {
                return ret;
            }
        }

        public Comparator<? super K> comparator() {
            return null;
        }
        
        public void clear() {
            throw new UnsupportedOperationException();
        }

        public boolean containsKey(final Object o) {
            return checkBounds(store.getKeyType().cast(o), false,
                               (tr) -> tr.current().getIndex() >= 0 ? true : false);
        }

        public boolean containsValue(final Object o) {
            final V v = store.getValueType().cast(o);
            for(V toTest : values()) {
                if(toTest.equals(v)) {
                    return true;
                }
            }

            return false;
        }

        public Set<Map.Entry<K,V>> entrySet() {
            return new BoundEntriesSet(this);
        }

        @Override
        public boolean equals(final Object o) {
            if(!(o instanceof Map)) {
                return false;
            }
            
            final Map rhs = (Map) o;
            return entrySet().equals(rhs.entrySet());
        }

        private V _get(final Traversal<K,V> tr) {
            final int index = tr.current().getIndex();
            return index >= 0 ? tr.current().getNode().asLeaf().value(index) : null;
        }
        
        public V get(final Object o) {
            return checkBounds(store.getKeyType().cast(o), null, this::_get);
        }

        @Override
        public int hashCode() {
            return entrySet().hashCode();
        }

        public K firstKey() {
            if(isEmpty()) {
                throw new NoSuchElementException();
            }
            else {
                final Traversal.Traverser<K,V> traverser = lower.mutable().traverser();
                traverser.next();
                return traverser.getLeaf().key(traverser.getIndex());
            }
        }

        public boolean isEmpty() {
            return lower.compareTo(upper) >= 0;
        }

        public BoundMap headMap(final K k) {
            return checkBounds(k, this::changeUpper);
        }

        public Set<K> keySet() {
            return new BoundKeysSet(this);
        }

        public K lastKey() {
            if(isEmpty()) {
                throw new NoSuchElementException();
            }
            else {
                final Traversal.Traverser<K,V> traverser = upper.mutable().traverser();
                return traverser.getLeaf().key(traverser.getIndex());
            }
        }

        public V put(final K k, final V v) {
            return checkBounds(k, (tr) -> BplusTree.this.put(k, v));
        }

        public void putAll(final Map<? extends K,? extends V> m) {
            for(Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }

        public V remove(final Object o) {
            final K k = store.getKeyType().cast(o);
            final Traversal<K,V> tr = tlTraversal.get().execute(store.getRoot(), k);
            if(lower.compareTo(tr) < 0 && tr.compareTo(upper) <= 0) {
                return BplusTree.this.remove(o);
            }
            else {
                return null;
            }
        }

        public int size() {
            return keySet().size();
        }

        public BoundMap subMap(final K fromKey, final K toKey) {
            checkRange(fromKey, toKey);
            return checkBounds(fromKey, (tr1) -> { return checkBounds(toKey, (tr2) -> { return new BoundMap(tr1, tr2); }); });
        }

        public BoundMap tailMap(final K k) {
            return checkBounds(k, this::changeLower);
        }
        
        public Collection<V> values() {
            return new BoundValuesCollection(this);
        }
    }

    public boolean assertOrders() {
        final Node<K,V> root = store.getRoot();
        if(root.isLeaf()) {
            return true;
        }

        final Branch<K,V> branch = root.asBranch();
        for(int i = 0; i < branch.size(); ++i) {
            depthFirst(branch.child(i), this::assertOrder);
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

        depthFirst(root, this::assertValidKeys);
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
        final Traversal.Traverser<K,V> traverser = Traversal.<K,V>newMutable().leftTraversal(store.getRoot()).traverser();
        while(traverser.hasNext()) {
            traverser.next();
            ret.add(traverser.getLeaf().key(traverser.getIndex()));
        }

        return ret;
    }
    
    private void depthFirst(final Node<K,V> root, final Consumer<Node<K,V>> consumer) {
        if(root.isBranch()) {
            final Branch<K,V> branch = root.asBranch();
            for(int i = 0; i < branch.size(); ++i) {
                depthFirst(branch.child(i), consumer);
            }
        }

        consumer.accept(root);
    }
}
