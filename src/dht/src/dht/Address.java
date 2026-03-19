package dht;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class Address implements Serializable {
    private static final long serialVersionUID = 1L;
	public final String ip;
    public final int port;

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
