package bplus;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

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
            if(get(i).hasNext()) {
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
        
        public boolean isEmpty() { return leafStep != null; }
        
        public NodeTraversal<K,V> next() {
            throw new UnsupportedOperationException();
        }

        public NodeTraversal<K,V> previous() {
            throw new UnsupportedOperationException();
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
            steps.add(new MutableStep<>(node, index));
        }

        public Step<K,V> pop() {
            return steps.remove(level());
        }
    }
}
