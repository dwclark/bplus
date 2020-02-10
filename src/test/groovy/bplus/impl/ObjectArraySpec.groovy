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

    def 'test copy'() {
        when:
        def branch = allocateBranch(10)
        branch.copy(5, 0, 2)

        then:
        branch.keys() == [5,6,2,3,4,5,6,7,8,9]

        when:
        branch = allocateBranch(10)
        branch.copy(8, 7, 2)

        then:
        branch.keys() == [0,1,2,3,4,5,6,8,9,9]

        when:
        branch = allocateBranch(10)
        branch.copy(3, 7, 2)

        then:
        branch.keys() == [0,1,2,3,4,5,6,3,4,9]
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

    def 'test leaf split'() {
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
        def oa = new ObjectArray(Integer, Integer, 10);
        def leaf = oa.root.newLeaf();
        def list = [6, 2, 4, 9, 7, 1, 3, 5, 8, 10]

        when:
        list.each { num -> leaf.insert(num, num * 10) }

        then:
        leaf.full
        leaf.keys() == [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        leaf.values() == [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    }
}
