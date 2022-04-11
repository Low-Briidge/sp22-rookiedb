# CS 186 Notes

[TOC]



## Files and Index Management



### Multiple File Organization

* **Heap Files** : HeapFile是一种**保存Page数据的数据结构**。既然都是保存数据，为什么不直接使用文件呢？因为系统文件并不区分文件的内容。处理起来粒度大。而HeapFile和SSTable都能够提供记录级别的管理，从这一点上来说，二者的功能都是相同的，都是为系统提供更细粒度的存储管理。
* **Sorted Files**
* **Clustered Files & Indexes**
* ...



### Heap Files vs Sorted Files

![image-20220405160815429](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220405160815429.png)

* **B:** The number of data blocks

* **R:** Number of records per block

* **D:** Average time to read/write disk block

> 此处涉及到类似 “平均查找长度/时间” 的概念



### 为文件建立索引的简单方式 (B+ 树)

![image-20220405161935622](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220405161935622.png)

#### Step 1: Sort heap file & leave some space

* 先对堆文件排序，每个page内留出额外空间
* 注意：pages在逻辑上是有序的，但在物理存储角度并不一定按照顺序存储
* Do we need “next” pointers to link pages?
  * No. Pages are physically sorted in logical order
  * 但是好像在B+数优化的时候叶子结点形成了双向链表？:question:



![image-20220405161956446](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220405161956446.png)

#### Step 2: Build the index data structure over this

* 为什么不直接用二叉搜索树？
  * 二叉搜索树的度为2，每个节点最多有两个子节点，会形成更深的数，从而需要更多的I/O开销
  * B+树在一般实际应用中，出度d是非常大的数字，通常超过100，因此h非常小（通常不超过3）





![image-20220405163256925](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220405163256925.png)



### Left Key Optimization

可以将上图的非叶子结点的最左侧节点移除

![image-20220405164656960](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220405164656960.png)





## Indices & B+ Tree Refinements



### 建立索引的几种方案

#### 1. By value

将数据(record)直接存储在索引文件中(叶子结点)

**Pros and Cons**

* 更少的 I/O
* 一个文件中只能建立一个索引 (除非复制并在副本中建立其他索引，或者建立一个方案二类型的索引(可能是更多的是非聚集索引？))，~~一张表只能建立一个索引~~ 



#### 2. By reference

叶子结点存储的是数据的指针

* 能对数据建立多个索引 (对多个属性建立索引)
* 如果键 (应该指的是建立索引的那个属性) 重复，比如有相同的名字存在，似乎有点缺陷，只能指向其中一条数据？



#### 3. By list of reference

存储多个指针，或者说指针列表，以解决键重复的问题



#### 一些问题

在**方案2**中，如果修改了某些数据，会不会导致某些索引失效？或者需要重新整理 / 建立索引？或者更加抽象一点

* 修改数据后，如何维护索引？需要重新建立索引吗？:question:
  * 



### Clustered indexes and Unclustered indexes (聚集索引和非聚集索引)

![image-20220408172258769](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220408172258769.png)



#### 聚集索引

大概意思应该指的是：在索引文件中相邻的索引 (在同一节点，或相邻节点)，他们的数据在磁盘分布上也是相邻 (在同一page，或相邻page)

* 因此对于范围查找，聚集索引对比非聚集索引，需要更少的 I/O 次数

#### 非聚集索引

相邻索引其指向的数据是随机分布的，可能相隔十万八千里



对于查找来说我觉得类似 磁盘的 **顺序读写** vs **随机读写**



## 访问磁盘页面

1. seek time
   * 磁头寻道时间
2. rotational delay
   * 盘片旋转延迟
3. transfer time
   * 数据传输时间

减少I/O时间的关键是：

* reduce seek/rotational delays





## B+ Tree

![image-20220409151544730](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220409151544730.png)



### Insert

1. 找到正确的叶子结点
2. 插入数据
   * 如果该叶子结点还有空间，直接插入数据，并排序
   * 否则将该节点拆分为两份 (新建一个节点)，旧节点 $d$ 个元素，新节点 $d + 1$ 个元素
     * 将该节点的父节点指向新节点
     * 将新节点的第一个元素**复制并向上推 (copy up)**
     * 如果父节点也满了，递归地重复 split and **push up**

注意：

* 叶子结点满了是 **复制并向上推**
* 索引节点满了是 **不复制向上推**



# Project Notes



## Project 2: B+ Tree

### Put

### Iterator

个人觉得迭代器为该实验最为精妙的设计 (醍醐灌顶的感觉)。

通过对细节的再次封装，使得上层在调用迭代器的时候无需考虑下层的各种细节。

在实验中，最上层的根节点需要调用最下层叶子节点的迭代器以访问所有数据。LeafNode提供了 `List<RecordId> rids` 的迭代器，但是这个迭代器只能访问该叶子节点，访问本层所有叶子节点的话需要一些额外的操作技巧 (Trick：通过兄弟节点指针访问右边的一个节点)，而如果把这个操作直接放进**上层**可能最后难以避免屎山的形成。那该怎么办？

最精妙的想法 ：

* 通过封装后提供一个 **更加抽象** 的接口，供上层调用
* 上层并不需要关心细节，能用就行，更准确的是 **用的舒服**

该实验设计者的操作：

在 `BPlusTree` 类中设计了一个内部类 `BPlusTreeIterator` implements `Iterator` 接口，重写 `hasNext()` 和 `next()` 方法，关键就在这两个方法中实现了迭代器访问整层叶子节点的 **细节** ，封装之后只需调用该内部类获得迭代器即可满足要求，无需关心内部细节。

最关键的是：

* 调用这种稍微复杂的迭代器就像调用 `List` 的 `iterator()` 一样 **无感**
* 下一层抽象后供本层调用，在本层只需关心本层的实现内容，本层再进行一波抽象以提供更加强大、完善的功能供上层调用。如此，将一些复杂的功能分为多个阶段，每一个阶段都比之前实现地更多一点、更完善一点、更强大一点，最终实现整个复杂的系统。这一点在B+树的**节点设计**上体现得淋漓尽致！
* (但是我的腰现在巨酸无比，择日再议...(逃 --- 2022.4.11 22:33



#### B+树 迭代器的封装

```java
private class BPlusTreeIterator implements Iterator<RecordId> {
        // TODO(proj2): Add whatever fields and constructors you want here.
        LeafNode nextNode; // = root.getLeftmostLeaf();
        Iterator<RecordId> nextIter; //= leftNode.scanAll();

        public BPlusTreeIterator(LeafNode leftNode, Iterator<RecordId> leftNodeIter) {
            this.nextNode = leftNode;
            this.nextIter = leftNodeIter;
        }


        @Override
        public boolean hasNext() {
            // TODO(proj2): implement
            if (nextIter.hasNext() || nextNode.getRightSibling().isPresent()) return true;
            return false;
        }

        @Override
        public RecordId next() {
            // TODO(proj2): implement
            if (hasNext()) {
                if (!nextIter.hasNext()) {
                    nextNode = nextNode.getRightSibling().get();
                    nextIter = nextNode.scanAll();
                }
                return nextIter.next();
            }
            throw new NoSuchElementException();
        }
    }
```



