package dht0;

public interface Node {
    void receiveMessage(Message msg);
    long getId();
}
