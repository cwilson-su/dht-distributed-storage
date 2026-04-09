package chord;

import dht.Address;

 
public class ChordHasher {
    public static final int M = 6;
    public static final int RING_SIZE = 1 << M; // 2^M = 64


    public static int hash(Address addr) {
        if (addr == null) return 0;
        return Math.abs(addr.toString().hashCode()) % RING_SIZE;
    }
   
    public static int hash(String key) {
        return Math.abs(key.hashCode()) % RING_SIZE;
    }


    public static boolean inRange(int id, int start, int end) {
        if (start < end) {
            return id > start && id <= end;
        } else { 
            // wrap-around
            return id > start || id <= end;
        }
    }
}
