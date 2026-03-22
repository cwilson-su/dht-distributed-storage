package dht;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class Address implements Serializable {
    private static final long serialVersionUID = 1L;
	public final String ip;
    public final int port;
  
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
        this.ip = "127.0.0.1";
        this.port = port;
    }
    
    public Address(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    // Helper to parse CLI arguments like 127.0.0.1:8002
    public static Address parse(String addr) {
        if (addr.contains(":")) {
            String[] parts = addr.split(":");
            return new Address(parts[0], Integer.parseInt(parts[1]));
        }
        return new Address(Integer.parseInt(addr)); // Fallback to local
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
