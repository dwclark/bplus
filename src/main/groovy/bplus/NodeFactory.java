package bplus;

public interface NodeFactory<K extends Comparable<K>,V> {
    Branch<K> branch();
    Leaf<K,V> leaf();

    void done(Branch<K> o);
    void done(Leaf<K,V> o);
}
