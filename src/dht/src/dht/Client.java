package dht;

import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java dht.Client <targetPort>");
            return;
        }

        int target = Integer.parseInt(args[0]);
        
        int clientPort = 10000 + new java.util.Random().nextInt(50000);		// Generate a random ephemeral port for this client session
        
        Address myAddr = new Address(clientPort);
        
        Node tool = new Node(clientPort);	// used for send() mechanism
        int seqNum = 0;
        Scanner sc = new Scanner(System.in);

        System.out.println("--- DHT Client (" + myAddr + ") ---");
        System.out.println("Connected to Node: " + target);
        System.out.println("Commands: PUT <key> <value> | GET <key> | exit");

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line.equalsIgnoreCase("exit")) break;

            String[] parts = line.split(" ");
            if (parts.length < 2) continue;

            String cmd = parts[0].toUpperCase();
            
            Message m = new Message();
            m.origin = myAddr;  // Original source 
            m.last = myAddr;    // Immediate previous hop 
            m.seq = ++seqNum;   // Sequence to prevent broadcast storms
            m.hops = 0;         // Initial TTL

            switch (cmd) {
                case "PUT" -> {
                    if (parts.length == 3) {
                        m.type = Message.Type.PUT;
                        m.k = parts[1];
                        m.v = parts[2];
                        tool.send(target, m);
                    } else {
                        System.out.println("Usage: PUT k v");
                    }
                }
                case "GET" -> {
                    if (parts.length == 2) {
                        m.type = Message.Type.GET;
                        m.k = parts[1];
                        tool.send(target, m);
                        System.out.println("GET request " + m.seq + " sent for key: " + m.k);
                    } else {
                        System.out.println("Usage: GET k");
                    }
                }
                default -> System.out.println("Unknown command.");
            }
        }
        sc.close();
    }
}