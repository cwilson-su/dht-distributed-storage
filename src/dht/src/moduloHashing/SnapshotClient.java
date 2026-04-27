package moduloHashing;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import metrics.MetricsLogger;

public class SnapshotClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java moduloHashing.SnapshotClient <coordIP:port>");
            return;
        }
        MetricsLogger.configure("results/centralized_metrics.csv");
        Address coord = Address.parse(args[0]);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(coord.getIp(), coord.getPort()), 3000);
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())) {
                out.flush();
                out.writeObject(Message.snapshotBefore());
                out.flush();
                Object resp = in.readObject();
                if (resp instanceof Message msg) {
                    System.out.println("[SNAPSHOT] " + msg.getInfo());
                }
            }
        }
    }
}