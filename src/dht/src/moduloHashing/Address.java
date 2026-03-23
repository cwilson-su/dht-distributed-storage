package moduloHashing;

import java.io.Serializable;
import java.util.Objects;

public class Address implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String ip;
    private final int port;

    // Local-friendly constructor
    public Address(int port) {
        this("127.0.0.1", port);
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

    // Parse "127.0.0.1:9000" or "9000"
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