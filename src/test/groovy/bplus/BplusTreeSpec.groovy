package bplus;

import spock.lang.*
import bplus.impl.ObjectArray

class BplusTreeSpec extends Specification {

    def "test creation, put, and get no splits"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa)
        (0..2).each { num -> btree.put(num, num * 10); }

        expect:
        btree.get(0) == 0
        btree.get(1) == 10
        btree.get(2) == 20
    }

    def 'test creation, put, and get with splits'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa)
        (0..3).each { num -> btree.put(num, num * 10); }

        expect:
        btree.get(0) == 0
        btree.get(1) == 10
        btree.get(2) == 20
        btree.get(3) == 30;
    }
}
