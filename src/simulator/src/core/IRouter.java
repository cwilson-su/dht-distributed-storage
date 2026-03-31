package core;

import java.util.List;
import core.Address;

public interface IRouter {
    List<Address> getNext(String key, Address src, List<Address> peers);
}
