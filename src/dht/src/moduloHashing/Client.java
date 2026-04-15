package moduloHashing;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

import metrics.MetricsLogger;

public class Client {
    private static final int CONNECT_TIMEOUT_MS = 2000;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java moduloHashing.Client <coordinatorIP:coordinatorPort>");
            return;
        }
        
        MetricsLogger.configure("results/centralized_metrics.csv");

        Address coordinator = Address.parse(args[0]);
        Scanner sc = new Scanner(System.in);

        System.out.println("--- Centralized Modulo Hashing Client ---");
        System.out.println("Connected to Coordinator: " + coordinator);
        System.out.println("Commands: PUT <key> <value> | GET <key> | DELETE <key> | exit");

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();

            if (line.equalsIgnoreCase("exit")) {
                break;
            }

            String[] parts = line.split(" ");
            if (parts.length < 2) {
                continue;
            }

            String cmd = parts[0].toUpperCase();

            switch (cmd) {
                case "PUT" -> {
                    if (parts.length == 3) {
                    	long start    = System.nanoTime();
                        Message response = sendRequest(coordinator, Message.clientPut(parts[1], parts[2]));
                        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
                        printResponse(response);
                        
                        int hops = (response != null) ? response.getHopCount() : 0;
                        MetricsLogger.get().log("PUT", latencyMs, hops, parts[1], "");
                        System.out.printf("  [metrics] latency=%dms  hops=%d%n", latencyMs, hops);
                    } else {
                        System.out.println("Usage: PUT <key> <value>");
                    }
                }

                case "GET" -> {
                    if (parts.length == 2) {
                    	long start    = System.nanoTime();
                        Message response = sendRequest(coordinator, Message.clientGet(parts[1]));
                        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
                        printResponse(response);
                        
                        int hops = (response != null) ? response.getHopCount() : 0;
                        MetricsLogger.get().log("GET", latencyMs, hops, parts[1], "");
                        System.out.printf("  [metrics] latency=%dms  hops=%d%n", latencyMs, hops);
                    } else {
                        System.out.println("Usage: GET <key>");
                    }
                }
                case "DELETE" -> {
                    if (parts.length == 2) {
                        long start = System.nanoTime();
                        Message response = sendRequest(coordinator, Message.clientDelete(parts[1]));
                        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
                        printResponse(response);

                        int hops = (response != null) ? response.getHopCount() : 0;
                        MetricsLogger.get().log("DELETE", latencyMs, hops, parts[1], "");
                        System.out.printf("  [metrics] latency=%dms  hops=%d%n", latencyMs, hops);
                    } else {
                        System.out.println("Usage: DELETE <key>");
                    }
                }

                default -> System.out.println("Unknown command.");
            }
        }

        sc.close();
    }

    private static Message sendRequest(Address dest, Message message) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(dest.getIp(), dest.getPort()), CONNECT_TIMEOUT_MS);

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.flush();
                out.writeObject(message);
                out.flush();

                Object obj = in.readObject();
                if (obj instanceof Message response) {
                    return response;
                }
                return Message.error("Invalid response type");
            }
        } catch (Exception e) {
            return Message.error("Client request failed: " + e.getMessage());
        }
    }

    private static void printResponse(Message response) {
        if (response == null) {
            System.out.println("No response received.");
            return;
        }

        switch (response.getType()) {
            case CLIENT_RESPONSE -> {
                if (response.isFound() || response.getValue() != null) {
                    System.out.println("[OK] " + response.getInfo() +
                            (response.getKey() != null ? " | key=" + response.getKey() : "") +
                            (response.getValue() != null ? " | value=" + response.getValue() : ""));
                } else {
                    System.out.println("[MISS] " + response.getInfo() +
                            (response.getKey() != null ? " | key=" + response.getKey() : ""));
                }
            }
            case ACK -> System.out.println("[ACK] " + response.getInfo());
            case HEARTBEAT_ACK -> System.out.println("[HEARTBEAT_ACK] " + response.getInfo());
            case ERROR -> System.out.println("[ERROR] " + response.getInfo());
            case REBALANCING -> System.out.println("[WAIT] " + response.getInfo());
            default -> System.out.println("[INFO] " + response);
        }
    }
}