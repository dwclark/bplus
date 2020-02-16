package bplus.impl

import spock.lang.*
import bplus.*

class ObjectArraySpec extends Specification {

    def "test creation, fill, and search for branch"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 128)
        def branch = oa.root.newBranch()

        when:
        def res = branch.search(10)

        then:
        res == -1

        when:
        (0..10).each { num ->
            branch.sizeUp(1).put(num, null, num * 10, null)
        }

        then:
        branch.search(10) == 1
        branch.search(50) == 5
        branch.search(100) == 10
        Node.insertIndex(branch.search(110)) == 11
    }

    def "test creation, fill, and search for leaf"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 128)
        def leaf = oa.root.newLeaf()

        when:
        def res = leaf.search(10)

        then:
        res == -1

        when:
        (0..10).each { num ->
            leaf.sizeUp(1).put(num, num * 10, num * 20)
        }

        then:
        leaf.search(10) == 1
        leaf.search(50) == 5
        leaf.search(100) == 10
        Node.insertIndex(leaf.search(110)) == 11
    }

    private allocateBranch(final int size) {
        def oa = new ObjectArray(Integer, Integer, size)
        def branch = oa.root.newBranch()
        (0..<size).each { i -> branch.sizeUp(1).put(i, null, i, null) }
        return branch
    }

    private makeLeaf(final ObjectArray oa, final List<Integer> list) {
        def leaf = oa.root.newLeaf();
        list.each { leaf.insert(it, it) }
        return leaf;
    }

    private makeBranch(final ObjectArray oa, final List<Node> nodes) {
        def branch = oa.root.newBranch()
        for(int i = 0; i < nodes.size() - 1; ++i) {
            branch.sizeUp(1)
            branch.put(i, nodes[i], nodes[i+1].minKey, nodes[i+1])
        }

        return branch
    }

    def 'test left shift'() {
        when:
        def branch = allocateBranch(4)
        branch.shiftLeft(3, 1).sizeDown(1)
        
        then:
        branch.keys() == [ 0, 1, 3 ];

        when:
        branch = allocateBranch(4)
        branch.shiftLeft(2, 2).sizeDown(2);

        then:
        branch.keys() == [2, 3]

        when:
        branch = allocateBranch(4)
        branch.shiftLeft(1, 1).sizeDown(1);

        then:
        branch.keys() == [1,2,3]

    }

    def 'test right shift'() {
        when:
        def branch = allocateBranch(4)
        branch.shiftRight(0, 1);
        branch.put(0, null, 100, null);
        
        then:
        branch.keys() == [ 100, 0, 1, 2 ];

        when:
        branch = allocateBranch(4)
        branch.shiftRight(0, 3);
        branch.put(0, null, 100, null);
        branch.put(1, null, 100, null);
        branch.put(2, null, 100, null);

        then:
        branch.keys() == [100, 100, 100, 0]

        when:
        branch = allocateBranch(4)
        branch.shiftRight(2, 1);

        then:
        branch.keys() == [0,1,2,2]

        when:
        branch = allocateBranch(4)
        branch.shiftRight(0, 1)

        then:
        branch.keys() == [0,0,1,2]
    }

    def 'test branch copy'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def leaves = [makeLeaf(oa, [1,2,3,4]),
                      makeLeaf(oa, [10,11,12,13]),
                      makeLeaf(oa, [20,21,22,23]),
                      makeLeaf(oa, [30,31,32,33]),
                      makeLeaf(oa, [40,41,42,43])]
        when:
        def branch = makeBranch(oa, leaves)

        then:
        branch.size() == 4
        branch.keys() == [10,20,30,40]

        when:
        def rightCopy = oa.root.newBranch()
        rightCopy.size(2)
        rightCopy.copy(2, branch, 0, 2)

        then:
        rightCopy.size() == 2
        rightCopy.keys() == [30,40]
        rightCopy.left(0).keys() == [20,21,22,23]
        rightCopy.left(1).keys() == [30,31,32,33]
        rightCopy.right(1).keys() == [40,41,42,43]

        when:
        def leftCopy = oa.root.newBranch()
        leftCopy.size(1)
        leftCopy.copy(0, branch, 0, 1)

        then:
        leftCopy.size() == 1
        leftCopy.keys() == [10]
        leftCopy.left(0).keys() == [1,2,3,4]
        leftCopy.right(0).keys() == [10,11,12,13]
    }

    def 'test leaf copy'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 128)
        def leaf = oa.root.newLeaf()

        when:
        (0..<10).each { num ->
            leaf.sizeUp(1).put(num, num * 10, num * 20)
        }

        leaf.copy(5, 0, 5);
        leaf.sizeDown(5);

        then:
        leaf.toMap() == [50:100, 60:120, 70:140, 80:160, 90:180]
        leaf.values() == [100,120,140,160,180]
    }

    def 'test leaf copy 2'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 10)
        def leaf = oa.root.newLeaf()
        (0..<10).each { num ->
            leaf.sizeUp(1).put(num, num * 10, num * 20)
        }

        def leftLeaf = oa.root.newLeaf();

        when:
        leftLeaf.copy(0, leaf, 0, 5).sizeUp(5);
        leaf.copy(5, 0, 5).sizeDown(5);

        then:
        leftLeaf.size() == 5
        leaf.size() == 5
        leftLeaf.toMap() == [0:0, 10:20, 20:40, 30:60, 40:80]
        leaf.toMap() == [50:100, 60:120, 70:140, 80:160, 90:180]

    }

    def 'test leaf insert'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 10)
        def leaf = oa.root.newLeaf();
        def list = [6, 2, 4, 9, 7, 1, 3, 5, 8, 10]

        when:
        list.each { num -> leaf.insert(num, num * 10) }

        then:
        leaf.full
        leaf.keys() == [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        leaf.values() == [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    }

    def 'test leaf split'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def make = this.&makeLeaf.curry(oa)
        
        when:
        def leaf = make([1,2,3,4])
        def right = leaf.split(5, 5)

        then:
        leaf.size() == 2
        right.size() == 3
        leaf.keys() == [1,2]
        right.keys() == [3,4,5]

        when:
        leaf = make([1,2,4,5])
        right = leaf.split(3,3)

        then:
        leaf.size() == 3
        right.size() == 2
        leaf.keys() == [1,2,3]
        right.keys() == [4,5]

        when:
        leaf = make([2,3,4,5])
        right = leaf.split(1,1)

        then:
        leaf.size() == 3
        right.size() == 2
        leaf.keys() == [1,2,3]
        right.keys() == [4,5]
    }

    def 'test branch insert'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def make = this.&makeLeaf.curry(oa)
        
        def branch = oa.root.newBranch()
        def ary10 = make([10,11,12,13])
        def ary100 = make([100,101,102,103])
        branch.sizeUp(1);
        branch.put(0, ary10, 100, ary100)

        when:
        def ary1000 = make([1000,1001,1002,1003])
        branch.insert(ary1000);

        then:
        branch.keys() == [100,1000]

        when:
        def ary1 = make([1,2,3,4])
        branch.insert(ary1)

        then:
        branch.keys() == [10,100,1000]

        when:
        def ary50 = make([50,51,52,53])
        branch.insert(ary50)

        then:
        branch.keys() == [10,50,100,1000]
        branch.left(0).keys() == [1,2,3,4]
        branch.left(1).keys() == [10,11,12,13]
        branch.left(2).keys() == [50,51,52,53]
        branch.left(3).keys() == [100,101,102,103]
        branch.right(3).keys() == [1000,1001,1002,1003]
    }

    def 'test branch split odd'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 3)
        def make = this.&makeLeaf.curry(oa)

        def makeBranch = { ->
            def branch = oa.root.newBranch()
            branch.size(3)
            branch.put(0, make([10,11,12]), 100, make([100,101,102]))
            branch.put(1, 1000, make([1000,1001,1002]))
            branch.put(2, 10_000, make([10_000, 10_001, 10_002]))
            return branch
        }

        when:
        def branch = makeBranch()
        def newRight = branch.split(make([1,2,3]))

        then:
        branch.keys() == [10,100]
        branch.left(0).keys() == [1,2,3]
        branch.left(1).keys() == [10,11,12]
        branch.right(1).keys() == [100,101,102]
        branch.size() == 2
        
        newRight.keys() == [10_000]
        newRight.size() == 1
        newRight.left(0).keys() == [1000,1001,1002]
        newRight.right(0).keys() == [10_000,10_001,10_002]

        when:
        branch = makeBranch()
        newRight = branch.split(make([15,16,17]))

        then:
        branch.keys() == [15,100]
        branch.left(0).keys() == [10,11,12]
        branch.left(1).keys() == [15,16,17]
        branch.right(1).keys() == [100,101,102]
        branch.size() == 2
        
        newRight.keys() == [10_000]
        newRight.size() == 1
        newRight.left(0).keys() == [1000,1001,1002]
        newRight.right(0).keys() == [10_000,10_001,10_002]

        when:
        branch = makeBranch()
        newRight = branch.split(make([115,116,117]))

        then:
        branch.keys() == [100,115]
        branch.left(0).keys() == [10,11,12]
        branch.left(1).keys() == [100,101,102]
        branch.right(1).keys() == [115,116,117]
        branch.size() == 2
        
        newRight.keys() == [10_000]
        newRight.size() == 1
        newRight.left(0).keys() == [1000,1001,1002]
        newRight.right(0).keys() == [10_000,10_001,10_002]

        when:
        branch = makeBranch()
        newRight = branch.split(make([1100,1101,1102]))

        then:
        branch.keys() == [100]
        branch.left(0).keys() == [10,11,12]
        branch.right(0).keys() == [100,101,102]
        branch.size() == 1
        
        newRight.keys() == [1100,10_000]
        newRight.size() == 2
        newRight.left(0).keys() == [1000,1001,1002]
        newRight.left(1).keys() == [1100,1101,1102]
        newRight.right(1).keys() == [10_000,10_001,10_002]

        when:
        branch = makeBranch()
        newRight = branch.split(make([11_000,11_001,11_002]))

        then:
        branch.keys() == [100]
        branch.left(0).keys() == [10,11,12]
        branch.right(0).keys() == [100,101,102]
        branch.size() == 1
        
        newRight.keys() == [10_000,11_000]
        newRight.size() == 2
        newRight.left(0).keys() == [1000,1001,1002]
        newRight.left(1).keys() == [10_000,10_001,10_002]
        newRight.right(1).keys() == [11_000,11_001,11_002]
    }

    def 'test branch split even'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 4)
        def make = this.&makeBranch.curry(oa)
        def leaves = [makeLeaf(oa, [10,11,12,13]),
                      makeLeaf(oa, [100,101,102,103]),
                      makeLeaf(oa, [1000,1001,1002,1003]),
                      makeLeaf(oa, [10_000, 10_001, 10_002,10_003]),
                      makeLeaf(oa, [100_000, 100_001, 100_002,100_003])]
        
        when:
        def branch = makeBranch(oa, leaves)
        def newRight = branch.split(makeLeaf(oa, [1,2,3,4]))

        then:
        branch.size() == 2
        branch.keys() == [10,100]
        branch.left(0).keys()== [1,2,3,4]
        branch.left(1).keys() == [10,11,12,13]
        branch.right(1).keys() == [100,101,102,103]
        newRight.size() == 2
        newRight.left(0).keys() == [1000,1001,1002,1003]
        newRight.left(1).keys() == [10_000, 10_001, 10_002,10_003]
        newRight.right(1).keys() == [100_000, 100_001, 100_002,100_003]
        newRight.keys()== [10_000,100_000]

        when:
        branch = makeBranch(oa, leaves);
        newRight = branch.split(makeLeaf(oa, [5000,5001,5002,5003]))

        then:
        branch.size() == 2
        branch.keys() == [100,1000]
        branch.left(0).keys()== [10,11,12,13]
        branch.left(1).keys() == [100,101,102,103]
        branch.right(1).keys() == [1000,1001,1002,1003]

        newRight.size() == 2
        newRight.left(0).keys() == [5000,5001,5002,5003]
        newRight.left(1).keys() == [10_000, 10_001, 10_002,10_003]
        newRight.right(1).keys() == [100_000, 100_001, 100_002,100_003]
        newRight.keys()== [10_000,100_000]

        when:
        branch = makeBranch(oa, leaves);
        newRight = branch.split(makeLeaf(oa, [15_000,15_001,15_002,15_003]))

        then:
        branch.size() == 2
        branch.keys() == [100,1000]
        branch.left(0).keys()== [10,11,12,13]
        branch.left(1).keys() == [100,101,102,103]
        branch.right(1).keys() == [1000,1001,1002,1003]

        newRight.size() == 2
        newRight.left(0).keys() == [10_000, 10_001, 10_002,10_003]
        newRight.left(1).keys() == [15_000, 15_001, 15_002,15_003]
        newRight.right(1).keys() == [100_000, 100_001, 100_002,100_003]
        newRight.keys()== [15_000,100_000]
    }
}
