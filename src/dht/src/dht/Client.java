package dht;

import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java dht.Client <targetPort>");
            return;
        }
        
     
        int myId = (int) (System.currentTimeMillis() % 10000);	// use a random originPort or a timestamp to ensure nodes treat it as a new session
        
        int target = Integer.parseInt(args[0]); //target port
        Node tool = new Node(9999); // Temporary node to use its send() method
        int seqNum = 0;
        Scanner sc = new Scanner(System.in);

        System.out.println("Client connected to Node " + target);
        System.out.println("Commands: PUT k v | GET k | exit");
        
        System.out.println("--- DHT Client (Connected to " + target + ") ---");

        while (true) {
        	Message m = new Message();
            m.originPort = myId; // unique to this client session
        	m.lastPort = 9999;   // mark client as last hop to prevent back-propagation
        	
            System.out.print("> ");
            String line = sc.nextLine();
            if (line.equals("exit")) break;

            String[] parts = line.split(" ");

            String cmd = parts[0].toUpperCase();
            m.seq = ++seqNum; // increment for every unique request

            switch (cmd) {
                case "PUT" -> {
                    if (parts.length == 3) {
                        m.type = Message.Type.PUT;
                        m.k = parts[1]; 
                        m.v = parts[2];
                        tool.send(target, m);
                    }
                }
                case "GET" -> {
                    if (parts.length == 2) {
                        m.type = Message.Type.GET;
                        m.k = parts[1];
                        tool.send(target, m);
                    }
                }
                default -> System.out.println("Unknown command. Use PUT k v or GET k");
            }
        }
    }
}