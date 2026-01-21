package crdt;
import java.io.Serializable;
import java.util.UUID;

class CharNode implements Serializable {
    UUID id;
    char value;
    UUID prevId;       // ID of node this was inserted after
    boolean deleted; //tombstone

    CharNode(UUID id, char value, UUID prevId) {
        this.id = id;
        this.value = value;
        this.prevId = prevId;
        this.deleted = false;
    }
}

class InsertOp implements Operation {
    UUID id;
    UUID prevId;
    char value;

    InsertOp(UUID id, UUID prevId, char value) {
        this.id = id;
        this.prevId = prevId;
        this.value = value;
    }
    @Override
    public void applyTo(RGAReplica replica) {
        replica.apply(this);
    }
}

class DeleteOp implements Operation{
    UUID id;

    DeleteOp(UUID id) {
        this.id = id;
    }

    @Override
    public void applyTo(RGAReplica replica) {
        replica.apply(this);
    }
}


class IndexCounter{
    int counter = 0;
    int target;
    UUID nodeFound = null;
    public IndexCounter(int target){
        this.target = target;
    }
}
