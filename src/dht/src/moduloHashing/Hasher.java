package moduloHashing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Hasher {
    // Centralized modulo hashing using the coordinator global view
    public static Address getTargetNode(String key, List<Address> nodes) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("No storage nodes available");
        }

        List<Address> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparing(Address::toString));

        int hashValue = Math.abs(key.hashCode());
        int targetIndex = hashValue % ordered.size();

        return ordered.get(targetIndex);
    }
}