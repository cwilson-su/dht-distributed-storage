package dht;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.net.Socket;

public class ConnectionHandler implements Runnable {
    private final Socket socket;
    private final NodeHandler handler;

    public ConnectionHandler(Socket socket, NodeHandler handler) {
        this.socket = socket;
        this.handler = handler;
    }

    @Override
    public void run() {
        try (socket;
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Minimal deserialization filter to reduce risk
            ObjectInputFilter filter = info -> {
                Class<?> c = info.serialClass();
                if (c == null) return ObjectInputFilter.Status.UNDECIDED;

                String name = c.getName();
                if (name.startsWith("dht.") || name.startsWith("java.lang.")) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
                return ObjectInputFilter.Status.REJECTED;
            };
            in.setObjectInputFilter(filter);

            Object obj = in.readObject();
            if (obj instanceof Message m) {
                handler.process(m);
            }

        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }
}