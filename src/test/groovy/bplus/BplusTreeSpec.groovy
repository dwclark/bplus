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

    def "test man random"() {
        setup:
        10.times {
            def max = 4_096
            def list = new ArrayList(max)
            (0..<max).each { list.add(it) }
            Collections.shuffle(list)

            def toAdd, index, keyList, sortedKeyList, nodeKeyList, sortedNodeKeyList;
            try {
                def oa = new ObjectArray(Integer, Integer, 8)
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
                println "adding ${toAdd} at index ${index}"
                println "nodeKeyList:       ${nodeKeyList}"
                println "sortedNodeKeyList: ${sortedNodeKeyList}"
                println "keyList:       ${keyList}"
                println "sortedKeyList: ${sortedKeyList}"
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

        println btree.height()

        expect:
        btree.height() == 5 //(9^4) + 1 level for leaves
    }
}
