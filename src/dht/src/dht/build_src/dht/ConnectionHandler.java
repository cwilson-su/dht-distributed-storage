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
        Message m = null;

        try (socket;
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Minimal deserialization filter to reduce risk
            ObjectInputFilter filter = info -> {
                Class<?> c = info.serialClass();
                if (c == null) return ObjectInputFilter.Status.UNDECIDED;

                String name = c.getName();
                if (name.startsWith("dht.") || name.startsWith("chord.") || name.startsWith("java.lang.") || name.startsWith("java.util.") || name.startsWith("java.io.")
                        || name.startsWith("[L")      
                        || name.startsWith("[B")      
                        || name.startsWith("[C")) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
                return ObjectInputFilter.Status.REJECTED;
            };
            in.setObjectInputFilter(filter);

            Object obj = in.readObject();
            if (obj instanceof Message msg) {
                m = msg;
            }

        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }

        try {
            handler.process(m);
        } catch (Exception e) {
            System.err.println("Connection error (traitement): " + e.getMessage());
        }
    }
}