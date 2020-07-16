package bplus;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class BplusTree<K extends Comparable<K>,V> implements Map<K,V>, SortedMap<K,V>, NavigableMap<K,V> {

    private class Working {
        private final List<Node<K,V>> done = new ArrayList<>(4);
        private Node<K,V> orphan = null;
        
        void addDone(final Node<K,V> node) {
            done.add(node);
        }

        void disown(final Node<K,V> node) {
            this.orphan = node;
        }

        boolean hasOrphan() {
            return orphan != null;
        }

        Node<K,V> adoptOrphan() {
            final Node<K,V> ret = orphan;
            orphan = null;
            return ret;
        }

        void done() {
            if(hasOrphan()) {
                throw new IllegalStateException();
            }

            for(Node<K,V> node : done) {
                node.done();
            }

            done.clear();
        }
    }
    
    private final NodeStore<K,V> store;
    private final ThreadLocal<Working> tlWorking = ThreadLocal.withInitial(Working::new);
    
    public BplusTree(final NodeStore<K,V> store) {
        this.store = store;
    }

    public int height() {
        return store.getRoot().leftTraverse().size();
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

    public Optional<V> value(final K k) {
        final Traversal<K,V> tr = store.getRoot().leafOnly(k);
        return tr.index() >= 0 ? Optional.of(tr.value(null)) : Optional.empty();
    }

    public V put(final K k, final V v) {
        final Traversal<K,V> traversal = store.getRoot().traverse(k);
        V ret = null;
        
        if(traversal.index() >= 0) {
            ret = traversal.value(null);
        }
        
        while(traversal.level() >= 0) {
            final Node<K,V> current = traversal.current().node();

            //handle leaf
            if(current.isLeaf()) {
                putLeaf(traversal, k, v);
                traversal.pop();
            }
            else if(tlWorking.get().hasOrphan()) {
                putBranch(traversal);
                traversal.pop();
            }
            else {
                return ret;
            }
        }

        //if there is a remaining orphan, we need a new root
        if(tlWorking.get().hasOrphan()) {
            final Node<K,V> orphan = tlWorking.get().adoptOrphan();
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
        final Traversal<K,V> traversal = store.getRoot().traverse(k);
        final Leaf<K,V> leaf = traversal.leaf();
        final int index = traversal.index();
        final V ret = (index >= 0) ? leaf.value(index) : null;
        if(index >= 0) {
            while(traversal.level() >= 0) {
                final Node<K,V> current = traversal.current().node();
                
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
                tlWorking.get().addDone(root);
                store.setRoot(root.asBranch().child(0));
            }

            tlWorking.get().done();
        }
        
        return ret;
    }
    
    private void putLeaf(final Traversal<K,V> traversal, final K k, final V v) {
        final Leaf<K,V> leaf = traversal.leaf();

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
        tlWorking.get().disown(newRightSibling);
        traversal.resetAncestorKeys();
    }

    private void putBranch(final Traversal<K,V> traversal) {
        final Branch<K,V> current = traversal.branch();
        final Node<K,V> orphan = tlWorking.get().adoptOrphan();

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
        tlWorking.get().disown(newRight);
        traversal.resetAncestorKeys();
    }

    private void removeLeaf(final Traversal<K,V> traversal, final int index) {
        final Leaf<K,V> current = traversal.leaf();

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
                final Traversal.Step<K,V> parentEntry = traversal.parent();
                final Branch<K,V> parent = parentEntry.node().asBranch();
                parent.remove(parentEntry.index());
                if(parentEntry.index() == 0) {
                    traversal.resetAncestorKeys();
                }
                
                tlWorking.get().addDone(current);
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
                final Traversal.Step<K,V> parentEntry = traversal.parent();
                final Branch<K,V> parent = parentEntry.node().asBranch();
                parent.remove(parentEntry.index());
                if(parentEntry.index() == 0) {
                    traversal.resetAncestorKeys();
                }
                
                tlWorking.get().addDone(current);
            }
        }
    }
    
    private void removeBranch(final Traversal<K,V> traversal) {
        //current is below limit, child was already removed previously
        final Branch<K,V> current = traversal.branch();

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
                final Traversal.Step<K,V> parentEntry = traversal.parent();
                final Branch<K,V> parent = parentEntry.node().asBranch();
                parent.remove(parentEntry.index());
                if(parentEntry.index() == 0) {
                    traversal.resetAncestorKeys();
                }
                
                tlWorking.get().addDone(current);
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

                final Traversal.Step<K,V> parentEntry = traversal.parent();
                final Branch<K,V> parent = parentEntry.node().asBranch();
                parent.remove(parentEntry.index());
                if(parentEntry.index() == 0) {
                    traversal.resetAncestorKeys();
                }

                tlWorking.get().addDone(current);
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
        return store.getRoot().traverse(k).isMatch();
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
        final Optional<V> opt = value(store.getKeyType().cast(o));
        return opt.isPresent() ? opt.get() : null;
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

    private Traversal<K,V> _ceiling(final K k) {
        final Traversal<K,V> traversal = store.getRoot().traverse(k);
        if(traversal.isMatch()) {
            return traversal;
        }

        traversal.positionInsert();
        return traversal.isMatch() ? traversal : traversal.empty();
    }
    
    public K ceilingKey(final K k) {
        return _ceiling(k).key(null);
    }

    public Map.Entry<K,V> ceilingEntry(final K k) {
        return _ceiling(k).entry(null);
    }

    public NavigableSet<K> descendingKeySet() {
        throw new UnsupportedOperationException();
    }

    public NavigableMap<K,V> descendingMap() {
        throw new UnsupportedOperationException();
    }

    public K firstKey() {
        if(isEmpty()) {
            throw new NoSuchElementException("tree is empty");
        }

        return store.getRoot().key(0);
    }

    public Map.Entry<K,V> firstEntry() {
        return isEmpty() ? null : store.getRoot().leftTraverse().next().entry(null);
    }

    private Traversal<K,V> _floor(final K k) {
        final Traversal<K,V> traversal = store.getRoot().traverse(k);
        if(traversal.isMatch()) {
            return traversal;
        }

        traversal.positionInsert();
        return traversal.hasPrevious() ? traversal.previous() : traversal.empty();
    }

    public K floorKey(final K k) {
        return isEmpty() ? null : _floor(k).key(null);
    }

    public Map.Entry<K,V> floorEntry(final K k) {
        return isEmpty() ? null : _floor(k).entry(null);
    }

    private Traversal<K,V> _higher(final K k) {
        final Traversal<K,V> traversal = store.getRoot().traverse(k);

        if(traversal.isMatch()) {
            return traversal.hasNext() ? traversal.next() : traversal.empty();
        }

        traversal.positionInsert();
        return traversal.isMatch() ? traversal : traversal.empty();
    }

    public K higherKey(final K k) {
        return isEmpty() ? null : _higher(k).key(null);
    }

    public Map.Entry<K,V> higherEntry(final K k) {
        return isEmpty() ? null : _higher(k).entry(null);
    }

    public SortedMap<K,V> headMap(final K toKey) {
        return boundMap(store.getRoot().leftTraverse(), store.getRoot().traverse(toKey)); 
    }

    public NavigableMap<K,V> headMap(final K toKey, final boolean inclusive) {
        throw new UnsupportedOperationException();
    }

    private Traversal<K,V> _lower(final K k) {
        final Traversal<K,V> traversal = store.getRoot().traverse(k);
        if(traversal.isMatch()) {
            return traversal.hasPrevious() ? traversal.previous() : traversal.empty();
        }

        traversal.positionInsert();
        return traversal.hasPrevious() ? traversal.previous() : traversal.empty();
    }
    
    public K lowerKey(final K k) {
        return isEmpty() ? null : _lower(k).key(null);
    }

    public Map.Entry<K,V> lowerEntry(final K k) {
        return isEmpty() ? null : _lower(k).entry(null);
    }

    public SortedMap<K,V> tailMap(final K fromKey) {
        return boundMap(store.getRoot().traverse(fromKey), store.getRoot().rightTraverse());
    }

    public NavigableMap<K,V> tailMap(final K fromKey, final boolean inclusive) {
        throw new UnsupportedOperationException();
    }

    public SortedMap<K,V> subMap(final K fromKey, final K toKey) {
        checkRange(fromKey, toKey);
        return boundMap(store.getRoot().traverse(fromKey), store.getRoot().traverse(toKey));
    }

    public NavigableMap<K,V> subMap(final K fromKey, final boolean fromInclusive,
                                    final K toKey, final boolean toInclusive) {
        throw new UnsupportedOperationException();
    }
    
    private Traversal<K,V> _last() {
        return store.getRoot().rightTraverse().positionInsert().previous();
    }
    
    public K lastKey() {
        if(isEmpty()) {
            throw new NoSuchElementException("tree is empty");
        }
        
        return _last().key(null);
    }

    public Map.Entry<K,V> lastEntry() {
        return isEmpty() ? null : _last().entry(null);
    }

    public NavigableSet<K> navigableKeySet() {
        throw new UnsupportedOperationException();
    }

    public Map.Entry<K,V> pollFirstEntry() {
        if(isEmpty()) {
            return null;
        }

        final Map.Entry<K,V> e = store.getRoot().leftTraverse().next().entry(null);
        delete(e.getKey());
        return e;
    }

    public Map.Entry<K,V> pollLastEntry() {
        if(isEmpty()) {
            return null;
        }

        final Map.Entry<K,V> e = store.getRoot().rightTraverse().positionInsert().previous().entry(null);
        delete(e.getKey());
        return e;
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

    //views
    private class KeysIterator implements Iterator<K> {
        private final Traversal<K,V> traversal;
        
        KeysIterator() {
            this.traversal = store.getRoot().leftTraverse();
        }

        public boolean hasNext() { return traversal.hasNext(); }
        public K next() { return traversal.next().key(null); }
    }

    private class KeysSet extends AbstractSet<K> {
        public int size() { return BplusTree.this.size(); }
        public Iterator<K> iterator() { return new KeysIterator(); }

        @Override
        public boolean contains(final Object o) { return containsKey(o); }

        @Override
        public boolean isEmpty() { return BplusTree.this.isEmpty(); }
    }

    private class ValuesIterator implements Iterator<V> {
        private final Traversal<K,V> traversal;
        
        ValuesIterator() {
            this.traversal = store.getRoot().leftTraverse();
        }

        public boolean hasNext() { return traversal.hasNext(); }
        public V next() { return traversal.next().value(null); }
    }

    private class ValuesCollection extends AbstractCollection<V> {
        public int size() { return BplusTree.this.size(); }
        public Iterator<V> iterator() { return new ValuesIterator(); }
    }

    private class EntriesIterator implements Iterator<Map.Entry<K,V>> {
        private final Traversal<K,V> traversal;

        EntriesIterator() {
            this.traversal = store.getRoot().leftTraverse();
        }

        public boolean hasNext() { return traversal.hasNext(); }
        public Map.Entry<K,V> next() { return traversal.next().entry(null); }
    }

    private class EntriesSet extends AbstractSet<Map.Entry<K,V>> {
        public int size() { return BplusTree.this.size(); }
        public Iterator<Map.Entry<K,V>> iterator() { return new EntriesIterator(); }

        @Override
        public boolean contains(final Object o) {
            if(!(o instanceof Map.Entry)) {
                return false;
            }

            final Map.Entry entry = (Map.Entry) o;
            final Optional<V> opt = value(store.getKeyType().cast(entry.getKey()));
            return (opt.isPresent() && opt.get().equals(entry.getValue()));
        }
        
        @Override
        public boolean isEmpty() {
            return BplusTree.this.isEmpty();
        }
    }

    private class BoundKeysIterator implements Iterator<K> {
        private final Traversal<K,V> upper;
        private final Traversal<K,V> tr;
        
        BoundKeysIterator(final BoundMap map) {
            this.upper = map.upper.immutable();
            this.tr = map.lower.mutable();
        }

        public boolean hasNext() { return tr.hasNext() && tr.compareTo(upper) < 0; }
        public K next() { return tr.next().key(null); }
    }

    private class BoundKeysSet extends AbstractSet<K> {
        private final BoundMap map;
        
        BoundKeysSet(final BoundMap map) {
            this.map = map;
        }
        
        public int size() { return iteratorCount(iterator()); }
        public Iterator<K> iterator() { return new BoundKeysIterator(map); }
        
        @Override
        public boolean contains(final Object o) {
            return map.containsKey(o);
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }
    }
    
    private class BoundValuesIterator implements Iterator<V> {
        private final Traversal<K,V> upper;
        private final Traversal<K,V> tr;
        
        BoundValuesIterator(final BoundMap map) {
            upper = map.upper.immutable();
            tr = map.lower.mutable();
        }
        
        public boolean hasNext() { return tr.hasNext() && tr.compareTo(upper) < 0; }
        public V next() { return tr.next().value(null); }
    }

    private class BoundValuesCollection extends AbstractCollection<V> {
        private final BoundMap map;
        
        BoundValuesCollection(final BoundMap map) {
            this.map = map;
        }

        public int size() { return iteratorCount(iterator()); }
        public Iterator<V> iterator() { return new BoundValuesIterator(map); }
    }

    private class BoundEntriesIterator implements Iterator<Map.Entry<K,V>> {
        private final Traversal<K,V> upper;
        private final Traversal<K,V> tr;

        BoundEntriesIterator(final BoundMap map) {
            upper = map.upper.immutable();
            tr = map.lower.mutable();
        }

        public boolean hasNext() { return tr.hasNext() && tr.compareTo(upper) < 0; }
        public Map.Entry<K,V> next() { return tr.next().entry(null); }
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

        @Override
        public boolean contains(final Object o) {
            if(!(o instanceof Map.Entry)) {
                return false;
            }
            
            final Map.Entry entry = (Map.Entry) o;
            final Optional<V> opt = map.value(store.getKeyType().cast(entry.getKey()));
            return (opt.isPresent() && opt.get().equals(entry.getValue()));
        }
    }

    private BoundMap boundMap(final Traversal<K,V> lower, final Traversal<K,V> upper) {
        return boundMap(lower, true, upper, false);
    }
    
    private BoundMap boundMap(final Traversal<K,V> lower, final boolean lowerInclusive,
                              final Traversal<K,V> upper, final boolean upperInclusive) {
        return new BoundMap(fixLowerBounds(lower, lowerInclusive),
                            fixUpperBounds(upper, upperInclusive));
    }

    private Traversal<K,V> fixLowerBounds(final Traversal<K,V> lower, final boolean inclusive) {
        if(!lower.isMatch()) {
            lower.positionInsert();
            if(inclusive) {
                lower.current().previous();
            }

            return lower;
        }
        else if(inclusive) {
            return lower.previous();
        }
        else {
            return lower;
        }
    }

    private Traversal<K,V> fixUpperBounds(final Traversal<K,V> upper, final boolean inclusive) {
        if(upper.isMatch()) {
            return inclusive ? upper : upper.previous();
        }

        upper.positionInsert();
        return upper.previous();
    }

    private class BoundMap implements Map<K,V>, SortedMap<K,V> {
        private final Traversal<K,V> lower;
        private final Traversal<K,V> upper;

        BoundMap(final Traversal<K,V> lower, final Traversal<K,V> upper) {
            this.lower = lower.immutable();
            this.upper = upper.immutable();
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
            final Traversal<K,V> tr = store.getRoot().traverse(k);
            if(lower.compareTo(tr) < 0 && tr.compareTo(upper) <= 0) {
                return func.apply(tr);
            }
            else {
                throw new IllegalArgumentException("key not within bounds of map");
            }
        }

        private <R> R checkBounds(final K k, final R ret, final Function<Traversal<K,V>, R> func) {
            final Traversal<K,V> tr = store.getRoot().traverse(k);
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
            return checkBounds(store.getKeyType().cast(o), false, (tr) -> tr.isMatch());
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

        private Optional<V> _value(final Traversal<K,V> tr) {
            return tr.isMatch() ? Optional.of(tr.value(null)) : Optional.empty();
        }
        
        public Optional<V> value(final K k) {
            return checkBounds(k, Optional.empty(), this::_value);
        }
        
        public V get(final Object o) {
            final Optional<V> opt = value(store.getKeyType().cast(o));
            return opt.isPresent() ? opt.get() : null;
        }

        @Override
        public int hashCode() {
            return entrySet().hashCode();
        }

        private <T> T checkEmpty(final Supplier<T> ifEmpty, final Supplier<T> notEmpty) {
            return isEmpty() ? ifEmpty.get() : notEmpty.get();
        }

        private Traversal<K,V> _first() {
            return lower.mutable().next();
        }
        
        public K firstKey() {
            if(isEmpty()) {
                throw new NoSuchElementException("tree is empty");
            }
            
            return _first().key(null);
        }
        
        public Map.Entry<K,V> firstEntry() {
            return isEmpty() ? null : _first().entry(null);
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

        private Map.Entry<K,V> _lastEntry() {
            return upper.entry(null);
        }
        
        public K lastKey() {
            if(isEmpty()) {
                throw new NoSuchElementException("tree is empty");
            }
            
            return upper.key(null);
        }

        public Map.Entry<K,V> lastEntry() {
            return isEmpty() ? null : upper.entry(null);
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
            return checkBounds(k, null, (tr) -> BplusTree.this.remove(o));
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
