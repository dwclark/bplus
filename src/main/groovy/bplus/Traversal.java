package bplus;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

abstract class Traversal<K extends Comparable<K>,V> implements Comparable<Traversal<K,V>> {

    public int compareTo(final Traversal<K,V> traversal) {
        if(count() != traversal.count()) {
            throw new IllegalStateException("traversals are not of same height");
        }

        if(get(0).getNode() != traversal.get(0).getNode()) {
            throw new IllegalStateException("traversals don't have same root");
        }

        for(int i = 0; i < count(); ++i) {
            final Entry lhs = get(i);
            final Entry rhs = traversal.get(i);
            int cmp = Integer.compare(lhs.getIndex(), rhs.getIndex());
            if(cmp != 0) {
                return cmp;
            }
        }

        return 0;
    }

    interface Traverser<T extends Comparable<T>, U> {
        Leaf<T,U> getLeaf();
        int getIndex();
        boolean hasNext();
        void next();
        boolean hasPrevious();
        void previous();
    }

    private int initialIndex(final Node<K,V> node) {
        return node.isBranch() ? 0 : -1;
    }

    private int lastIndex(final Node<K,V> node) {
        return node.isBranch() ? node.size() - 1 : node.size();
    }

    public Traverser<K,V> traverser() {
        return new _Traverser();
    }
    
    public class _Traverser implements Traverser<K,V> {

        public Leaf<K,V> getLeaf() {
            return current().getNode().asLeaf();
        }

        public int getIndex() {
            return current().getIndex();
        }
        
        public boolean hasNext() {
            if(isEmpty()) {
                return false;
            }
            
            for(int i = 0; i < count(); ++i) {
                final Entry e = get(i);
                if(e.hasNext()) {
                    return true;
                }
            }
            
            return false;
        }

        public void next() {
            if(current().hasNext()) {
                current().next();
                return;
            }
            else {
                positionNext();
                current().next();
            }
        }

        public boolean hasPrevious() {
            throw new UnsupportedOperationException();
        }

        public void previous() {
            if(current().hasPrevious()) {
                current().previous();
                return;
            }
            else {
                positionPrevious();
                current().previous();
            }
        }

        private void positionNext() {
            for(int i = count() - 2; i >= 0; --i) {
                pop();
                final Entry toTest = get(i);
                if(toTest.hasNext()) {
                    toTest.next();
                    break;
                }
            }

            if(isEmpty()) {
                return;
            }

            Entry tentry = current();
            while(!tentry.getNode().isLeaf()) {
                final Branch<K,V> branch = tentry.getNode().asBranch();
                final Node<K,V> node = branch.child(tentry.getIndex());
                tentry = nextEntry().setNode(node).setIndex(initialIndex(node));
            }
        }

        private void positionPrevious() {
            for(int i = count() - 2; i >= 0; --i) {
                pop();
                final Entry toTest = get(i);
                if(toTest.hasPrevious()) {
                    toTest.previous();
                    break;
                }
            }
            
            if(isEmpty()) {
                return;
            }

            Entry tentry = current();
            while(!tentry.getNode().isLeaf()) {
                final Branch<K,V> branch = tentry.getNode().asBranch();
                final Node<K,V> node = branch.child(tentry.getIndex());
                tentry = nextEntry().setNode(node).setIndex(lastIndex(node));
            }
        }
    }
    
    public interface SiblingRelation<T extends Comparable<T>,U> {
        Branch<T,U> getParent();
        int getIndex();
        Node<T,U> getSibling();
        void resetAncestorKeys();
    }
    
    private class SameFamily implements SiblingRelation<K,V> {
        private final Entry parentEntry;
        private final Node<K,V> sibling;
        
        private SameFamily(final Entry parentEntry, final Node<K,V> sibling) {
            this.parentEntry = parentEntry;
            this.sibling = sibling;
        }
        
        public Branch<K,V> getParent() { return parentEntry.getNode().asBranch(); }
        public int getIndex() { return parentEntry.getIndex(); }
        public Node<K,V> getSibling() { return sibling; }

        public void resetAncestorKeys() {
            resetEntry(parentEntry);
            resetEntries(level() - 2, parentEntry.getIndex());
        }
    }
    
    private class AdoptedFamily implements SiblingRelation<K,V> {
        private final Entry grandparentEntry;
        private final Entry uncleEntry;
        private final Node<K,V> sibling;

        private AdoptedFamily(final Entry grandparentEntry, final Entry uncleEntry,
                              final Node<K,V> sibling) {
            this.grandparentEntry = grandparentEntry;
            this.uncleEntry = uncleEntry;
            this.sibling = sibling;
        }

        public Branch<K,V> getParent() { return grandparentEntry.getNode().asBranch(); }
        public int getIndex() { return grandparentEntry.getIndex(); }
        public Node<K,V> getSibling() { return sibling; }

        public void resetAncestorKeys() {
            resetEntry(uncleEntry);
            resetEntry(grandparentEntry);
            resetEntries(level() - 3, grandparentEntry.getIndex());
        }
    }

