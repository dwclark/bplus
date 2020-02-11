package bplus;

import java.util.List;
import java.util.ArrayList;

class Traversal<K extends Comparable<K>,V> {

    public enum Direction { LEFT, RIGHT, ROOT };
    
    public class SiblingRelation {
        private final Branch<K,V> commonParent;
        private final int parentIndex;
        private final Node<K,V> sibling;

        private SiblingRelation(final Branch<K,V> commonParent, final int parentIndex, final Node<K,V> sibling) {
            this.commonParent = commonParent;
            this.parentIndex = parentIndex;
            this.sibling = sibling;
        }

        public Branch<K,V> getCommonParent() { return commonParent; }
        public int getParentIndex() { return parentIndex; }
        public Node<K,V> getSibling() { return sibling; }
    }
    
    public class Entry {
        private Node<K,V> node = null;
        private Direction direction = null;
        private int index = -1;
        private Node<K,V> orphan = null;
        
        public Entry setNode(final Node<K,V> val) { node = val; return this; }
        public Node<K,V> getNode() { return node; }

        public Entry setDirection(final Direction val) { direction = val; return this; }
        public Direction getDirection() { return direction; }

        public Entry setIndex(final int val) { index = val; return this; }
        public int getIndex() { return index; }

        public Node<K,V> getOrphan() { return orphan; }
        public Entry setOrphan(final Node<K,V> val) { orphan = val; return this; } 

        public void clear() {
            node = null;
            direction = null;
            index = -1;
            orphan = null;
        }
    }

    private static final int DEFAULT = 5;

    private final List<Entry> all;
    private int count;
    
    Traversal() {
        this.all = new ArrayList<>();
        this.count = 0;

        for(int i = 0; i < DEFAULT; ++i) {
            all.add(new Entry());
        }
    }

    public void clear() {
        for(int i = 0; i < count; ++i) {
            all.get(i).clear();
        }

        count = 0;
    }

    public int level() {
        return count - 1;
    }

    public void goRoot(final Node<K,V> node, final int index) {
        next().setNode(node).setDirection(Direction.ROOT).setIndex(index);
    }

    public void goLeft(final Node<K,V> node, final int index) {
        next().setNode(node).setDirection(Direction.LEFT).setIndex(index);
    }

    public void goRight(final Node<K,V> node, final int index) {
        next().setNode(node).setDirection(Direction.RIGHT).setIndex(index);
    }

    public void pop() {
        current().clear();
        --count;
    }

    public SiblingRelation getLeftSiblingWithRoom() {
        final Entry parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        Branch<K,V> parent = null;
        int parentIndex = -1;
        Node<K,V> sibling = null;
        if(parentEntry.direction == Direction.RIGHT) {
            parent = parentEntry.node.asBranch();
            parentIndex = parentEntry.index;
            sibling = parent.left(parentEntry.index);
        }
        else if(parentEntry.direction == Direction.LEFT && parentEntry.index > 0) {
            parent = parentEntry.node.asBranch();
            parentIndex = parentEntry.index - 1;
            sibling = parent.right(parentIndex);
        }
        else if(parentEntry.direction == Direction.LEFT && parentEntry.index == 0) {
            final Entry grandEntry = grandparent();
            if(grandEntry == null) {
                return null;
            }

            final Branch<K,V> grandparent = grandEntry.node.asBranch();
            final Branch<K,V> uncle = grandparent.left(grandEntry.index).asBranch();
            parent = grandparent;
            parentIndex = parentEntry.index;
            sibling = uncle.right(uncle.size() - 1);
        }

        if(sibling != null && !sibling.isFull()) {
            return new SiblingRelation(parent, parentIndex, sibling);
        }
        else {
            return null;
        }
    }

    public SiblingRelation getRightSibling() {
        final Entry parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        Branch<K,V> parent = null;
        int parentIndex = -1;
        Node<K,V> sibling = null;
        if(parentEntry.direction == Direction.LEFT) {
            parent = parentEntry.node.asBranch();
            parentIndex = parentEntry.index;
            sibling = parent.right(parentEntry.index);
        }
        else if(parentEntry.direction == Direction.RIGHT && (parentEntry.index + 1) < parentEntry.node.size()) {
            parent = parentEntry.node.asBranch();
            parentIndex = parentEntry.index + 1;
            sibling = parent.left(parentIndex);
        }
        else if(parentEntry.direction == Direction.RIGHT && (parentEntry.index + 1) == parentEntry.node.size()) {
            final Entry grandEntry = grandparent();
            if(grandEntry == null) {
                return null;
            }

            final Branch<K,V> grandparent = grandEntry.node.asBranch();
            final Branch<K,V> uncle = grandparent.right(grandEntry.index).asBranch();
            parent = grandparent;
            parentIndex = parentEntry.index;
            sibling = uncle.left(0);
        }

        if(sibling != null && !sibling.isFull()) {
            return new SiblingRelation(parent, parentIndex, sibling);
        }
        else {
            return null;
        }
    }

    private Entry current() {
        return all.get(level());
    }

    private Entry parent() {
        return (level() - 1 >= 0) ? all.get(level() - 1) : null;
    }

    private Entry grandparent() {
        return (level() - 2 >= 0) ? all.get(level() - 2) : null;
    }

    private Entry next() {
        if(count < all.size()) {
            ++count;
            return all.get(count);
        }
        else {
            ++count;
            Entry entry = new Entry();
            all.add(entry);
            return entry;
        }
    }
}
