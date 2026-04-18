# dht-distributed-storage
Distributed storage system based on a DHT. Developed as an academic project for the Master’s in Distributed Systems and Applications (SAR) at Sorbonne University.


## The following operations work & have been tested:
### Client-Server (1st gen)
Basic tasks:
- [x] heartbeat
- [x] put
- [x] get
- [x] reply
- [x] join
- [x] leave
- [ ] delete
- [ ] ...

Concurrency:


Optimisations:


### Naive DHT (2nd gen; Gnutella style)
Basic tasks:
- [x] put
- [x] get
- [x] reply
- [x] join
- [x] leave
- [x] delete

Concurrency:
- [x] Multithreaded Architecture
- [x] Thread-Safe Data Structures (ConcurrentHashMap, CopyOnWriteArrayList, AtomicInteger localSeq)
- [x] Atomic Peer Insertion
- [x] Atomic Data Retrieval
- [x] Atomic Data Deletion
- [x] Atomic Routing Table Consumption

Optimisations:
- [x] Pong caching
- [x] Implicit Heartbeating (aka Passive Liveness Tracking)
- [x] Reverse Path Caching (simple routing table)

Discarded ideas (potential future work)
- [ ] Cache eviction {Split storage architecture: owenedStore(permanent, local PUTs) & cacheStore(intercepted Reverse Paths, LRU cache)}

### Structured DHT (3rd gen): CHORD 
Basic tasks:
- [x] put
- [x] get
- [x] reply
- [x] join
- [x] leave
- [x] finger table routing 
- [x] key transfer on join/leave
- [ ] delete

Concurrency:
- [x] Multithreaded Architecture (one thread per connection + dedicated Stabilizer thread)
- [x] Thread-Safe Finger Table (AtomicReferenceArray)
- [x] Thread-Safe Store (ConcurrentHashMap)
- [x] Thread-Safe Pending RPC Map (ConcurrentHashMap)
- [x] Atomic Sequence Counter (AtomicInteger)
- [x] Latch-based RPC synchronization (CountDownLatch with timeout)
- [x] Synchronized Predecessor Updates (check-then-act safety)
- [x] Thread-Safe Successor List (CopyOnWriteArrayList)

Optimisations:
- [x] Successor List for fault tolerance (backup successors)
- [x] Stabilization protocol (periodic ring self-repair)
- [x] Passive predecessor liveness check (checkPredecessorAlive)
- [x] Graceful leave with key transfer and neighbor notification