    public abstract class Entry {
        abstract public Entry setNode(final Node<K,V> val);
        abstract public Node<K,V> getNode();
        abstract public Entry setIndex(final int index);
        abstract public int getIndex();

        public boolean hasNext() {
            return getIndex() + 1 < getNode().size();
        }

        public void next() {
            setIndex(getIndex() + 1);
        }

        public boolean hasPrevious() {
            return getIndex() >- 0;
        }

        public void previous() {
            setIndex(getIndex() - 1);
        }

        public void clear() {
            setNode(null);
            setIndex(-1);
        }

        public Entry mutable() {
            return new MutableEntry(getNode(), getIndex());
        }

        public Entry immutable() {
            if(this instanceof Traversal.ImmutableEntry) {
                return this;
            }
            else {
                return new ImmutableEntry(getNode(), getIndex());
            }
        }
    }
    
    public class MutableEntry extends Entry {
        private Node<K,V> node;
        private int index;
        
        public MutableEntry() {
            this.node = null;
            this.index = -1;
        }
        
        public MutableEntry(final Node<K,V> node, final int index){
            this.node = node;
            this.index = index;
        }
        
        public MutableEntry setNode(final Node<K,V> val) { node = val; return this; }
        public Node<K,V> getNode() { return node; }

        public MutableEntry setIndex(final int val) { index = val; return this; }
        public int getIndex() { return index; }
    }
    
    public class ImmutableEntry extends Entry {
        private final Node<K,V> node;
        private final int index;
        
        public ImmutableEntry(final Node<K,V> node, final int index){
            this.node = node;
            this.index = index;
        }
        
        public MutableEntry setNode(final Node<K,V> val) { throw new UnsupportedOperationException(); }
        public Node<K,V> getNode() { return node; }

        public MutableEntry setIndex(final int val) { throw new UnsupportedOperationException(); }
        public int getIndex() { return index; }
    }
    
    public Traversal<K,V> execute(final Node<K,V> root, final K k) {
        clear();
        Node<K,V> current = root;
        while(!current.isLeaf()) {
            final Branch<K,V> branch = current.asBranch();
            final int navIndex = branch.navigateIndex(k);
            nextEntry().setNode(current).setIndex(navIndex);
            current = branch.child(navIndex);
        }

        final Leaf<K,V> leaf = current.asLeaf();
        nextEntry().setNode(current).setIndex(leaf.search(k));
        return this;
    }

    public Traversal<K,V> leftTraversal(final Node<K,V> root) {
        clear();
        nextEntry().setNode(root).setIndex(initialIndex(root));
        Node<K,V> node = root;
        
        while(!node.isLeaf()) {
            node = node.asBranch().child(0);
            nextEntry().setNode(node).setIndex(initialIndex(node));
        }

        return this;
    }
    
    public Traversal<K,V> rightTraversal(final Node<K,V> root) {
        clear();
        nextEntry().setNode(root).setIndex(lastIndex(root));
        Node<K,V> node = root;
        
        while(!node.isLeaf()) {
            node = node.asBranch().child(lastIndex(node));
            nextEntry().setNode(node).setIndex(lastIndex(node));
        }

        return this;
    }

    public int level() {
        return count() - 1;
    }

    public SiblingRelation<K,V> getLeftSibling() {
        final Entry parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        if(parentEntry.getIndex() > 0) {
            final Branch<K,V> parent = parentEntry.getNode().asBranch();
            final int index = parentEntry.getIndex() - 1;
            return new SameFamily(new ImmutableEntry(parent, index), parent.child(index));
        }
        else {
            final Entry tmpEntry = grandparent();
            if(tmpEntry == null || tmpEntry.getIndex() == 0) {
                return null;
            }

            final Branch<K,V> grandparent = tmpEntry.getNode().asBranch();
            final int grandparentNavIndex = tmpEntry.getIndex() - 1;
            final Entry grandparentEntry = new ImmutableEntry(grandparent, grandparentNavIndex);
            final Branch<K,V> uncle = grandparent.child(grandparentNavIndex).asBranch();
            final int uncleNavIndex = uncle.lastIndex();
            final Entry uncleEntry = new ImmutableEntry(uncle, uncleNavIndex);
            final Node<K,V> cousin = uncle.child(uncleNavIndex);
            return new AdoptedFamily(grandparentEntry, uncleEntry, cousin);
        }
    }

    public SiblingRelation<K,V> getRightSibling() {
        final Entry parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        if(parentEntry.getIndex() + 1 < parentEntry.getNode().size()) {
            final Branch<K,V> parent = parentEntry.getNode().asBranch();
            final int index = parentEntry.getIndex() + 1;
            return new SameFamily(new ImmutableEntry(parent, index), parent.child(index));
        }
        else {
            final Entry tmpEntry = grandparent();
            if(tmpEntry == null || (tmpEntry.getIndex() + 1 == tmpEntry.getNode().size())) {
                return null;
            }
            
            final Branch<K,V> grandparent = tmpEntry.getNode().asBranch();
            final int grandparentNavIndex = tmpEntry.getIndex() + 1;
            final Entry grandparentEntry = new ImmutableEntry(grandparent, grandparentNavIndex);
            final Branch<K,V> uncle = grandparent.child(grandparentNavIndex).asBranch();
            final int uncleNavIndex = 0;
            final Entry uncleEntry = new ImmutableEntry(uncle, uncleNavIndex);
            final Node<K,V> cousin = uncle.child(uncleNavIndex);
            return new AdoptedFamily(grandparentEntry, uncleEntry, cousin);
        }
    }

