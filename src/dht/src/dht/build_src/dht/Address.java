package dht;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class Address implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String ip;
    private final int port;
    
    /*
    // this version will be used later when we'll no longer test our project locally
    public Address(int port) {
        String tempIp;
        try {
            tempIp = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            tempIp = "127.0.0.1";
        }
        this.ip = tempIp;
        this.port = port;
    }
    */

    // local-friendly version
    public Address(int port) {
		 if (port < 0 || port > 65535) {
	         throw new IllegalArgumentException("Invalid port: " + port);
	     }
		 this.ip = "127.0.0.1";
		 this.port = port;
    }
    
    public Address(String ip, int port) {
    	if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("IP cannot be null or empty");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }

        this.ip = ip;
        this.port = port;
    }
    
    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    // Helper to parse CLI arguments like 127.0.0.1:8002
    public static Address parse(String addr) {
    	if (addr == null || addr.isBlank()) {
            throw new IllegalArgumentException("Address string cannot be null or empty");
        }

        if (addr.contains(":")) {
            String[] parts = addr.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid address format: " + addr);
            }
            return new Address(parts[0], Integer.parseInt(parts[1]));
        }

        return new Address(Integer.parseInt(addr));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Address other)) return false;
        return port == other.port && ip.equals(other.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }

    @Override
    public String toString() {
        return ip + ":" + port;
    }
}
