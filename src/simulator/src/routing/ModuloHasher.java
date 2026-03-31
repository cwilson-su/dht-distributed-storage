package routing;

import core.Address;
import core.IRouter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModuloHasher implements IRouter {
    @Override
    public List<Address> getNext(String key, Address src, List<Address> peers) {
        if (key == null || key.isBlank()) return List.of();

        List<Address> allNodes = new ArrayList<>(peers);
        if (!allNodes.contains(src)) {
            allNodes.add(src);
        }

        if (allNodes.isEmpty()) return List.of();

        allNodes.sort(Comparator.comparing(Address::toString));

        int hash = Math.abs(key.hashCode());
        int idx = hash % allNodes.size();

        return List.of(allNodes.get(idx));
    }
}
