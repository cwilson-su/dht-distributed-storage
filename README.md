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

Optimisations:

### Naive DHT (2nd gen; Gnutella style)
Basic tasks:
- [x] put
- [x] get
- [x] reply
- [x] join
- [x] leave
- [x] delete

Optimisations:
- [x] pong caching
- [x] Implicit Heartbeating (aka Passive Liveness Tracking)
- [ ] Reverse Path Caching
- [ ] ...

### Structured DHT (3rd gen): CHORD 
Basic tasks:
- [x] put
- [x] get
- [ ] reply
- [ ] join
- [ ] leave
- [ ] delete
- [ ] ...

Optimisations:

