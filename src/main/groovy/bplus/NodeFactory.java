package bplus;

import java.util.function.Consumer;

public interface NodeFactory<K extends Comparable<K>,V> {
    Branch<K,V> newBranch();
    Leaf<K,V> newLeaf();
    Node<K,V> getRoot();
    void setRoot(Node<K,V> val);
}