    private void resetEntry(final Entry entry) {
        if(entry != null) {
            entry.getNode().asBranch().resetKey(entry.getIndex());
        }
    }
    
    private void resetEntries(final int startLevel, final int startIndex) {
        int i = startLevel;
        int prevIndex = startIndex;
        
        while(i >= 0 && prevIndex == 0) {
            final Entry entry = get(i--);
            resetEntry(entry);
            prevIndex = entry.getIndex();
        }
    }
    
    public void resetAncestorKeys() {
        final Entry parentEntry = parent();
        if(parentEntry != null) {
            resetEntry(parentEntry);
            resetEntries(level() - 2, parentEntry.getIndex());
        }
    }

    protected Entry current() {
        return get(level());
    }
    
    protected Entry parent() {
        return (level() - 1 >= 0) ? get(level() - 1) : null;
    }

    protected Entry grandparent() {
        return (level() - 2 >= 0) ? get(level() - 2) : null;
    }

    abstract protected Entry get(int i);
    abstract protected int count();
    abstract protected boolean isEmpty();
    abstract protected Traversal<K,V> clear();
    abstract protected Entry nextEntry();
    abstract protected void pop();
    abstract public void addDone(final Node<K,V> node);
    abstract public void done();
    abstract public boolean hasOrphan();
    abstract public Node<K,V> adoptOrphan();
    abstract public void disown(final Node<K,V> val);
    
    public static <K extends Comparable<K>,V> Traversal<K,V> newMutable() {
        return new MutableTraversal<>();
    }

    public Traversal<K,V> immutable() {
        final List<Entry> entries = new ArrayList<>(count());
        for(int i = 0; i < count(); ++i) {
            entries.add(get(i).immutable());
        }

        return new ImmutableTraversal<>(Collections.unmodifiableList(entries));
    }

    public Traversal<K,V> mutable() {
        return new MutableTraversal<>(this);
    }
    
    private static class MutableTraversal<K extends Comparable<K>,V> extends Traversal<K,V> {

        private final List<Node<K,V>> doneNodes;
        private final List<Entry> all;
        private int count;
        private Node<K,V> orphan;

        MutableTraversal() {
            this.all = new ArrayList<>(5);
            this.doneNodes = new ArrayList<>(2);
            this.count = 0;
            this.orphan = null;
        }

        MutableTraversal(final Traversal<K,V> toCopy) {
            this();

            for(int i = 0; i < toCopy.count(); ++i) {
                all.add(toCopy.get(i).mutable());
            }

            this.count = toCopy.count();
        }

        protected Entry get(final int i) {
            return all.get(i);
        }

        protected int count() {
            return count;
        }

        protected boolean isEmpty() {
            return all.isEmpty();
        }

        protected Traversal<K,V> clear() {
            for(int i = 0; i < count; ++i) {
                all.get(i).clear();
            }
            
            doneNodes.clear();
            count = 0;
            orphan = null;
            return this;
        }

        protected Entry nextEntry() {
            if(count < all.size()) {
                ++count;
                return current();
            }
            else {
                ++count;
                Entry entry = new MutableEntry();
                all.add(entry);
                return entry;
            }
        }

        public void pop() {
            current().clear();
            --count;
        }

        public void addDone(final Node<K,V> node) {
            doneNodes.add(node);
        }
        
        public void done() {
            for(Node<K,V> node : doneNodes) {
                node.done();
            }
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
    }

    private static class ImmutableTraversal<K extends Comparable<K>,V> extends Traversal<K,V> {
        private final List<Entry> all;

        public ImmutableTraversal(final List<Entry> all) {
            this.all = all;
        }

        protected Entry get(int i) {
            return all.get(i);
        }
        
        protected int count() {
            return all.size();
        }
        
        protected boolean isEmpty() {
            return all.isEmpty();
        }

        protected Traversal<K,V> clear() {
            throw new UnsupportedOperationException();
        }
        
        protected Entry nextEntry() {
            throw new UnsupportedOperationException();
        }
        
        protected void pop() {
            throw new UnsupportedOperationException();
        }
        
        public void addDone(final Node<K,V> node) {
            throw new UnsupportedOperationException();
        }
        
        public void done() {
            throw new UnsupportedOperationException();
        }
        
        public boolean hasOrphan() {
            throw new UnsupportedOperationException();
        }
        
        public Node<K,V> adoptOrphan() {
            throw new UnsupportedOperationException();
        }
        
        public void disown(final Node<K,V> val) {
            throw new UnsupportedOperationException();
        }
    }
}
