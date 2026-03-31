package core;
import java.util.List;

public interface IRouter {
    List<Address> getNext(Message msg, Address src, List<Address> peers);
}
