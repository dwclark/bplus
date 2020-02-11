package bplus;

import java.util.List;
import java.util.ArrayList;

class Traversal<K extends Comparable<K>,V> {
    private static final int DEFAULT = 5;
    public enum Direction { LEFT, RIGHT, ROOT };

    public class Entry {
        private Node<K,V> node = null;
        private Direction direction = null;
        private int index = -1;

        public Entry setNode(final Node<K,V> val) { node = val; return this; }
        public Entry setDirection(final Direction val) { direction = val; return this; }

        public Node<K,V> getNode() { return node; }
        public Direction getDirection() { return direction; }

        public Entry setIndex(final int val) { index = val; return this; }
        public int getIndex() { return index; }

        public void clear() {
            node = null;
            direction = null;
            index = -1;
        }
    }
    
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

    public Node<K,V> getLeftSibling() {
        final Entry parentEntry = parent();
        if(parentEntry == null) {
            return null;
        }

        final Branch<K,V> parent = parentEntry.node.asBranch();
        if(parentEntry.direction == Direction.RIGHT) {
            return parent.left(parentEntry.index);
        }

        if(parentEntry.direction == Direction.LEFT && parentEntry.index > 0) {
            return parent.right(parentEntry.index - 1);
        }

        if(parentEntry.direction == Direction.LEFT && parentEntry.index == 0) {
            final Entry grandEntry = grandparent();
            if(grandEntry == null) {
                return null;
            }

            final Branch<K,V> grandparent = grandEntry.node.asBranch();
            final Branch<K,V> uncle = grandparent.left(grandEntry.index).asBranch();
            return uncle.right(uncle.size() - 1);
        }

        return null;
    }

    public Node<K,V> getRightSibling() {
        
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
