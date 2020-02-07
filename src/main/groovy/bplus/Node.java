package bplus;

import java.util.List;
import java.util.ArrayList;

public interface Node<K extends Comparable<K>> {
    int size();
    Node<K> size(int sz);
    int order();
    K key(int index);
    boolean isBranch();
    void done();

    default boolean isLeaf() {
        return !isBranch();
    }

    default boolean isFull() {
        return size() == order();
    }
    
    default int compare(int index, K rhs) {
        return key(index).compareTo(rhs);
    }

    default int search(final K lookFor) {
        int low = 0;
        int high = size() - 1;

        while(low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = compare(mid, lookFor);

            if(cmp < 0) {
                low = mid + 1;
            }
            else if(cmp > 0) {
                high = mid - 1;
            }
            else {
                return mid;
            }
        }

        return insertIndex(low);
    }

    default Node<K> sizeUp(final int by) {
        return size(size() + by);
    }

    default Node<K> sizeDown(final int by) {
        return size(size() - by);
    }

    default List<K> keys() {
        List<K> ret = new ArrayList<>(size());
        for(int i = 0; i < size(); i++) {
            ret.add(key(i));
        }

        return ret;
    }

    static boolean found(final int index) {
        return index >= 0;
    }

    static int insertIndex(final int index) {
        return -(index + 1);
    }
}
