package bplus;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import static bplus.Node.insertIndex;

abstract class NodeTraversal<K extends Comparable<K>,V> implements Comparable<NodeTraversal<K,V>> {

    public static <K extends Comparable<K>,V> NodeTraversal<K,V> makeMutable() {
        return new MutableTraversal<>();
    }

    public static <K extends Comparable<K>,V> NodeTraversal<K,V> makeEmpty() {
        return new EmptyTraversal<>();
    }

    public static <K extends Comparable<K>,V> NodeTraversal<K,V> makeLeafOnly() {
        return new LeafOnly<>();
    }
    
    public abstract NodeTraversal<K,V> next();
    public abstract NodeTraversal<K,V> previous();
    public abstract Step<K,V> get(int level);
    public abstract int size();
    public abstract void add(final Node<K,V> node, final int index);
    public abstract Step<K,V> pop();
    public abstract boolean isEmpty();
    
    public int compareTo(final NodeTraversal<K,V> traversal) {
        if(size() != traversal.size()) {
            throw new IllegalStateException("traversals are not of same height");
        }

        if(get(0).node() != traversal.get(0).node()) {
            throw new IllegalStateException("traversals don't have same root");
        }

        for(int i = 0; i < size(); ++i) {
            final Step<K,V> lhs = get(i);
            final Step<K,V> rhs = traversal.get(i);
            final int cmp = Integer.compare(lhs.index(), rhs.index());
            if(cmp != 0) {
                return cmp;
            }
        }

        return 0;
    }

    public int level() { return size() - 1; }
    public Step<K,V> current() { return get(level()); }
    public Step<K,V> parent() { return size() >= 2 ? get(level() - 1) : null; }
    public Step<K,V> grandparent() { return size() >= 3 ? get(level() - 1) : null; }
    public Leaf<K,V> leaf() { return current().node().asLeaf(); }
    public Branch<K,V> branch() { return current().node().asBranch(); }
    public int index() { return current().index(); }
    public boolean isMatch() { return index() >= 0 && index() < leaf().size(); }

    public NodeTraversal<K,V> positionInsert() {
        final Step<K,V> c = current();
        c.index(insertIndex(c.index()));
        return this;
    }
    
    public boolean hasNext() {
        for(int i = 0; i < size(); ++i) {
            if(get(i).hasNext()) {
                return true;
            }
        }
        
        return false;
    }

    public boolean hasPrevious() {
        for(int i = 0; i < size(); ++i) {
            if(get(i).hasPrevious()) {
                return true;
            }
        }
        
        return false;
    }

    public K key(final K onEmpty) {
        return isEmpty() ? onEmpty : leaf().key(index());
    }

    public Map.Entry<K,V> entry(Map.Entry<K,V> onEmpty) {
        return isEmpty() ? onEmpty : leaf().entry(index());
    }

    public V value(final V onEmpty) {
        return isEmpty() ? onEmpty : leaf().value(index());
    }

    public NodeTraversal<K,V> empty() {
        return new EmptyTraversal<>();
    }

    public NodeTraversal<K,V> mutable() {
        return isEmpty() ? new MutableTraversal<K,V>() : new MutableTraversal<K,V>(this);
    }

    public NodeTraversal<K,V> immutable() {
        if((this instanceof EmptyTraversal) || (this instanceof ImmutableTraversal)) {
            return this;
        }
        else {
            return new ImmutableTraversal<>(this);
        }
    }
    
    private void resetKeys(final int startLevel, final int startIndex) {
        int i = startLevel;
        int prevIndex = startIndex;
        
        while(i >= 0 && prevIndex == 0) {
            final Step<K,V> step = get(i--);
            step.node().asBranch().resetKey(step.index());
            prevIndex = step.index();
        }
    }

    public void resetAncestorKeys() {
        final Step<K,V> parent = parent();
        if(parent != null) {
            parent.node().asBranch().resetKey(parent.index());
            resetKeys(level() - 2, parent.index());
        }
    }
    
    static abstract class Step<K extends Comparable<K>,V> {
        public abstract Step<K,V> node(Node<K,V> node);
        public abstract Node<K,V> node();
        public abstract Step<K,V> index(int index);
        public abstract int index();

        public boolean hasNext() {
            return index() + 1 < node().size();
        }

        public void next() {
            index(index() + 1);
        }

        public boolean hasPrevious() {
            return index() > 0;
        }

        public void previous() {
            index(index() - 1);
        }

        public void clear() {
            node(null).index(-1);
        }

        public Step<K,V> mutable() {
            return new MutableStep<>(node(), index());
        }

        public Step<K,V> immutable() {
            if(this instanceof ImmutableStep) {
                return this;
            }
            else {
                return new ImmutableStep<>(node(), index());
            }
        }
    }

