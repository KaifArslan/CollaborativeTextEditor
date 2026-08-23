package crdt;

import java.io.Serializable;
import java.util.Objects;

public class NodeId implements Serializable, Comparable<NodeId> {
    public final long timestamp;
    public final int siteId;
    public NodeId(long timestamp, int siteId){
        this.timestamp = timestamp;
        this.siteId = siteId;
    }

    public NodeId increment(int offset) {
        return new NodeId(this.timestamp + offset, this.siteId);
    }
    @Override
    public int compareTo(NodeId other){
        int timeCmp = Long.compare(other.timestamp, this.timestamp); // Descending time order
        if(timeCmp != 0) return timeCmp;
        return Integer.compare(other.siteId, this.siteId);
    }
    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(!(o instanceof NodeId nodeId)) return false;
        return timestamp == nodeId.timestamp && siteId == nodeId.siteId;
    }
    @Override
    public int hashCode(){
        return Objects.hash(timestamp, siteId);
    }

}
