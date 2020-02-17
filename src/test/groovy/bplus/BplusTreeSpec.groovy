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

    @Ignore
    def "test man random"() {
        setup:
        def max = 512
        def list = new ArrayList(max)
        (0..<max).each { list.add(it) }
        Collections.shuffle(list)

        def toAdd, index
        try {
            def oa = new ObjectArray(Integer, Integer, 8)
            def btree = new BplusTree(oa)
            list.eachWithIndex { num, i ->
                toAdd = num
                index = i                
                btree.put(toAdd, toAdd)
            }
        }
        catch(Exception e) {
            println "adding ${toAdd} at index ${index}"
            println "list is: ${list}"
            throw e
        }
    }
    
    @Ignore
    def "test specific insert sequence"() {
        setup:
        def list = [141, 202, 320, 63, 121, 164, 344, 474, 8, 377, 151, 445, 386, 465, 276, 255, 464, 55, 467, 270, 15, 207, 340, 364, 500, 391, 459, 107, 341, 495, 98, 195, 229, 89, 62, 262, 203, 396, 28, 406, 411, 473, 161, 298, 259, 466, 196, 413, 449, 108, 349, 511, 343, 504, 208, 122, 268, 280, 39, 261, 339, 324, 311, 176, 3, 369, 155, 388, 110, 174, 0, 30, 206, 415, 236, 265, 444, 177, 153, 394, 257, 499, 160, 401, 346, 175, 446, 422, 289, 387, 407, 383, 215, 452, 420, 41, 74, 83, 33, 359, 416, 250, 209, 328, 225, 382, 81, 438, 264, 14, 58, 69, 78, 105, 333, 425, 367, 16, 53, 158, 92, 350, 184, 216, 237, 68, 49, 283, 381, 443, 242, 434, 244, 263, 267, 477, 104, 488, 125, 475, 64, 400, 304, 204, 507, 77, 95, 149, 200, 26, 484, 32, 297, 460, 247, 13, 371, 334, 23, 137, 87, 45, 470, 497, 337, 113, 101, 274, 418, 150, 235, 82, 146, 363, 275, 172, 336, 325, 159, 492, 335, 296, 233, 482, 123, 76, 187, 295, 303, 183, 27, 46, 494, 139, 156, 234, 269, 29, 433, 168, 166, 509, 218, 221, 56, 278, 79, 502, 181, 378, 106, 398, 42, 384, 232, 330, 65, 210, 40, 306, 219, 430, 52, 38, 402, 455, 389, 314, 212, 312, 354, 313, 317, 361, 214, 448, 412, 436, 140, 7, 190, 266, 453, 148, 456, 357, 323, 147, 253, 302, 230, 469, 54, 9, 288, 342, 365, 115, 440, 163, 503, 254, 180, 252, 293, 327, 100, 501, 114, 31, 329, 21, 143, 239, 197, 193, 17, 131, 332, 67, 127, 399, 117, 454, 506, 300, 395, 468, 43, 90, 424, 279, 182, 224, 379, 5, 243, 392, 10, 238, 251, 429, 169, 321, 372, 358, 119, 167, 408, 435, 194, 426, 198, 57, 272, 472, 246, 72, 414, 432, 136, 326, 374, 360, 188, 310, 213, 128, 48, 315, 138, 287, 111, 226, 170, 129, 50, 245, 291, 152, 73, 316, 124, 485, 490, 437, 157, 481, 4, 393, 93, 421, 36, 248, 11, 362, 451, 61, 223, 281, 199, 35, 132, 308, 431, 12, 142, 410, 19, 366, 331, 60, 1, 103, 478, 189, 428, 345, 319, 162, 211, 318, 487, 348, 442, 22, 463, 423, 299, 427, 368, 20, 258, 186, 220, 260, 116, 154, 471, 405, 491, 462, 2, 34, 277, 192, 47, 309, 441, 99, 102, 458, 66, 130, 480, 353, 447, 370, 249, 307, 290, 18, 347, 205, 6, 390, 144, 179, 227, 461, 380, 75, 118, 292, 273, 134, 80, 373, 286, 457, 71, 352, 217, 88, 201, 479, 271, 498, 356, 375, 85, 97, 489, 351, 191, 486, 419, 508, 256, 493, 84, 24, 240, 403, 510, 305, 231, 385, 126, 439, 120, 285, 241, 185, 222, 397, 173, 59, 51, 44, 91, 133, 282, 483, 70, 284, 376, 496, 404, 171, 322, 409, 135, 294, 37, 228, 96, 450, 109, 178, 145, 112, 505, 165, 25, 476, 94, 355, 417, 338, 301, 86]

        def toAdd, index, keyList, sortedKeyList
        try {
            def oa = new ObjectArray(Integer, Integer, 8)
            def btree = new BplusTree(oa)
            list.eachWithIndex { num, i ->
                toAdd = num
                index = i
                if(index == 131) {
                    println "At index 131"
                }
                
                btree.put(toAdd, toAdd)
                keyList = btree.keyList()
                sortedKeyList = new ArrayList(keyList)
                sortedKeyList.sort()
                if(keyList != sortedKeyList) {
                    throw new RuntimeException("list no longer sorted")
                }
            }
        }
        catch(Exception e) {
            println "adding ${toAdd} at index ${index}"
            println "keyList: ${keyList}"
            println "sortedKeyList: ${sortedKeyList}"
            throw e
        }
    }

    def "test bad insert ordering"() {
        setup:
        def list = [141, 202, 320, 63, 121, 164, 344, 474, 8, 377, 151, 445, 386, 465, 276, 255, 464, 55, 467, 270, 15, 207, 340, 364, 500, 391, 459, 107]

        def toAdd, index, keyList, sortedKeyList
        try {
            def oa = new ObjectArray(Integer, Integer, 8)
            def btree = new BplusTree(oa)
            list.eachWithIndex { num, i ->
                toAdd = num
                index = i
                if(num == 107) {
                    println "At 107"
                }
                
                btree.put(toAdd, toAdd)
                keyList = btree.keyList()
                sortedKeyList = new ArrayList(keyList)
                sortedKeyList.sort()
                if(keyList != sortedKeyList) {
                    throw new RuntimeException("list no longer sorted")
                }
            }
        }
        catch(Exception e) {
            println "adding ${toAdd} at index ${index}"
            println "keyList: ${keyList}"
            println "sortedKeyList: ${sortedKeyList}"
            throw e
        }
    }
}