    static class MutableStep<K extends Comparable<K>,V> extends Step<K,V> {
        private Node<K,V> node;
        private int index;
        
        public Step<K,V> node(final Node<K,V> node) { this.node = node; return this; }
        public Node<K,V> node() { return node; }
        public Step<K,V> index(final int index) { this.index = index; return this; }
        public int index() { return index; }

        public MutableStep() {
            this.node = null;
            this.index = -1;
        }

        public MutableStep(final Node<K,V> node, final int index) {
            this.node = node;
            this.index = index;
        }
    }

    static class ImmutableStep<K extends Comparable<K>,V> extends Step<K,V> {
        private final Node<K,V> node;
        private final int index;
        
        public Step<K,V> node(final Node<K,V> node) { throw new UnsupportedOperationException(); }
        public Node<K,V> node() { return node; }
        public Step<K,V> index(final int index) { throw new UnsupportedOperationException(); }
        public int index() { return index; }

        public ImmutableStep(final Node<K,V> node, final int index) {
            this.node = node;
            this.index = index;
        }
    }

    static class EmptyTraversal<K extends Comparable<K>,V> extends NodeTraversal<K,V> {
        public boolean isEmpty() { return true; }
        
        public NodeTraversal<K,V> next() {
            throw new UnsupportedOperationException();
        }

        public NodeTraversal<K,V> previous() {
            throw new UnsupportedOperationException();
        }

        public Step<K,V> get(int level) {
            throw new UnsupportedOperationException();
        }
        
        public int size() {
            return 0;
        }

        public void add(final Node<K,V> node, final int index) {
            throw new UnsupportedOperationException();
        }

        public Step<K,V> pop() {
            throw new UnsupportedOperationException();
        }
    }

    static class LeafOnly<K extends Comparable<K>,V> extends NodeTraversal<K,V> {
        private Step<K,V> leafStep = null;
        
        public boolean isEmpty() { return leafStep == null; }
        
        public NodeTraversal<K,V> next() {
            leafStep.next();
            return this;
        }

        public NodeTraversal<K,V> previous() {
            leafStep.previous();
            return this;
        }

        public Step<K,V> get(int level) {
            return level == 0 ? leafStep : null;
        }
        
        public int size() {
            return leafStep == null ? 0 : 1;
        }

        public void add(final Node<K,V> node, final int index) {
            if(node.isLeaf()) {
                leafStep = new MutableStep<>(node, index);
            }
        }

        public Step<K,V> pop() {
            final Step<K,V> prev = leafStep;
            leafStep = null;
            return prev;
        }
    }

    static class ImmutableTraversal<K extends Comparable<K>,V> extends NodeTraversal<K,V> {
        private final List<Step<K,V>> steps;

        public ImmutableTraversal(final NodeTraversal<K,V> toCopy) {
            final List<Step<K,V>> tmp = new ArrayList<>(toCopy.size());
            for(int i = 0; i < toCopy.size(); ++i) {
                tmp.add(toCopy.get(i).immutable());
            }

            this.steps = Collections.unmodifiableList(tmp);
        }
        
        public boolean isEmpty() { return steps.isEmpty(); }
        
        public NodeTraversal<K,V> next() {
            throw new UnsupportedOperationException();
        }

        public NodeTraversal<K,V> previous() {
            throw new UnsupportedOperationException();
        }

        public Step<K,V> get(int level) {
            return steps.get(level);
        }
        
        public int size() {
            return steps.size();
        }

        public void add(final Node<K,V> node, final int index) {
            throw new UnsupportedOperationException();
        }

        public Step<K,V> pop() {
            throw new UnsupportedOperationException();
        }
    }

    static class MutableTraversal<K extends Comparable<K>,V> extends NodeTraversal<K,V> {
        private final List<Step<K,V>> steps = new ArrayList<>(4);

        public MutableTraversal() {}

        public MutableTraversal(final NodeTraversal<K,V> toCopy) {
            for(int i = 0; i < toCopy.size(); ++i) {
                steps.add(toCopy.get(i).mutable());
            }
        }
        
        public boolean isEmpty() { return steps.isEmpty(); }
        
        public NodeTraversal<K,V> next() {
            if(!current().hasNext()) {
                nextNode();
            }

            current().next();
            return this;
        }

        private void nextNode() {
            for(int i = size() - 2; i >= 0; --i) {
                pop();
                final Step<K,V> toTest = get(i);
                if(toTest.hasNext()) {
                    toTest.next();
                    break;
                }
            }

            if(isEmpty()) {
                return;
            }

            branch().child(index()).leftTraverse(this);
        }

        public NodeTraversal<K,V> previous() {
            if(!current().hasPrevious()) {
                previousNode();
            }
            
            current().previous();
            return this;
        }

