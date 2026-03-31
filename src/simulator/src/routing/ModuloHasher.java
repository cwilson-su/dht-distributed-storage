package routing;

import core.Address;
import core.IRouter;
import core.Message;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModuloHasher implements IRouter {
    @Override
    public List<Address> getNext(Message msg, Address src, List<Address> peers) {
        String key = msg.getKey();
        if (key == null || key.isBlank()) return List.of();

        List<Address> allNodes = new ArrayList<>(peers);
        if (!allNodes.contains(src)) allNodes.add(src);
        if (allNodes.isEmpty()) return List.of();

        allNodes.sort(Comparator.comparing(Address::toString));
        int hash = Math.abs(key.hashCode());
        int idx = hash % allNodes.size();

        return List.of(allNodes.get(idx));
    }
}
