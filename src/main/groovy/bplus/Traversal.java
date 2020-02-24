package bplus;

import java.util.List;
import java.util.ArrayList;

class Traversal<K extends Comparable<K>,V> {

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
    
    public Traverser<K,V> leftTraverser(final Node<K,V> root) {
        nextEntry().setNode(root).setIndex(initialIndex(root));
        Node<K,V> node = root;
        
        while(!node.isLeaf()) {
            node = node.asBranch().child(0);
            nextEntry().setNode(node).setIndex(initialIndex(node));
        }

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
            if(all.isEmpty()) {
                return false;
            }
            
            for(int i = 0; i < count; ++i) {
                final TEntry e = all.get(i);
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
            throw new UnsupportedOperationException();
        }

        private void positionNext() {
            for(int i = count - 2; i >= 0; --i) {
                pop();
                final TEntry toTest = all.get(i);
                if(toTest.hasNext()) {
                    toTest.next();
                    break;
                }
            }

            if(all.isEmpty()) {
                return;
            }

            TEntry tentry = current();
            while(!tentry.getNode().isLeaf()) {
                final Branch<K,V> branch = tentry.getNode().asBranch();
                final Node<K,V> node = branch.child(tentry.getIndex());
                tentry = nextEntry().setNode(node).setIndex(initialIndex(node));
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
        private final TEntry parentEntry;
        private final Node<K,V> sibling;
        
        private SameFamily(final TEntry parentEntry, final Node<K,V> sibling) {
            this.parentEntry = parentEntry;
            this.sibling = sibling;
        }
        
        public Branch<K,V> getParent() { return parentEntry.getNode().asBranch(); }
        public int getIndex() { return parentEntry.index; }
        public Node<K,V> getSibling() { return sibling; }

        public void resetAncestorKeys() {
            resetEntry(parentEntry);
            resetEntries(level() - 2, parentEntry.getIndex());
        }
    }
    
    private class AdoptedFamily implements SiblingRelation<K,V> {
        private final TEntry grandparentEntry;
        private final TEntry uncleEntry;
        private final Node<K,V> sibling;

        private AdoptedFamily(final TEntry grandparentEntry, final TEntry uncleEntry,
                              final Node<K,V> sibling) {
            this.grandparentEntry = grandparentEntry;
            this.uncleEntry = uncleEntry;
            this.sibling = sibling;
        }

        public Branch<K,V> getParent() { return grandparentEntry.getNode().asBranch(); }
        public int getIndex() { return grandparentEntry.index; }
        public Node<K,V> getSibling() { return sibling; }

        public void resetAncestorKeys() {
            resetEntry(uncleEntry);
            resetEntry(grandparentEntry);
            resetEntries(level() - 3, grandparentEntry.getIndex());
        }
    }

    interface Entry<T extends Comparable<T>, U> {
        Node<T,U> getNode();
        int getIndex();
    }

    public class TEntry implements Entry<K,V> {
        private Node<K,V> node = null;
        private int index = -1;
        
        public TEntry setNode(final Node<K,V> val) { node = val; return this; }
        public Node<K,V> getNode() { return node; }

        public TEntry setIndex(final int val) { index = val; return this; }
        public int getIndex() { return index; }

        public boolean hasNext() {
            return index + 1 < node.size();
        }

        public void next() {
            ++index;
        }

        public boolean hasPrevious() {
            return index >= 0;
        }

        public void previous() {
            --index;
        }

        public void clear() {
            node = null;
            index = -1;
        }
    }
    
    private static final int DEFAULT = 5;
    private final List<Node<K,V>> doneNodes;
    private final List<TEntry> all;
    private int count;
    private Node<K,V> orphan;
    
    Traversal() {
        this.all = new ArrayList<>(DEFAULT);
        this.doneNodes = new ArrayList<>(2);
        this.count = 0;
        this.orphan = null;

        for(int i = 0; i < DEFAULT; ++i) {
            all.add(new TEntry());
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
            nextEntry().setNode(current).setIndex(navIndex);
            current = branch.child(navIndex);
        }

        nextEntry().setNode(current).setIndex(0);
        return this;
    }

    public int level() {
        return count - 1;
    }

    public void pop() {
        current().clear();
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
        final TEntry parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        if(parentEntry.index > 0) {
            final Branch<K,V> parent = parentEntry.node.asBranch();
            final int index = parentEntry.index - 1;
            return new SameFamily(new TEntry().setNode(parent).setIndex(index), parent.child(index));
        }
        else {
            final TEntry tmpEntry = grandparent();
            if(tmpEntry == null || tmpEntry.index == 0) {
                return null;
            }

            final Branch<K,V> grandparent = tmpEntry.node.asBranch();
            final int grandparentNavIndex = tmpEntry.index - 1;
            final TEntry grandparentEntry = new TEntry().setNode(grandparent).setIndex(grandparentNavIndex);
            final Branch<K,V> uncle = grandparent.child(grandparentNavIndex).asBranch();
            final int uncleNavIndex = uncle.lastIndex();
            final TEntry uncleEntry = new TEntry().setNode(uncle).setIndex(uncleNavIndex);
            final Node<K,V> cousin = uncle.child(uncleNavIndex);
            return new AdoptedFamily(grandparentEntry, uncleEntry, cousin);
        }
    }

    public SiblingRelation<K,V> getRightSibling() {
        final TEntry parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        if(parentEntry.index + 1 < parentEntry.node.size()) {
            final Branch<K,V> parent = parentEntry.node.asBranch();
            final int index = parentEntry.index + 1;
            return new SameFamily(new TEntry().setNode(parent).setIndex(index), parent.child(index));
        }
        else {
            final TEntry tmpEntry = grandparent();
            if(tmpEntry == null || (tmpEntry.index + 1 == tmpEntry.node.size())) {
                return null;
            }
            
            final Branch<K,V> grandparent = tmpEntry.node.asBranch();
            final int grandparentNavIndex = tmpEntry.index + 1;
            final TEntry grandparentEntry = new TEntry().setNode(grandparent).setIndex(grandparentNavIndex);
            final Branch<K,V> uncle = grandparent.child(grandparentNavIndex).asBranch();
            final int uncleNavIndex = 0;
            final TEntry uncleEntry = new TEntry().setNode(uncle).setIndex(uncleNavIndex);
            final Node<K,V> cousin = uncle.child(uncleNavIndex);
            return new AdoptedFamily(grandparentEntry, uncleEntry, cousin);
        }
    }

    private void resetEntry(final TEntry entry) {
        if(entry != null) {
            entry.getNode().asBranch().resetKey(entry.getIndex());
        }
    }
    
    private void resetEntries(final int startLevel, final int startIndex) {
        int i = startLevel;
        int prevIndex = startIndex;
        
        while(i >= 0 && prevIndex == 0) {
            final TEntry entry = all.get(i--);
            resetEntry(entry);
            prevIndex = entry.getIndex();
        }
    }
    
    public void resetAncestorKeys() {
        final TEntry parentEntry = parent();
        if(parentEntry != null) {
            resetEntry(parentEntry);
            resetEntries(level() - 2, parentEntry.getIndex());
        }
    }

    private TEntry current() {
        return all.get(level());
    }
    
    public Entry<K,V> getCurrent() {
        return current();
    }

    private TEntry parent() {
        return (level() - 1 >= 0) ? all.get(level() - 1) : null;
    }

    public Entry<K,V> getParent() {
        return parent();
    }

    private TEntry grandparent() {
        return (level() - 2 >= 0) ? all.get(level() - 2) : null;
    }

    public Entry<K,V> getGrandparent() {
        return grandparent();
    }

    private TEntry nextEntry() {
        if(count < all.size()) {
            ++count;
            return current();
        }
        else {
            ++count;
            TEntry entry = new TEntry();
            all.add(entry);
            return entry;
        }
    }
}
