package bplus;

import java.util.function.Consumer;

public interface NodeStore<K extends Comparable<K>,V> {
    Node<K,V> getRoot();
    void setRoot(Node<K,V> val);
    Class<K> getKeyType();
    Class<V> getValueType();
}
