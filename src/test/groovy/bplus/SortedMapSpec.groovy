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
        btree.tailMap(100).values() as List == (100..1024)
        btree.tailMap(100).firstKey() == 100
        btree.tailMap(100).lastKey() == 1024
    }

    def 'test head map'() {
        setup:
        def btree = basicMap();

        expect:
        btree.headMap(100).keySet() as List == (1..99)
        btree.headMap(100).values() as List == (1..99)
        btree.headMap(100).firstKey() == 1
        btree.headMap(100).lastKey() == 99
    }

    def 'test sub map'() {
        setup:
        def btree = basicMap()

        expect:
        btree.subMap(50, 100).keySet() as List == (50..99)
        btree.subMap(50, 100).values() as List == (50..99)
        btree.subMap(50, 100).firstKey() == 50
        btree.subMap(50, 100).lastKey() == 99
    }

    def 'test sorted map methods'() {
        setup:
        def map = basicMap().subMap(50, 100)
        def map2 = basicMap().subMap(50, 100)

        def copy1 = new HashMap(map);
        def copy2 = new HashMap(map2)
        
        expect:
        map.containsKey(50)
        !map.containsKey(49)
        map.containsValue(75)
        !map.containsValue(101)
        map.get(56) == 56
        map.get(102) == null
        map.size() == 50
        map.size() == map2.size()
        map == map2
        map.hashCode() == map2.hashCode()
    }

    def 'test empty'() {
        setup:
        def map = basicMap().tailMap(2000)
        def map2 = basicMap().tailMap(1024)
        
        expect:
        map.isEmpty()
        !map2.isEmpty()
        map2.size() == 1
    }

    def 'test bad ranges'() {
        when:
        basicMap().subMap(200, 100)

        then:
        thrown(IllegalArgumentException)

        when:
        basicMap().subMap(100, 200).subMap(150, 125)

        then:
        thrown(IllegalArgumentException)
    }
}
