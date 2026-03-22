package dht;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Hasher {
    // Simple modulo hashing using the full IP:Port string
	public static Address getTargetNode(String key, Address self, List<Address> peers) {
	    if (key == null) {
	        throw new IllegalArgumentException("Key cannot be null");
	    }
	    // Create a complete view of the network (Self + Peers)
	    List<Address> networkView = new ArrayList<>(peers);

	    if (!networkView.contains(self)) {
	        networkView.add(self);
	    }

	    if (networkView.isEmpty()) {
	        throw new IllegalStateException("No nodes available");
	    }

	    networkView.sort(Comparator.comparing(Address::toString));
	    
	    // Hash the key and apply modulo
	    int hashValue = Math.abs(key.hashCode());
	    int targetIndex = hashValue % networkView.size();

	    return networkView.get(targetIndex);
	}
}
