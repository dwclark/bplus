package bplus;

import spock.lang.*
import bplus.impl.ObjectArray
import java.util.concurrent.ThreadLocalRandom;

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

    def "test insert cases"() {
        setup:
        def left
        def right
        def newRoot
        def btree
        
        def create = {
            def oa = new ObjectArray(Integer, Integer, 4)
            left = oa.root.newLeaf()
            right = oa.root.newLeaf()
            newRoot = oa.root.newBranch()
            
            left.sizeUp(3).put(0, 15, 15).put(1, 16, 16).put(2, 17, 17)
            right.sizeUp(3).put(0, 20, 20).put(1, 21, 21).put(2, 22, 22)
            newRoot.sizeUp(1).put(0, left, 20, right)
            oa.root = newRoot;
            btree = new BplusTree(oa);
        };
        
        when: //overflow left leaf, current insert goes right
        btree = create()
        btree.put(18, 18)
        btree.put(19, 19)
        
        then:
        left.keys() == [ 15, 16, 17, 18 ]
        right.keys() == [ 19, 20, 21, 22 ]
        newRoot.keys() == [ 19 ];

        when: //overflow left leaf, current insert stays left
        btree = create()
        btree.put(19, 19)
        btree.put(18, 18)
        
        then:
        left.keys() == [ 15, 16, 17, 18 ]
        right.keys() == [ 19, 20, 21, 22 ]
        newRoot.keys() == [ 19 ];

        when: //overflow right leaf
        btree = create()
        btree.put(23, 23)
        btree.put(24, 24)

        then:
        left.keys() == [ 15, 16, 17, 20 ]
        right.keys() == [ 21, 22, 23, 24 ]
        newRoot.keys()== [ 21 ]
    }

    def "test split with new root creation, then fill branch"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa);

        when:
        (1..20).each { num -> btree.put(num, num); }
        def root = oa.root
        def left0 = root.left(0)
        def left1 = root.left(1)
        def left2 = root.left(2)
        def left3 = root.left(3)
        def right = root.right(3)
        
        then:
        root.keys() == [5,9,13,17]
        left0.keys()== [1,2,3,4]
        left1.keys()== [5,6,7,8]
        left2.keys()== [9,10,11,12]
        left3.keys()== [13,14,15,16]
        right.keys()== [17,18,19,20]
    }

    def "test split root branch"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa);

        when:
        (1..8).each { num -> btree.put(num, num) }
        (501..504).each { num -> btree.put(num, num) }
        (1001..1008).each { num -> btree.put(num, num) }

        then:
        oa.root.keys() == [5, 501, 1001, 1005 ]
    }

    def "test add random"() {
        setup:
        10.times {
            def max = 4_096
            def list = new ArrayList(max)
            (0..<max).each { list.add(it) }
            Collections.shuffle(list)
            def branchOrder = ThreadLocalRandom.current().nextInt(6,12)
            def leafOrder = ThreadLocalRandom.current().nextInt(6,12)

            def toAdd, index, keyList, sortedKeyList, nodeKeyList, sortedNodeKeyList;
            try {
                def oa = new ObjectArray(Integer, Integer, branchOrder, leafOrder)
                def btree = new BplusTree(oa)
                list.eachWithIndex { num, i ->
                    toAdd = num
                    index = i
                    
                    btree.put(toAdd, toAdd)
                    nodeKeyList = btree.nodeKeyList()
                    sortedNodeKeyList = new ArrayList(nodeKeyList)
                    sortedNodeKeyList.sort()
                    keyList = btree.keyList()
                    sortedKeyList = new ArrayList(keyList)
                    sortedKeyList.sort()
                    
                    if((nodeKeyList as Set).size() != nodeKeyList.size()) {
                        throw new RuntimeException("duplicates in keylist");
                    }
                    
                    if(keyList != sortedKeyList) {
                        throw new RuntimeException("list no longer sorted")
                    }
                    
                    if(nodeKeyList != sortedNodeKeyList) {
                        throw new RuntimeException('node key list no longer sorted')
                    }
                }
            }
            catch(Exception e) {
                println "branchOrder: ${branchOrder}, leafOrder ${leafOrder}"
                println "adding ${toAdd} at index ${index}"
                println "nodeKeyList:       ${nodeKeyList}"
                println "sortedNodeKeyList: ${sortedNodeKeyList}"
                println "keyList:       ${keyList}"
                println "sortedKeyList: ${sortedKeyList}"
                println "all: ${list}"
                throw e
            }
        }
    }

    def "test height"() {
        setup:
        def max = 4_096
        def list = new ArrayList(max)
        (0..<max).each { list.add(it) }
        Collections.shuffle(list)
        
        def oa = new ObjectArray(Integer, Integer, 8)
        def btree = new BplusTree(oa)
        list.each { num -> btree.put(num, num) }

        expect:
        btree.height() >= 4
    }

    def "test leaf remove"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 8)
        def btree = new BplusTree(oa)
        (0..7).each { num -> btree.put(num, num) }

        when:
        btree.remove(12)

        then:
        oa.root.size() == 8

        when:
        btree.remove(7)

        then:
        oa.root.keys() == [0,1,2,3,4,5,6]
        oa.root.size() == 7

        when:
        btree.remove(1)
        btree.remove(4)

        then:
        oa.root.keys() == [0,2,3,5,6]
        oa.root.size() == 5

        when:
        [0,2,3,5,6].each { btree.remove(it) }

        then:
        oa.root.keys() == []
        oa.root.size() == 0
    }

    def "test right leaf sibling borrow and merge"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa)

        when:
        (1..20).each { num -> btree.put(num, num) }

        then:
        oa.root.keys() == [5,9,13,17]
        oa.root.left(0).keys() == [1,2,3,4]
        oa.root.left(1).keys() == [5,6,7,8]
        oa.root.left(2).keys() == [9,10,11,12]
        oa.root.left(3).keys() == [13,14,15,16]
        oa.root.right(3).keys() == [17,18,19,20]

        when:
        btree.remove(1);

        then:
        oa.root.keys() == [5,9,13,17]
        oa.root.left(0).keys() == [2,3,4]

        when:
        btree.remove(2)

        then:
        oa.root.keys() == [5,9,13,17]
        oa.root.left(0).keys() == [3,4]

        when:
        btree.remove(4)

        then:
        oa.root.left(0).keys() == [3,5]
        oa.root.left(1).keys() == [6,7,8]
        oa.root.keys() == [6,9,13,17]

        when:
        btree.remove(5)

        then:
        oa.root.left(0).keys() == [3,6]
        oa.root.left(1).keys() == [7,8]
        oa.root.keys() == [7,9,13,17]

        when:
        btree.remove(3)

        then:
        oa.root.left(0).keys() == [6,7,8]
        oa.root.keys() == [9,13,17]
    }

    def "test left leaf sibling borrow and merge"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa)

        when:
        (1..20).each { num -> btree.put(num, num) }
        
        then:
        oa.root.keys() == [5,9,13,17]
        oa.root.left(0).keys() == [1,2,3,4]
        oa.root.left(1).keys() == [5,6,7,8]
        oa.root.left(2).keys() == [9,10,11,12]
        oa.root.left(3).keys() == [13,14,15,16]
        oa.root.right(3).keys() == [17,18,19,20]

        when:
        btree.remove(19)

        then:
        oa.root.keys() == [5,9,13,17]
        oa.root.right(3).keys() == [17,18,20]

        when:
        btree.remove(17)

        then:
        oa.root.keys() == [5,9,13,18]
        oa.root.right(3).keys() == [18,20]

        when:
        btree.remove(18)

        then:
        oa.root.keys() == [5,9,13,16]
        oa.root.left(3).keys() == [13,14,15]
        oa.root.right(3).keys() == [16,20]

        when:
        btree.remove(13)
        btree.remove(16)

        then:
        oa.root.keys() == [5,9,14]
        oa.root.right(2).keys() == [14,15,20]
        oa.root.size() == 3
    }

    def "test left leaf remove"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa)
        
        when:
        (1..100).each { num -> btree.put(num, num) }
        def upperLeft = oa.root.left(0);
        def farLeft = upperLeft.left(0);
        def nextLeft = upperLeft.right(0);
        
        then:
        farLeft.keys() == [1,2,3,4]
        nextLeft.keys() == [5,6,7,8]

        when:
        [1,2,3,4].each { btree.remove(it) }

        then:
        farLeft.keys() == [5,6]
        nextLeft.keys() == [7,8]

        when:
        btree.remove(5)
        farLeft = upperLeft.left(0)
        
        then:
        upperLeft.size() == 3
        farLeft.keys() == [6,7,8]
        upperLeft.keys() == [9,13,17]

        when:
        [6,7,8,9,10,11,12,13,14,15,16,17,18].each { btree.remove(it) }

        then:
        upperLeft.size() == 2
        
    }

}
