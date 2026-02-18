package dht0;

class Message {
    enum Type { PUT, GET }
    Type type;
    String key;
    String value;
    Node origin; 
    int hops = 0; 
}
