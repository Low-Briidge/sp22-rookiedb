package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;

import java.util.*;

/**
 * LockManager maintains the bookkeeping for what transactions have what locks
 * on what resources and handles queuing logic. The lock manager should generally
 * NOT be used directly: instead, code should call methods of LockContext to
 * acquire/release/promote/escalate locks.
 *
 * The LockManager is primarily concerned with the mappings between
 * transactions, resources, and locks, and does not concern itself with multiple
 * levels of granularity. Multigranularity is handled by LockContext instead.
 *
 * Each resource the lock manager manages has its own queue of LockRequest
 * objects representing a request to acquire (or promote/acquire-and-release) a
 * lock that could not be satisfied at the time. This queue should be processed
 * every time a lock on that resource gets released, starting from the first
 * request, and going in order until a request cannot be satisfied. Requests
 * taken off the queue should be treated as if that transaction had made the
 * request right after the resource was released in absence of a queue (i.e.
 * removing a request by T1 to acquire X(db) should be treated as if T1 had just
 * requested X(db) and there were no queue on db: T1 should be given the X lock
 * on db, and put in an unblocked state via Transaction#unblock).
 *
 * This does mean that in the case of:
 *    queue: S(A) X(A) S(A)
 * only the first request should be removed from the queue when the queue is
 * processed.
 */
public class LockManager {
    // transactionLocks is a mapping from transaction number to a list of lock
    // objects held by that transaction.
    private Map<Long, List<Lock>> transactionLocks = new HashMap<>();

    // resourceEntries is a mapping from resource names to a ResourceEntry
    // object, which contains a list of Locks on the object, as well as a
    // queue for requests on that resource.
    private Map<ResourceName, ResourceEntry> resourceEntries = new HashMap<>();

    // A ResourceEntry contains the list of locks on a resource, as well as
    // the queue for requests for locks on the resource.
    private class ResourceEntry {
        // List of currently granted locks on the resource.
        List<Lock> locks = new ArrayList<>();
        // Queue for yet-to-be-satisfied lock requests on this resource.
        Deque<LockRequest> waitingQueue = new ArrayDeque<>();

        // Below are a list of helper methods we suggest you implement.
        // You're free to modify their type signatures, delete, or ignore them.

        /**
         * Check if `lockType` is compatible with preexisting locks. Allows
         * conflicts for locks held by transaction with id `except`, which is
         * useful when a transaction tries to replace a lock it already has on
         * the resource.
         */
        public boolean checkCompatible(LockType lockType, long except) {
            // TODO(proj4_part1): implement
            for (Lock lock : locks) {
                if (lock.transactionNum == except) continue;
                if (!LockType.compatible(lockType, lock.lockType)) return false;
            }
            return true;
        }

        /**
         * Gives the transaction the lock `lock`. Assumes that the lock is
         * compatible. Updates lock on resource if the transaction already has a
         * lock.
         */
        public void grantOrUpdateLock(Lock lock) {
            // TODO(proj4_part1): implement
            if (getTransactionLockType(lock.transactionNum) != LockType.NL) {
                int index;
                for (index = 0; index < locks.size(); index++) {
                    Lock lock1 = locks.get(index);
                    if (lock.transactionNum.equals(lock1.transactionNum)) {
                        locks.add(index, lock);
                        locks.remove(lock1);
                        return;
                    }
                }
            }
            locks.add(lock);
            return;
        }

        /**
         * Releases the lock `lock` and processes the queue. Assumes that the
         * lock has been granted before.
         */
        public void releaseLock(Lock lock) {
            // TODO(proj4_part1): implement
            locks.removeIf(lock::equals);
            processQueue();
            return;
        }

        /**
         * Adds `request` to the front of the queue if addFront is true, or to
         * the end otherwise.
         */
        public void addToQueue(LockRequest request, boolean addFront) {
            // TODO(proj4_part1): implement
            if (addFront) waitingQueue.addFirst(request);
            else waitingQueue.addLast(request);
            return;
        }

        /**
         * Grant locks to requests from front to back of the queue, stopping
         * when the next lock cannot be granted. Once a request is completely
         * granted, the transaction that made the request can be unblocked.
         */
        private void processQueue() {
            Iterator<LockRequest> requests = waitingQueue.iterator();

            // TODO(proj4_part1): implement
            while (requests.hasNext()) {
                LockRequest lockRequest = requests.next();
                if (!checkCompatible(lockRequest.lock.lockType, lockRequest.transaction.getTransNum()))
                    return;
                waitingQueue.pollFirst();
                grantOrUpdateLock(lockRequest.lock);
                lockRequest.transaction.unblock();
            }
            return;
        }

        /**
         * Gets the type of lock `transaction` has on this resource.
         */
        public LockType getTransactionLockType(long transaction) {
            // TODO(proj4_part1): implement
            for (Lock lock : locks) {
                if (lock.transactionNum == transaction)
                    return lock.lockType;
            }
            return LockType.NL;
        }

