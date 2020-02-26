package bplus;

import spock.lang.*
import bplus.impl.ObjectArray
import java.util.concurrent.ThreadLocalRandom;

class SortedMapSpec extends Specification {

    private basicMap() {
        def oa = new ObjectArray(Integer,Integer, 8)
        def btree = new BplusTree(oa);
        (1..1024).each { btree.put(it, it) }
        return btree;
    }

    def 'test tail map'() {
        setup:
        def btree = basicMap();

        expect:
        btree.tailMap(100).keySet() as List == (100..1024)
    }
}
