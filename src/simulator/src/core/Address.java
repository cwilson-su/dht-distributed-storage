package core;

import java.io.Serializable;
import java.util.Objects;

public class Address implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;

    public Address(int id) {
        if (id < 0) throw new IllegalArgumentException("Invalid ID");
        this.id = id;
    }

    public int getId() { return id; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Address other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Node-" + id;
    }
}
