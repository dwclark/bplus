package bplus;

import spock.lang.*
import bplus.impl.ObjectArray
import java.util.concurrent.ThreadLocalRandom;

class TraverserSpec extends Specification {

    def 'test left traversal'() {
        setup:
        def oa = new ObjectArray(Integer,Integer, 8)
        def btree = new BplusTree(oa);
        (1..1024).each { btree.put(it, it) }

        when:
        def traverser = Traversal.newMutable().leftTraversal(oa.root).traverser();

        then:
        traverser.hasNext();

        when:
        def list = []
        while(traverser.hasNext()) {
            traverser.next()
            list << traverser.leaf.key(traverser.index)
        }
        
        then:
        list == (1..1024) as List
        
    }

    def 'test traversal comparisons'() {
        setup:
        def oa = new ObjectArray(Integer,Integer, 8)
        def btree = new BplusTree(oa);
        (1..1024).each { btree.put(it, it) }

        when:
        def lower = Traversal.newMutable().execute(oa.root, 10);
        def upper = Traversal.newMutable().execute(oa.root, 20);

        then:
        (lower <=> upper) == -1
        (lower <=> lower) == 0
        (upper <=> lower) == 1

        when:
        def traverser = lower.traverser()
        10.times { traverser.next() }

        then:
        (lower <=> upper) == 0        
    }

    def 'test right and left traversal'() {
        setup:
        def oa = new ObjectArray(Integer,Integer, 8)
        def btree = new BplusTree(oa);
        (1..1024).each { btree.put(it, it) }

        when:
        def left = Traversal.newMutable().leftTraversal(oa.root)
        def lower = Traversal.newMutable().execute(oa.root, 100);
        def upper = Traversal.newMutable().rightTraversal(oa.root);

        then:
        (lower <=> upper) == -1
        (left <=> lower) == -1
        (left <=> upper) == -1

        when:
        def list = []
        def traverser = left.traverser()
        while(left < lower) {
            traverser.next()
            list << traverser.leaf.key(traverser.index)
        }

        then:
        list == (1..100)
    }
}

