package chord;

import dht.Address;
import dht.Message;
import metrics.MetricsLogger;

import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Envoie un GET avec clé spéciale directement à chaque nœud (TCP direct,
 * sans routing Chord) pour déclencher le log DATA_SKEW_BEFORE/AFTER.
 *
 * Usage: java chord.ChordSnapshotClient <before|after> <port1> [port2 ...]
 */
public class ChordSnapshotClient {

    private static final int CONNECT_TIMEOUT_MS = 3000;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java chord.ChordSnapshotClient <before|after> <port1> [port2 ...]");
            return;
        }
        MetricsLogger.configure("results/chord_metrics.csv");

        String phase      = args[0].toLowerCase();
        int    totalNodes = args.length - 1;
        String snapshotKey = "__snapshot_" + phase + "_" + totalNodes + "__";

        for (int i = 1; i < args.length; i++) {
            int port = Integer.parseInt(args[i].trim());
            Address nodeAddr = new Address(port);

            try (ServerSocket ss = new ServerSocket(0)) {
                int clientPort = ss.getLocalPort();
                Address clientAddr = new Address(clientPort);
                Message msg = new Message(
                        Message.Type.GET, snapshotKey, "",
                        clientAddr, clientAddr, i * 1000, 0);
                sendDirect(nodeAddr, msg);
                System.out.println("[SnapshotClient] " + phase + " -> port " + port);
            }
        }
        Thread.sleep(1000);
        System.out.println("[SnapshotClient] Terminé.");
    }

    private static void sendDirect(Address dest, Message msg) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(dest.getIp(), dest.getPort()), CONNECT_TIMEOUT_MS);
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception e) {
            System.err.println("[SnapshotClient] Impossible de joindre " + dest + " : " + e.getMessage());
        }
    }
}