package chord;

import dht.Address;
import dht.Message;
import metrics.MetricsLogger;

import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * ChordSnapshotClient — contacte chaque nœud Chord directement (TCP)
 * avec un vrai port éphémère et une clé spéciale "__snapshot_before__"
 * ou "__snapshot_after__" pour déclencher le log DATA_SKEW sur chaque nœud.
 *
 * On utilise Message.Type.GET avec une clé réservée car c'est garanti
 * de passer le filtre de désérialisation et d'être reçu par process().
 *
 * Usage:
 *   java chord.ChordSnapshotClient before <port1> <port2> ... <portN>
 *   java chord.ChordSnapshotClient after  <port1> <port2> ... <portN>
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

        // Clé spéciale reconnue par ChordNodeHandler sans routing Chord
        String snapshotKey = "__snapshot_" + phase + "_" + totalNodes + "__";

        for (int i = 1; i < args.length; i++) {
            int port = Integer.parseInt(args[i].trim());
            Address nodeAddr = new Address(port);

            // Ouvre un vrai ServerSocket éphémère pour que l'adresse origin soit valide
            try (ServerSocket ss = new ServerSocket(0)) {
                int clientPort = ss.getLocalPort();
                Address clientAddr = new Address(clientPort);

                Message snapshotMsg = new Message(
                        Message.Type.GET,
                        snapshotKey,
                        "",
                        clientAddr,
                        clientAddr,
                        i * 1000,
                        0
                );

                sendDirect(nodeAddr, snapshotMsg);
                System.out.println("[SnapshotClient] snapshot-" + phase
                        + " envoyé -> port " + port + " (clé=" + snapshotKey + ")");
            }
        }

        // Laisse le temps aux nœuds de traiter et flusher le MetricsLogger
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