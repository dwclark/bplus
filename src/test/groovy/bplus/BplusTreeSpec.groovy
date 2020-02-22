package bplus;

import spock.lang.*
import bplus.impl.ObjectArray
import java.util.concurrent.ThreadLocalRandom;

class BplusTreeSpec extends Specification {

    private makeLeaf(final ObjectArray oa, final List<Integer> list) {
        def leaf = oa.root.newLeaf();
        list.each { leaf.insert(it, it) }
        return leaf;
    }

    private makeBranch(final ObjectArray oa, final List<List<Integer>> lists) {
        def branch = oa.root.newBranch()
        lists.eachWithIndex { list, i ->
            branch.sizeUp(1)
            branch.put(i, makeLeaf(oa, list))
        }

        return branch
    }

    def "test get single leaf root"() {
        setup:
        def elements = [1,2,3,4]
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa)
        oa.root = makeLeaf(oa, elements)

        expect:
        elements.every { e -> btree.get(e) == e }
        btree.get(0) == null
        btree.get(5) == null
    }

    def "test get 2 level btree"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa)
        oa.root = makeBranch(oa, [[1,2,3,4],
                                  [5,6,7,8],
                                  [9,10,11,12],
                                  [13,14,15,16]])
        expect:
        (1..16).every { e -> btree.get(e) == e }
        btree.get(0) == null
        btree.get(17) == null
    }
    
    def "test creation, put, and get no splits"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa)
        (0..3).each { num -> btree.put(num, num * 10); }

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
            newRoot.sizeUp(2).put(0, left).put(1, right)
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
        newRoot.keys() == [ 15, 19 ];

        when: //overflow left leaf, current insert stays left
        btree = create()
        btree.put(19, 19)
        btree.put(18, 18)
        
        then:
        left.keys() == [ 15, 16, 17, 18 ]
        right.keys() == [ 19, 20, 21, 22 ]
        newRoot.keys() == [ 15, 19 ];

        when: //overflow right leaf
        btree = create()
        btree.put(23, 23)
        btree.put(24, 24)

        then:
        left.keys() == [ 15, 16, 17, 20 ]
        right.keys() == [ 21, 22, 23, 24 ]
        newRoot.keys()== [ 15, 21 ]
    }

    def "test split with new root creation, then fill branch"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def btree = new BplusTree(oa);

        when:
        (1..16).each { num -> btree.put(num, num); }
        def root = oa.root
        def left0 = root.child(0)
        def left1 = root.child(1)
        def left2 = root.child(2)
        def left3 = root.child(3)
        
        then:
        root.keys() == [1,5,9,13]
        left0.keys()== [1,2,3,4]
        left1.keys()== [5,6,7,8]
        left2.keys()== [9,10,11,12]
        left3.keys()== [13,14,15,16]
    }

    def "test specific"() {
        setup:
        def list = [41, 59, 57, 18, 22, 120, 121, 102, 19, 29, 114, 12, 13, 127, 26, 15, 3, 9, 45, 90, 60, 106, 100, 38, 48, 24, 109, 62, 74, 116, 55, 39, 63, 25, 31, 46, 61, 82, 111, 35, 104, 89, 123, 67, 119, 51, 84, 21, 11, 10, 86, 30, 33, 27, 32, 65, 37, 16, 23, 122, 69, 75, 125, 113, 58, 72, 92, 43, 73, 34, 40, 103, 17, 14, 93, 50, 68, 5, 79, 87, 107, 80, 77, 20, 110, 117, 101, 53, 4, 81, 94, 36, 126, 78, 71, 66, 70, 49, 76, 96, 112, 83, 99, 124, 6, 98, 91, 97, 1, 105, 28, 0, 52, 115, 64, 7, 118, 88, 8, 54, 56, 2, 108, 95, 85, 47, 44, 42]

        def toAdd, toRemove, index, keyList, sortedKeyList
        try {
            def oa = new ObjectArray(Integer, Integer, 4)
            def btree = new BplusTree(oa)
            list.eachWithIndex { num, i ->
                toAdd = num
                index = i

                btree.put(toAdd, toAdd)
                keyList = btree.keyList()
                sortedKeyList = new ArrayList(keyList)
                sortedKeyList.sort()

                btree.assertValidKeys()
                
                if(keyList != sortedKeyList) {
                    throw new RuntimeException("list no longer sorted")
                }
            }
            
            toAdd = null
        }
        catch(Exception e) {
            println "adding ${toAdd} removing ${toRemove}"
            println "keyList:       ${keyList}"
            println "sortedKeyList: ${sortedKeyList}"
            println "all: ${list}"
            throw e
        }
    }

    def "test add/remove random"() {
        setup:
        10.times {
            def max = 4096
            def list = new ArrayList(max)
            (0..<max).each { list.add(it) }
            Collections.shuffle(list)
            def removalList = new ArrayList(list)
            Collections.shuffle(removalList)
            def branchOrder = ThreadLocalRandom.current().nextInt(6,12)
            def leafOrder = ThreadLocalRandom.current().nextInt(6,12)

            def toAdd, toRemove, index, keyList, sortedKeyList
            try {
                def oa = new ObjectArray(Integer, Integer, branchOrder, leafOrder)
                def btree = new BplusTree(oa)
                list.eachWithIndex { num, i ->
                    toAdd = num
                    index = i
                    
                    btree.put(toAdd, toAdd)
                    btree.assertValidKeys()
                    btree.assertOrders()
                    keyList = btree.keyList()
                    sortedKeyList = new ArrayList(keyList)
                    sortedKeyList.sort()
                    
                    if(keyList != sortedKeyList) {
                        throw new RuntimeException("list no longer sorted")
                    }

                    btree.assertValidKeys()
                }

                toAdd = null
                
                removalList.eachWithIndex { num, i ->
                    toRemove = num
                    index = i
                    
                    btree.remove(toRemove)
                    btree.assertValidKeys()
                    btree.assertOrders()
                    keyList = btree.keyList()
                    sortedKeyList = new ArrayList(keyList)
                    sortedKeyList.sort()
                    
                    if(keyList != sortedKeyList) {
                        throw new RuntimeException("[removal] list no longer sorted")
                    }
                }
            }
            catch(Exception e) {
                println "branchOrder: ${branchOrder}, leafOrder ${leafOrder}"
                println "adding ${toAdd} removing ${toRemove}"
                println "keyList:       ${keyList}"
                println "sortedKeyList: ${sortedKeyList}"
                println "all: ${list}"
                println "removal list: ${removalList}"
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
        def oa = new ObjectArray(Integer, Integer, 5, 4)
        def btree = new BplusTree(oa)
        
        when:
        (1..20).each { num -> btree.put(num, num) }

        then:
        oa.root.keys() == [1,5,9,13,17]
        oa.root.child(0).keys() == [1,2,3,4]
        oa.root.child(1).keys() == [5,6,7,8]
        oa.root.child(2).keys() == [9,10,11,12]
        oa.root.child(3).keys() == [13,14,15,16]
        oa.root.child(4).keys() == [17,18,19,20]

        when:
        btree.remove(1);

        then:
        oa.root.keys() == [2,5,9,13,17]
        oa.root.child(0).keys() == [2,3,4]
        
        when:
        btree.remove(2)

        then:
        oa.root.keys() == [3,5,9,13,17]
        oa.root.child(0).keys() == [3,4]

        when:
        btree.remove(4)

        then:
        oa.root.child(0).keys() == [3,5]
        oa.root.child(1).keys() == [6,7,8]
        oa.root.keys() == [3,6,9,13,17]

        when:
        btree.remove(5)

        then:
        oa.root.child(0).keys() == [3,6]
        oa.root.child(1).keys() == [7,8]
        oa.root.keys() == [3,7,9,13,17]

        when:
        btree.remove(3)

        then:
        oa.root.child(0).keys() == [6,7,8]
        oa.root.keys() == [6,9,13,17]
    }

    def "test left leaf sibling borrow and merge"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 5, 4)
        def btree = new BplusTree(oa)

        when:
        (1..20).each { num -> btree.put(num, num) }
        
        then:
        oa.root.keys() == [1, 5,9,13,17]
        oa.root.child(0).keys() == [1,2,3,4]
        oa.root.child(1).keys() == [5,6,7,8]
        oa.root.child(2).keys() == [9,10,11,12]
        oa.root.child(3).keys() == [13,14,15,16]
        oa.root.child(4).keys() == [17,18,19,20]

        when:
        btree.remove(19)

        then:
        oa.root.keys() == [1,5,9,13,17]
        oa.root.child(4).keys() == [17,18,20]

        when:
        btree.remove(17)

        then:
        oa.root.keys() == [1,5,9,13,18]
        oa.root.child(4).keys() == [18,20]

        when:
        btree.remove(18)

        then:
        oa.root.keys() == [1,5,9,13,16]
        oa.root.child(3).keys() == [13,14,15]
        oa.root.child(4).keys() == [16,20]

        when:
        btree.remove(13)
        btree.remove(16)

        then:
        oa.root.keys() == [1,5,9,14]
        oa.root.child(3).keys() == [14,15,20]
        oa.root.size() == 4
    }

    def "test left leaf remove"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 5, 4)
        def btree = new BplusTree(oa)
        
        when:
        (1..100).each { num -> btree.put(num, num) }
        def upperLeft = oa.root.child(0);
        def farLeft = upperLeft.child(0);
        def nextLeft = upperLeft.child(1);
        
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
        farLeft = upperLeft.child(0)
        
        then:
        upperLeft.size() == 4
        farLeft.keys() == [6,7,8]
        upperLeft.keys() == [6,9,13,17]

        when:
        [6,7,8,9,10,11,12,13,14,15,16,17,18].each { btree.remove(it) }

        then:
        btree.assertValidKeys();
        upperLeft.size() == 3
    }

    def "test full left leaf removal"() {
        setup:
        def num
        def btree
        
        try {
            def oa = new ObjectArray(Integer, Integer, 4)
            btree = new BplusTree(oa)
            (1..1000).each { btree.put(it, it) }

            (1..1000).each {
                num = it
                btree.remove(it)
                btree.assertValidKeys()
                btree.assertOrders()
            }
        }
        catch(Exception e) {
            println "Error when trying to remove ${num}"
            throw e
        }
    }

    def "test full right leaf removal"() {
        setup:
        def num
        def btree
        
        try {
            def oa = new ObjectArray(Integer, Integer, 4)
            btree = new BplusTree(oa)
            (1..1000).each { btree.put(it, it) }

            (1000..1).each {
                num = it

                btree.remove(it)
                btree.assertValidKeys()
                btree.assertOrders()
            }
        }
        catch(Exception e) {
            println "Error when trying to remove ${num}"
            throw e
        }
    }

    def "test specific branch order problem"() {
        setup:
        def list = [81, 48, 68, 6, 59, 92, 183, 58, 206, 188, 182, 205, 147, 232, 104, 54, 33, 133, 162, 227, 169, 150, 132, 57, 154, 193, 105, 51, 142, 187, 236, 220, 241, 226, 216, 230, 161, 74, 15, 248, 224, 31, 61, 174, 113, 197, 234, 157, 237, 102, 185, 198, 121, 1, 96, 65, 17, 228, 233, 165, 11, 219, 247, 101, 118, 252, 127, 19, 67, 20, 95, 214, 3, 138, 78, 29, 190, 240, 170, 99, 100, 254, 75, 85, 28, 32, 246, 223, 125, 128, 23, 70, 195, 222, 167, 56, 194, 124, 156, 229, 27, 171, 130, 129, 93, 140, 72, 112, 41, 122, 238, 215, 217, 110, 64, 163, 35, 62, 136, 7, 88, 210, 60, 12, 143, 200, 24, 177, 243, 189, 158, 90, 211, 175, 10, 201, 131, 135, 39, 250, 115, 86, 2, 97, 212, 245, 30, 82, 38, 120, 106, 16, 103, 42, 53, 191, 235, 25, 151, 159, 148, 242, 21, 164, 14, 87, 166, 73, 225, 9, 91, 34, 111, 244, 98, 71, 192, 52, 155, 77, 152, 43, 49, 253, 196, 40, 36, 45, 66, 83, 181, 108, 209, 144, 94, 26, 116, 168, 145, 139, 141, 199, 251, 126, 22, 76, 204, 79, 203, 117, 50, 153, 4, 137, 172, 207, 13, 80, 0, 63, 179, 8, 178, 208, 47, 37, 249, 46, 119, 160, 186, 55, 176, 44, 18, 149, 218, 213, 255, 114, 69, 221, 107, 184, 146, 123, 231, 180, 89, 5, 109, 134, 202, 239, 173, 84]

        def branchOrder = 5
        def leafOrder = 4
        
        def toAdd, toRemove, index, keyList, sortedKeyList
        try {
            def oa = new ObjectArray(Integer, Integer, branchOrder, leafOrder)
            def btree = new BplusTree(oa)
            list.eachWithIndex { num, i ->
                toAdd = num
                index = i
                
                btree.put(toAdd, toAdd)
                btree.assertValidKeys()
                btree.assertOrders()
                keyList = btree.keyList()
                sortedKeyList = new ArrayList(keyList)
                sortedKeyList.sort()
                
                if(keyList != sortedKeyList) {
                    throw new RuntimeException("list no longer sorted")
                }
            }
        }
        catch(Exception e) {
            println "branchOrder: ${branchOrder}, leafOrder ${leafOrder}"
            println "adding ${toAdd} removing ${toRemove}"
            println "keyList:       ${keyList}"
            println "sortedKeyList: ${sortedKeyList}"
            println "all: ${list}"
            throw e
        }
    }
}
