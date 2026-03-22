package dht;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Hasher {
    // Simple modulo hashing using the full IP:Port string
    public static Address getTargetNode(String key, Address self, List<Address> peers) {
        // Create a complete view of the network (Self + Peers)
        List<Address> networkView = new ArrayList<>(peers);
        if (!networkView.contains(self)) {
            networkView.add(self);
        }

        // Sort predictably using the full IP:Port string (eg. "127.0.0.1:8001")
        networkView.sort(Comparator.comparing(Address::toString));

        // Hash the key and apply modulo
        int hashValue = Math.abs(key.hashCode());
        int targetIndex = hashValue % networkView.size();

        return networkView.get(targetIndex);
    }
}