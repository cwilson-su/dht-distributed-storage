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
- [ ] reply
- [ ] join
- [ ] leave
- [ ] delete
- [ ] ...

Concurrency:


Optimisations:

