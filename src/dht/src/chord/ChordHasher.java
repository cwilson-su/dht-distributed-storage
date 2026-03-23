package chord;

import dht.Address;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

 
public class ChordHasher {
    public static final int M = 6;
    public static final int RING_SIZE = 1 << M; // 2^M = 64

    // Hash an Address (ip:port string) to a ring identifier
    public static int hash(Address addr) {
        return hash(addr.toString());
    }

    // Hash an arbitrary string key to a ring identifier
    public static int hash(String key) {
        return Math.floorMod(key.hashCode(), RING_SIZE);
    }

    // Returns (base + 2^i) mod 2^M — the start of finger table entry i.
    public static int fingerStart(int base, int i) {
        int offset = 1 << i; // 2^i
        return (base + offset) % RING_SIZE;
    }

  
    public static boolean inRangeRightInclusive(int id, int start, int end) {
        if (start == end) return true;
        if (start < end) {
            return id > start && id <= end;
        } else {
            return id > start || id <= end;
        }
    }

    public static boolean inRangeOpen(int id, int start, int end) {
        if (start == end) return id != start;
        if (start < end) {
            return id > start && id < end;
        } else {
            return id > start || id < end;
        }
    }
}
