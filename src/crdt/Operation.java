package crdt;

import java.io.Serializable;

public interface Operation extends Serializable {
    void applyTo(RGAReplica replica);
}
