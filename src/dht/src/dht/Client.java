package dht;

import java.util.Random;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java dht.Client <targetIP:targetPort>");
            return;
        }

        Address target = Address.parse(args[0]);

        // Generate a random ephemeral port for this client session
        int clientPort = 10000 + new Random().nextInt(50000);

        Address myAddr = new Address(clientPort);

        // used for send() mechanism + receiving replies
        Node tool = new Node(clientPort);
        tool.start();

        int seqNum = 0;
        Scanner sc = new Scanner(System.in);

        System.out.println("--- DHT Client (" + myAddr + ") ---");
        System.out.println("Connected to Node: " + target);
        System.out.println("Commands: PUT <key> <value> | GET <key> | exit");

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
            int seq = ++seqNum;

            switch (cmd) {
                case "PUT" -> {
                    if (parts.length == 3) {
                        Message m = new Message(
                                Message.Type.PUT,
                                parts[1],
                                parts[2],
                                myAddr,
                                myAddr,
                                seq,
                                0
                        );

                        tool.send(target, m);
                    } else {
                        System.out.println("Usage: PUT <key> <value>");
                    }
                }

                case "GET" -> {
                    if (parts.length == 2) {
                        Message m = new Message(
                                Message.Type.GET,
                                parts[1],
                                "",
                                myAddr,
                                myAddr,
                                seq,
                                0
                        );

                        tool.send(target, m);
                        System.out.println("GET request " + m.getSeq() + " sent for key: " + m.getKey());
                    } else {
                        System.out.println("Usage: GET <key>");
                    }
                }

                default -> System.out.println("Unknown command.");
            }
        }

        sc.close();
    }
}