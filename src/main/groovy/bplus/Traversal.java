package bplus;

import java.util.List;
import java.util.ArrayList;

class Traversal<K extends Comparable<K>,V> {

    public static class SiblingRelation<T extends Comparable<T>,U> {
        private final Branch<T,U> parent;
        private final int index;
        private final Node<T,U> sibling;

        private SiblingRelation(final Branch<T,U> parent, final int index, final Node<T,U> sibling) {
            this.parent = parent;
            this.index = index;
            this.sibling = sibling;
        }

        public Branch<T,U> getParent() { return parent; }
        public int getIndex() { return index; }
        public Node<T,U> getSibling() { return sibling; }
    }
    
    public static class Entry<T extends Comparable<T>,U> {
        private Node<T,U> node = null;
        private int index = -1;
        
        public Entry<T,U> setNode(final Node<T,U> val) { node = val; return this; }
        public Node<T,U> getNode() { return node; }

        public Entry<T,U> setIndex(final int val) { index = val; return this; }
        public int getIndex() { return index; }

        public void clear() {
            node = null;
            index = -1;
        }
    }
    
    private static final int DEFAULT = 5;
    private final List<Node<K,V>> doneNodes;
    private final List<Entry<K,V>> all;
    private int count;
    private Node<K,V> orphan;
    
    Traversal() {
        this.all = new ArrayList<>(DEFAULT);
        this.doneNodes = new ArrayList<>(2);
        this.count = 0;
        this.orphan = null;

        for(int i = 0; i < DEFAULT; ++i) {
            all.add(new Entry<>());
        }
    }

    public Traversal<K,V> clear() {
        for(int i = 0; i < count; ++i) {
            all.get(i).clear();
        }

        doneNodes.clear();
        count = 0;
        orphan = null;
        return this;
    }

    public void addDone(final Node<K,V> node) {
        doneNodes.add(node);
    }

    public void done() {
        for(Node<K,V> node : doneNodes) {
            node.done();
        }
    }

    public Traversal<K,V> execute(final Node<K,V> root, final K k) {
        clear();
        Node<K,V> current = root;
        while(!current.isLeaf()) {
            final Branch<K,V> branch = current.asBranch();
            final int navIndex = branch.navigateIndex(k);
            next().setNode(current).setIndex(navIndex).getNode();
            current = branch.child(navIndex);
        }

        next().setNode(current).setIndex(0);
        return this;
    }

    public int level() {
        return count - 1;
    }

    public void pop() {
        getCurrent().clear();
        --count;
    }

    public boolean hasOrphan() {
        return orphan != null;
    }

    public Node<K,V> adoptOrphan() {
        final Node<K,V> ret = orphan;
        orphan = null;
        return ret;
    }

    public void disown(final Node<K,V> val) {
        this.orphan = val;
    }

    public SiblingRelation<K,V> getLeftSibling() {
        final Entry<K,V> parentEntry = getParent();
        if(parentEntry == null) {
            return null;
        }

        if(parentEntry.index > 0) {
            final Branch<K,V> parent = parentEntry.node.asBranch();
            final int index = parentEntry.index;
            return new SiblingRelation<>(parent, index, parent.child(index - 1));
        }
        else {
            final Entry<K,V> grandEntry = getGrandparent();
            if(grandEntry == null || grandEntry.index == 0) {
                return null;
            }

            final Branch<K,V> grandparent = grandEntry.node.asBranch();
            final Branch<K,V> uncle = grandparent.child(grandEntry.index - 1).asBranch();
            final int index = grandEntry.index;
            final Node<K,V> cousin = uncle.child(uncle.size() - 1);
            return new SiblingRelation<>(grandparent, index, cousin);
        }
    }

    public SiblingRelation<K,V> getRightSibling() {
        final Entry<K,V> parentEntry = getParent();
        if(parentEntry == null) {
            return null;
        }

        if(parentEntry.index + 1 < parentEntry.node.size()) {
            final Branch<K,V> parent = parentEntry.node.asBranch();
            final int index = parentEntry.index;
            return new SiblingRelation<>(parent, index, parent.child(index + 1));
        }
        else {
            final Entry<K,V> grandEntry = getGrandparent();
            if(grandEntry == null || (grandEntry.index + 1 == grandEntry.node.size())) {
                return null;
            }
            
            final Branch<K,V> grandparent = grandEntry.node.asBranch();
            final int index = grandEntry.index;
            final Branch<K,V> uncle = grandparent.child(index + 1).asBranch();
            final Node<K,V> cousin = uncle.child(0);
            return new SiblingRelation<>(grandparent, index, cousin);
        }
    }

    public Entry<K,V> getCurrent() {
        return all.get(level());
    }

    public Entry<K,V> getParent() {
        return (level() - 1 >= 0) ? all.get(level() - 1) : null;
    }

    public Entry<K,V> getGrandparent() {
        return (level() - 2 >= 0) ? all.get(level() - 2) : null;
    }

    private Entry<K,V> next() {
        if(count < all.size()) {
            ++count;
            return getCurrent();
        }
        else {
            ++count;
            Entry<K,V> entry = new Entry<>();
            all.add(entry);
            return entry;
        }
    }
}