        private void previousNode() {
            for(int i = size() - 2; i >= 0; --i) {
                pop();
                final Step<K,V> toTest = get(i);
                if(toTest.hasPrevious()) {
                    toTest.previous();
                    break;
                }
            }
            
            if(isEmpty()) {
                return;
            }

            branch().child(index()).rightTraverse(this);
        }

        public Step<K,V> get(int level) {
            return steps.get(level);
        }
        
        public int size() {
            return steps.size();
        }

        public void add(final Node<K,V> node, final int index) {
            steps.add(new MutableStep<>(node, index));
        }

        public Step<K,V> pop() {
            return steps.remove(level());
        }
    }

    public interface SiblingRelation<T extends Comparable<T>,U> {
        Branch<T,U> getParent();
        int getIndex();
        Node<T,U> getSibling();
        void resetAncestorKeys();
    }

    private class SameFamily implements SiblingRelation<K,V> {
        private final Step<K,V> parentEntry;
        private final Node<K,V> sibling;
        
        private SameFamily(final Step<K,V> parentEntry, final Node<K,V> sibling) {
            this.parentEntry = parentEntry;
            this.sibling = sibling;
        }
        
        public Branch<K,V> getParent() { return parentEntry.node().asBranch(); }
        public int getIndex() { return parentEntry.index(); }
        public Node<K,V> getSibling() { return sibling; }

        public void resetAncestorKeys() {
            parentEntry.node().asBranch().resetKey(parentEntry.index());
            resetKeys(level() - 2, parentEntry.index());
        }
    }

    private class AdoptedFamily implements SiblingRelation<K,V> {
        private final Step<K,V> grandparentEntry;
        private final Step<K,V> uncleEntry;
        private final Node<K,V> sibling;

        private AdoptedFamily(final Step<K,V> grandparentEntry, final Step<K,V> uncleEntry,
                              final Node<K,V> sibling) {
            this.grandparentEntry = grandparentEntry;
            this.uncleEntry = uncleEntry;
            this.sibling = sibling;
        }

        public Branch<K,V> getParent() { return grandparentEntry.node().asBranch(); }
        public int getIndex() { return grandparentEntry.index(); }
        public Node<K,V> getSibling() { return sibling; }

        public void resetAncestorKeys() {
            uncleEntry.node().asBranch().resetKey(uncleEntry.index());
            grandparentEntry.node().asBranch().resetKey(grandparentEntry.index());
            resetKeys(level() - 3, grandparentEntry.index());
        }
    }

    public SiblingRelation<K,V> getLeftSibling() {
        final Step<K,V> parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        if(parentEntry.index() > 0) {
            final Branch<K,V> parent = parentEntry.node().asBranch();
            final int index = parentEntry.index() - 1;
            return new SameFamily(new ImmutableStep<>(parent, index), parent.child(index));
        }
        else {
            final Step<K,V> tmpEntry = grandparent();
            if(tmpEntry == null || tmpEntry.index() == 0) {
                return null;
            }

            final Branch<K,V> grandparent = tmpEntry.node().asBranch();
            final int grandparentNavIndex = tmpEntry.index() - 1;
            final Step<K,V> grandparentEntry = new ImmutableStep<>(grandparent, grandparentNavIndex);
            final Branch<K,V> uncle = grandparent.child(grandparentNavIndex).asBranch();
            final int uncleNavIndex = uncle.lastIndex();
            final Step<K,V> uncleEntry = new ImmutableStep<>(uncle, uncleNavIndex);
            final Node<K,V> cousin = uncle.child(uncleNavIndex);
            return new AdoptedFamily(grandparentEntry, uncleEntry, cousin);
        }
    }

    public SiblingRelation<K,V> getRightSibling() {
        final Step<K,V> parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        if(parentEntry.index() + 1 < parentEntry.node().size()) {
            final Branch<K,V> parent = parentEntry.node().asBranch();
            final int index = parentEntry.index() + 1;
            return new SameFamily(new ImmutableStep<>(parent, index), parent.child(index));
        }
        else {
            final Step<K,V> tmpEntry = grandparent();
            if(tmpEntry == null || (tmpEntry.index() + 1 == tmpEntry.node().size())) {
                return null;
            }
            
            final Branch<K,V> grandparent = tmpEntry.node().asBranch();
            final int grandparentNavIndex = tmpEntry.index() + 1;
            final Step<K,V> grandparentEntry = new ImmutableStep<>(grandparent, grandparentNavIndex);
            final Branch<K,V> uncle = grandparent.child(grandparentNavIndex).asBranch();
            final int uncleNavIndex = 0;
            final Step<K,V> uncleEntry = new ImmutableStep<>(uncle, uncleNavIndex);
            final Node<K,V> cousin = uncle.child(uncleNavIndex);
            return new AdoptedFamily(grandparentEntry, uncleEntry, cousin);
        }
    }
}
