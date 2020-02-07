package bplus.impl

import spock.lang.*
import bplus.*

class ObjectArraySpec extends Specification {

    def "test creation, fill, and search for branch"() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 128)
        def branch = oa.newBranch()

        when:
        def res = branch.search(10)

        then:
        res == -1

        when:
        (0..10).each { num ->
            branch.put(num, null, num * 10, null).sizeUp(1)
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
        def leaf = oa.newLeaf()

        when:
        def res = leaf.search(10)

        then:
        res == -1

        when:
        (0..10).each { num ->
            leaf.put(num, num * 10, num * 20).sizeUp(1)
        }

        then:
        leaf.search(10) == 1
        leaf.search(50) == 5
        leaf.search(100) == 10
        Node.insertIndex(leaf.search(110)) == 11
    }

    def 'test left shift'() {
        setup:
        def oa = new ObjectArray(Integer, Integer, 3)
        def branch = oa.newBranch()
        (0..2).each { i -> branch.put(i, null, i, null).sizeUp(1) }

        when:
        branch.shiftLeft(1, 1)
        branch.sizeDown(1)
        
        then:
        branch.keys() == [ 1, 2 ];
    }

}
