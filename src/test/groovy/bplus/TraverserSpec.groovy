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
        def traverser = Traversal.newMutable().leftTraverser(oa.root)

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
}

