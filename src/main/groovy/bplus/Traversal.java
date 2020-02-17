package bplus;

import java.util.List;
import java.util.ArrayList;

class Traversal<K extends Comparable<K>,V> {

    public static class SiblingRelation<T extends Comparable<T>,U> {
        private final Branch<T,U> commonParent;
        private final int parentIndex;
        private final Node<T,U> sibling;

        private SiblingRelation(final Branch<T,U> commonParent, final int parentIndex, final Node<T,U> sibling) {
            this.commonParent = commonParent;
            this.parentIndex = parentIndex;
            this.sibling = sibling;
        }

        public Branch<T,U> getCommonParent() { return commonParent; }
        public int getParentIndex() { return parentIndex; }
        public Node<T,U> getSibling() { return sibling; }
    }
    
    public static class Entry<T extends Comparable<T>,U> {
        private Node<T,U> node = null;
        private Direction direction = null;
        private int index = -1;
        
        public Entry<T,U> setNode(final Node<T,U> val) { node = val; return this; }
        public Node<T,U> getNode() { return node; }

        public Entry<T,U> setDirection(final Direction val) { direction = val; return this; }
        public Direction getDirection() { return direction; }

        public Entry<T,U> setIndex(final int val) { index = val; return this; }
        public int getIndex() { return index; }

        public void clear() {
            node = null;
            direction = null;
            index = -1;
        }
    }

    private static final int DEFAULT = 5;

    private final List<Entry<K,V>> all;
    private int count;
    private Node<K,V> orphan;
    
    Traversal() {
        this.all = new ArrayList<>();
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

        count = 0;
        orphan = null;
        return this;
    }

    public Traversal<K,V> execute(final Node<K,V> root, final K k) {
        clear();
        Node<K,V> current = root;
        while(!current.isLeaf()) {
            final Branch<K,V> branch = current.asBranch();
            final int searchIndex = branch.search(k);
            final Direction dir = direction(searchIndex, branch);
            final int navIndex = navigateIndex(searchIndex, branch);
            current = (dir == Direction.LEFT) ? goLeft(branch, navIndex) : goRight(branch, navIndex);
        }

        goLeaf(current);
        return this;
    }

    public int level() {
        return count - 1;
    }

    public void goLeaf(final Node<K,V> node) {
        next().setNode(node).setDirection(Direction.NONE).setIndex(0);
    }

    public Node<K,V> goLeft(final Branch<K,V> node, final int index) {
        next().setNode(node).setDirection(Direction.LEFT).setIndex(index);
        return node.left(index);
    }

    public Node<K,V> goRight(final Branch<K,V> node, final int index) {
        next().setNode(node).setDirection(Direction.RIGHT).setIndex(index);
        return node.right(index);
    }

    public void pop() {
        getCurrent().clear();
        --count;
    }

    public boolean hasOrphan() {
        return orphan != null;
    }

    public Node<K,V> getOrphan() {
        return orphan;
    }

    public void setOrphan(final Node<K,V> val) {
        this.orphan = val;
    }

    public SiblingRelation<K,V> getLeftSiblingWithRoom() {
        final Entry<K,V> parentEntry = parent();
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
            sibling = parent.left(parentIndex);
        }
        else if(parentEntry.direction == Direction.LEFT && parentEntry.index == 0) {
            final Entry<K,V> grandEntry = grandparent();
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
            return new SiblingRelation<>(parent, parentIndex, sibling);
        }
        else {
            return null;
        }
    }

    public SiblingRelation<K,V> getRightSiblingWithRoom() {
        final Entry<K,V> parentEntry = parent();
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
            sibling = parent.right(parentIndex);
        }
        else if(parentEntry.direction == Direction.RIGHT && (parentEntry.index + 1) == parentEntry.node.size()) {
            final Entry<K,V> grandEntry = grandparent();
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
            return new SiblingRelation<>(parent, parentIndex, sibling);
        }
        else {
            return null;
        }
    }

    public Entry<K,V> getCurrent() {
        return all.get(level());
    }

    public Entry<K,V> parent() {
        return (level() - 1 >= 0) ? all.get(level() - 1) : null;
    }

    public Entry<K,V> grandparent() {
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

    private Direction direction(final int index, final Branch<K,V> branch) {
        if(index >= 0) {
            return Direction.RIGHT;
        }
        
        final int retIndex = Node.insertIndex(index);
        if(retIndex < branch.size()) {
            return Direction.LEFT;
        }
        else {
            return Direction.RIGHT;
        }
    }

    private int navigateIndex(final int index, final Branch<K,V> branch) {
        if(index >= 0) {
            return index;
        }
        
        int retIndex = Node.insertIndex(index);
        if(retIndex < branch.size()) {
            return retIndex;
        }
        else {
            return retIndex - 1;
        }
    }
}