        @Override
        public String toString() {
            return "Active Locks: " + Arrays.toString(this.locks.toArray()) +
                    ", Queue: " + Arrays.toString(this.waitingQueue.toArray());
        }
    }

    // You should not modify or use this directly.
    private Map<String, LockContext> contexts = new HashMap<>();

    /**
     * Helper method to fetch the resourceEntry corresponding to `name`.
     * Inserts a new (empty) resourceEntry into the map if no entry exists yet.
     */
    private ResourceEntry getResourceEntry(ResourceName name) {
        resourceEntries.putIfAbsent(name, new ResourceEntry());
        return resourceEntries.get(name);
    }

    /**
     * Acquire a `lockType` lock on `name`, for transaction `transaction`, and
     * releases all locks on `releaseNames` held by the transaction after
     * acquiring the lock in one atomic action.
     *
     * Error checking must be done before any locks are acquired or released. If
     * the new lock is not compatible with another transaction's lock on the
     * resource, the transaction is blocked and the request is placed at the
     * FRONT of the resource's queue.
     *
     * Locks on `releaseNames` should be released only after the requested lock
     * has been acquired. The corresponding queues should be processed.
     *
     * An acquire-and-release that releases an old lock on `name` should NOT
     * change the acquisition time of the lock on `name`, i.e. if a transaction
     * acquired locks in the order: S(A), X(B), acquire X(A) and release S(A),
     * the lock on A is considered to have been acquired before the lock on B.
     *
     * @throws DuplicateLockRequestException if a lock on `name` is already held
     * by `transaction` and isn't being released
     * @throws NoLockHeldException if `transaction` doesn't hold a lock on one
     * or more of the names in `releaseNames`
     */
    public void acquireAndRelease(TransactionContext transaction, ResourceName name,
                                  LockType lockType, List<ResourceName> releaseNames)
            throws DuplicateLockRequestException, NoLockHeldException {
        // TODO(proj4_part1): implement
        // You may modify any part of this method. You are not required to keep
        // all your code within the given synchronized block and are allowed to
        // move the synchronized block elsewhere if you wish.
        boolean shouldBlock = false;
        for (;;) {
            synchronized (this) {
                ResourceEntry resourceEntry = getResourceEntry(name);
                Lock lock = new Lock(name, lockType, transaction.getTransNum());
                // Error check
                if (resourceEntry.getTransactionLockType(transaction.getTransNum()) == lockType)
                    throw new DuplicateLockRequestException("DuplicateLockRequest");
                for (ResourceName resourceName : releaseNames) {
                    if (getResourceEntry(resourceName).getTransactionLockType(transaction.getTransNum()) == LockType.NL)
                        throw new NoLockHeldException("NoLockHeld");
                }

                if (!resourceEntry.checkCompatible(lockType, transaction.getTransNum())) {
                    LockRequest lockRequest = new LockRequest(transaction, lock, getLocks(transaction));
                    shouldBlock = true;
                    transaction.prepareBlock();
                } else {
                    for (ResourceName resourceName : releaseNames) {
                        release(transaction, resourceName);
                    }
                    acquire(transaction, name, lockType);
                    return;
                }
            }
            if (shouldBlock) {
                transaction.block();
            }
        }
    }

    /**
     * Acquire a `lockType` lock on `name`, for transaction `transaction`.
     *
     * Error checking must be done before the lock is acquired. If the new lock
     * is not compatible with another transaction's lock on the resource, or if there are
     * other transaction in queue for the resource, the transaction is
     * blocked and the request is placed at the **back** of NAME's queue.
     *
     * @throws DuplicateLockRequestException if a lock on `name` is held by
     * `transaction`
     */
    public void acquire(TransactionContext transaction, ResourceName name,
                        LockType lockType) throws DuplicateLockRequestException {
        // TODO(proj4_part1): implement
        // You may modify any part of this method. You are not required to keep all your
        // code within the given synchronized block and are allowed to move the
        // synchronized block elsewhere if you wish.
        boolean shouldBlock = false;

        ResourceEntry resourceEntry = getResourceEntry(name);
        Lock lock = new Lock(name, lockType, transaction.getTransNum());
        synchronized (this) {
            if (resourceEntry.getTransactionLockType(transaction.getTransNum()) == lockType)
                throw new DuplicateLockRequestException("DuplicateLockRequest");
            if (!resourceEntry.checkCompatible(lockType, transaction.getTransNum())
                || !resourceEntry.waitingQueue.isEmpty()) {
                LockRequest lockRequest = new LockRequest(transaction, lock);
                resourceEntry.addToQueue(lockRequest, false);
                shouldBlock = true;
                transaction.prepareBlock();
            } else {
                resourceEntry.grantOrUpdateLock(lock);

            }
        }
        if (shouldBlock) {
            transaction.block();
        }
        if (transactionLocks.get(transaction.getTransNum()) == null) {
            transactionLocks.put(transaction.getTransNum(), new ArrayList<>());
        }
        transactionLocks.get(transaction.getTransNum()).add(lock);
        return;
    }

