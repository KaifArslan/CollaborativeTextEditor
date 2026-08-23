package crdt;
import java.io.Serializable;

class CharNode implements Serializable {
    NodeId id;
    char value;
    NodeId prevId;       // ID of node this was inserted after
    boolean deleted; //tombstone

    CharNode(NodeId id, char value, NodeId prevId) {
        this.id = id;
        this.value = value;
        this.prevId = prevId;
        this.deleted = false;
    }
}