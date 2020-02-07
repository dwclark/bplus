package bplus;

public interface NodeFactory<K extends Comparable<K>,V> {
    Branch<K> newBranch();
    Leaf<K,V> newLeaf();
    Node<K> getRoot();
}
