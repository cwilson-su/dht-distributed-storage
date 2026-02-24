package dht;

public interface Node {
    void receiveMessage(Message msg);
    long getId();
}
