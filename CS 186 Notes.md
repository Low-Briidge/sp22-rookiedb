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



## Project 3: Joins and Query Optimization

### 写在前面

本章内容属于经典的 **项目驱动学习系列** :heavy_check_mark:

**首先**，对于该实验，我感觉对各种Joins的理解，或者说对这两节课内容的理解 **有点虚**，不是有点，感觉 **肥肠虚**。所有的Task都不能一次bug free，一个知识点经常要看好几遍

**其次**，出现bug后，然后继续靠玄学删删改改企图靠那点可怜的运气暴力通过测试样例。这种下意识的本能操作在写bug的时候我能很明显的感觉到，但是怎么就改不了捏...这是个很严重的问题

**还有**，阅读源码相关的问题



### Part 1: Join Algorithms



#### Task 1: Nested Loop Joins (fucked 10 hours)

**Simple Nested Loop Joins**

![SNLJ](https://3248067225-files.gitbook.io/~/files/v0/b/gitbook-legacy-files/o/assets%2F-MFVQnrLlCBowpNWJo1E%2Fsync%2Fa73c972f30b1f1f9a4d3338fa7994c85c980d74a.gif?generation=1601331281677102&alt=media)

为啥不用两层for循环？难道是难以扩展，多层循环后代码会变得非常复杂？

这让我想起了LeetCode上的9x9数组判断是否有效，也是用了很多重循环...

```java
private Record fetchNextRecord() {
    if (leftRecord == null) {
        // The left source was empty, nothing to fetch
        return null;
    }
    while(true) {
        if (this.rightSourceIterator.hasNext()) {
            // there's a next right record, join it if there's a match
            Record rightRecord = rightSourceIterator.next();
            if (compare(leftRecord, rightRecord) == 0) {
                return leftRecord.concat(rightRecord);
            }
        } else if (leftSourceIterator.hasNext()){
            // there's no more right records but there's still left
            // records. Advance left and reset right
            this.leftRecord = leftSourceIterator.next();
            this.rightSourceIterator.reset();
        } else {
            // if you're here then there are no more records to fetch
            return null;
        }
    }
}
```



**Block Nested Loop Joins**

* 针对PNLJ(page nested loop joins)的优化，完全利用剩余的缓冲区($Block = (B - 2)*Pages$)
* 一次读入1 Block(B - 2 bufferPages)大小的记录，一个缓冲区用于读入另一个表的**一页**记录，最后一个缓冲区作输出缓冲

![bnlj-final](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\bnlj-final.gif)

下面四种情况对应BNLJ的四种判断条件，但是为什么一个 `while` 能实现四重循环???(我先研究一下SNLJ的简化版本代码)

> Try to think about what should be advanced and what should be reset in each case. As a reminder:
>
> - Case 1: The right page iterator has a value to yield
> - Case 2: The right page iterator doesn't have a value to yield but the left block iterator does
> - Case 3: Neither the right page nor left block iterators have values to yield, but there's more right pages
> - Case 4: Neither right page nor left block iterators have values nor are there more right pages, but there are still left blocks

![spaces_-MFVQnrLlCBowpNWJo1E_uploads_git-blob-789697883f5e7f4e6c782c5259ed5dc635310d4f_cases (1) (1)](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\spaces_-MFVQnrLlCBowpNWJo1E_uploads_git-blob-789697883f5e7f4e6c782c5259ed5dc635310d4f_cases (1) (1).png)

```java
if (this.leftRecord != null) {
    while (true) {
        	// Case 1
        if (this.rightPageIterator.hasNext()) {
            Record rightRecord = rightPageIterator.next();
            if (compare(leftRecord, rightRecord) == 0) {
                return leftRecord.concat(rightRecord);
            }

            // Case 2
        } else if (this.leftBlockIterator.hasNext()) {
            this.leftRecord = leftBlockIterator.next();
            this.rightPageIterator.reset();

            // Case 3
        } else if (rightSourceIterator.hasNext()) {
            this.fetchNextRightPage();
            this.leftBlockIterator.reset();
            if (this.leftBlockIterator.hasNext())
                this.leftRecord = this.leftBlockIterator.next();

            // Case 4
        } else if (this.leftSourceIterator.hasNext()) {
            this.fetchNextLeftBlock();
            this.rightSourceIterator.reset();
            this.fetchNextRightPage();
        } else {
            return null;
        }
    }
}
```



**一些重要的类 / 方法**

* BacktrackingIterator (接口，实现类为Array....)

可**回溯**的迭代器，通过设置标记后可使位置重置为标记所在处

```java
/*[1,2,3]:
 *
 * BackTrackingIterator<Integer> iter = new BackTrackingIteratorImplementation();
 * iter.next();     // returns 1
 * iter.next();     // returns 2
 * iter.markPrev(); // marks the previously returned value, 2
 * iter.next();     // returns 3
 * iter.hasNext();  // returns false
 * iter.reset();    // reset to the marked value (line 5)
 * iter.hasNext();  // returns true
 * iter.next();     // returns 2
 * iter.markNext(); // mark the value to be returned next, 3
 * iter.next();     // returns 3
 * iter.hasNext();  // returns false
 * iter.reset();    // reset to the marked value (line 11)
 * iter.hasNext();  // returns true
 * iter.next();     // returns 3
 */
```



* getBlockIterator (静态方法)

```java
/**
     * @param records an iterator of records
     * @param schema the schema of the records yielded from `records`
     * @param maxPages the maximum number of pages worth of records to consume
     * @return This method will consume up to `maxPages` pages of records from
     * `records` (advancing it in the process) and return a backtracking
     * iterator over those records. Setting maxPages to 1 will result in an
     * iterator over a single page of records.
     */
    public static BacktrackingIterator<Record> getBlockIterator(Iterator<Record> records, Schema schema, int maxPages) {
        int recordsPerPage = Table.computeNumRecordsPerPage(PageDirectory.EFFECTIVE_PAGE_SIZE, schema);
        int maxRecords = recordsPerPage * maxPages;
        List<Record> blockRecords = new ArrayList<>();
        for (int i = 0; i < maxRecords && records.hasNext(); i++) {
            blockRecords.add(records.next());
        }
        return new ArrayBacktrackingIterator<>(blockRecords);
    }
```





**错误点 (Bug)**

1. leftPage reset() but not next() **精准命中...**

   > The mistake was that the leftRecord wasn't reset back to the first record in the left page. Many students will remember to call `leftIterator.reset()`, but forget to do `leftRecord = leftIterator.next()` afterwards, causing this issue.

   ![image-20220423105209982](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220423105209982.png)



#### Task 2: Hash Joins (fucked 5+ hours)

* Simple Hash Join
* Grace Hash Join



#### Task 3: External Sort (fucked 2 hours)

这一部分有很多可以说，够我喝一壶的了...查了不下三五十个相关的页面，从外排序一直查到海量数据相关的面试题。这里先放几个关键词，择日再议(逃...

* 归并外**IO优化**
* **败者树**的内部归并优化

**What's run (not wrong...) with you ?**

根据 `Run.java` 内对该类的注释

> A run represents a section of space on disk that we can append records to or
>
> read from. This is useful for external sorting to store records while we
>
> aren't using them and free up memory. Automatically buffers reads and writes
>
> to minimize I/Os incurred.

Run 应该是外排序归并过程的一个(输入或输出)单位(有图有真相，上图！

![image-20220425154345907](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220425154345907.png)

* `public Run sortRun(Iterator<Record> records)` should sort the passed in data using an in-memory sort (Pass 0 of external mergesort).
  * 这一部分，从磁盘读入尽可能多的页到内存，进行一个内排序。因此直接读入 $num = numbuffer$ 数量的页即可，然后用内置排序后直接输出到磁盘

* `public Run mergeSortedRuns(List<Run> runs)` should return a new run given a list of sorted runs.
  * 对多个已排序的Run进行归并，返回一个排好序的Run
  * 官方推荐用 **堆(优先队列)** 进行归并优化

* `public List<Run> mergePass(List<Run> runs)` should perform a single merge pass of external mergesort, given a list of all the sorted runs from the previous pass.
  * 结合函数名、函数签名再加上注释，看样子是很多的Run归并，返回一组Run
  * 我这里就直接 $size = numBuffers - 1$ 一组进行 `mergeSortedRuns()` 

* `public Run mergeSortedRuns(List<Run> runs)` should run external mergesort from start to finish, and return the final run with the sorted data
  * 该函数把前面的操作组合起来以实现一个完整的 **External Sort**:white_check_mark:

#### Task 4: Sorted Merge Join (3 hours)

**Pseudo Code**

![image-20220425133703087](C:\Users\low19\AppData\Roaming\Typora\typora-user-images\image-20220425133703087.png)

伪代码删除了所有的边界判断(如：Record的非空判断)。这么一想，这种模式似乎提供了一种很好的代码编写思路

* 先确定大致的解决框架
* 然后填充细节(比如各种边界判断等...)

## Project 4: Concurrency

### Part 1: Queuing

#### Task 2: LockManager

```java
public void acquireAndRelease(TransactionContext transaction,
                              ResourceName name,
                              LockType lockType, 
                              List<ResourceName> releaseNames)
```

* 事务获取锁后释放已持有资源上的锁，原子地实现
* 获取的锁类型与共享资源上其他事务的锁冲突，阻塞，并且将该事务至于该资源的队列头



```java
public void acquire(TransactionContext transaction, 
                    ResourceName name,
                    LockType lockType)
```

* 尝试在该资源上获取锁，不兼容的话阻塞并加入队列尾部



```java
public void release(TransactionContext transaction, ResourceName name)
```

* 释放锁