    /**
     * Release `transaction`'s lock on `name`. Error checking must be done
     * before the lock is released.
     *
     * The resource name's queue should be processed after this call. If any
     * requests in the queue have locks to be released, those should be
     * released, and the corresponding queues also processed.
     *
     * @throws NoLockHeldException if no lock on `name` is held by `transaction`
     */
    public void release(TransactionContext transaction, ResourceName name)
            throws NoLockHeldException {
        // TODO(proj4_part1): implement
        // You may modify any part of this method.
        synchronized (this) {
            ResourceEntry resourceEntry = getResourceEntry(name);
            LockType lockType;
            if ((lockType = resourceEntry.getTransactionLockType(transaction.getTransNum())) == LockType.NL)
                throw new NoLockHeldException("NoLockHeld");
            Lock lock = new Lock(name, lockType, transaction.getTransNum());
            resourceEntry.releaseLock(lock);
            transactionLocks.get(transaction.getTransNum()).remove(lock);
        }
    }

    /**
     * Promote a transaction's lock on `name` to `newLockType` (i.e. change
     * the transaction's lock on `name` from the current lock type to
     * `newLockType`, if its a valid substitution).
     *
     * Error checking must be done before any locks are changed. If the new lock
     * is not compatible with another transaction's lock on the resource, the
     * transaction is blocked and the request is placed at the FRONT of the
     * resource's queue.
     *
     * A lock promotion should NOT change the acquisition time of the lock, i.e.
     * if a transaction acquired locks in the order: S(A), X(B), promote X(A),
     * the lock on A is considered to have been acquired before the lock on B.
     *
     * @throws DuplicateLockRequestException if `transaction` already has a
     * `newLockType` lock on `name`
     * @throws NoLockHeldException if `transaction` has no lock on `name`
     * @throws InvalidLockException if the requested lock type is not a
     * promotion. A promotion from lock type A to lock type B is valid if and
     * only if B is substitutable for A, and B is not equal to A.
     */
    public void promote(TransactionContext transaction, ResourceName name,
                        LockType newLockType)
            throws DuplicateLockRequestException, NoLockHeldException, InvalidLockException {
        // TODO(proj4_part1): implement
        // You may modify any part of this method.
        boolean shouldBlock = false;
        LockType oldLockType = getLockType(transaction, name);
        Lock oldLock = new Lock(name, oldLockType, transaction.getTransNum());
        Lock lock = new Lock(name, newLockType, transaction.getTransNum());
        ResourceEntry resourceEntry = getResourceEntry(name);
        synchronized (this) {
            if (oldLockType.equals(newLockType))
                throw new DuplicateLockRequestException("DuplicateLockRequest");
            if (!LockType.substitutable(newLockType, oldLockType))
                throw new InvalidLockException("Can not substitute");
            if (resourceEntry.getTransactionLockType(transaction.getTransNum()) == LockType.NL)
                throw new NoLockHeldException("NoLockHeld");
            if (!resourceEntry.checkCompatible(newLockType, transaction.getTransNum())) {
                shouldBlock = true;
                LockRequest lockRequest = new LockRequest(transaction, lock);
                resourceEntry.addToQueue(lockRequest, true);
                transaction.prepareBlock();
            } else {
                resourceEntry.grantOrUpdateLock(lock);
            }
        }
        if (shouldBlock) {
            transaction.block();
        }
        int index = transactionLocks.get(transaction.getTransNum()).indexOf(oldLock);
        transactionLocks.get(transaction.getTransNum()).set(index, lock);
    }

    /**
     * Return the type of lock `transaction` has on `name` or NL if no lock is
     * held.
     */
    public synchronized LockType getLockType(TransactionContext transaction, ResourceName name) {
        // TODO(proj4_part1): implement
        ResourceEntry resourceEntry = getResourceEntry(name);
        for (int i = 0; i < resourceEntry.locks.size(); i++) {
            Lock lock = resourceEntry.locks.get(i);
            if (lock.transactionNum == transaction.getTransNum())
                return lock.lockType;
        }
        return LockType.NL;
    }

    /**
     * Returns the list of locks held on `name`, in order of acquisition.
     */
    public synchronized List<Lock> getLocks(ResourceName name) {
        return new ArrayList<>(resourceEntries.getOrDefault(name, new ResourceEntry()).locks);
    }

    /**
     * Returns the list of locks held by `transaction`, in order of acquisition.
     */
    public synchronized List<Lock> getLocks(TransactionContext transaction) {
        return new ArrayList<>(transactionLocks.getOrDefault(transaction.getTransNum(),
                Collections.emptyList()));
    }

    /**
     * Creates a lock context. See comments at the top of this file and the top
     * of LockContext.java for more information.
     */
    public synchronized LockContext context(String name) {
        if (!contexts.containsKey(name)) {
            contexts.put(name, new LockContext(this, null, name));
        }
        return contexts.get(name);
    }

    /**
     * Create a lock context for the database. See comments at the top of this
     * file and the top of LockContext.java for more information.
     */
    public synchronized LockContext databaseContext() {
        return context("database");
    }
}
