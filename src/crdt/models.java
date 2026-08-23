package crdt;
import java.util.ArrayList;
import java.util.List;

class InsertOp implements Operation {
    NodeId id;
    NodeId prevId;
    char value;

    InsertOp(NodeId id, NodeId prevId, char value) {
        this.id = id;
        this.prevId = prevId;
        this.value = value;
    }
    @Override
    public void applyTo(RGAReplica replica) {
        replica.apply(this);
    }

    @Override
    public Operation reverseOperation(){
       return new DeleteOp(id);
   }
}
class InsertStringOp implements Operation {
    NodeId baseId;
    NodeId prevId;
    String value;

    public InsertStringOp(NodeId prevId, String value, NodeId baseId) {
        this.prevId = prevId;
        this.value = value;
        this.baseId = baseId;
//        this.baseId = RGAReplica.nextId();
    }
    @Override
    public void applyTo(RGAReplica replica) {
        replica.apply(this);
    }

    @Override
    public Operation reverseOperation(){
        List<NodeId> list = new ArrayList<>();
        for(int i = 0; i < value.length(); i++){
            list.add(baseId.increment(i+1));
        }
        return new DeleteStringOp(list);
    }

}

class DeleteOp implements Operation{
    NodeId id;

    public DeleteOp(NodeId id) {
        this.id = id;
    }

    @Override
    public void applyTo(RGAReplica replica) {
        replica.apply(this);
    }

    @Override
    public Operation reverseOperation(){
        return new RestoreDeleteOP(id);
    }
}

class DeleteStringOp implements Operation {
    public final List<NodeId> targetIds;

    public DeleteStringOp(List<NodeId> targetIds) {
        this.targetIds = targetIds;
    }

    @Override
    public void applyTo(RGAReplica replica) {
        replica.apply(this);
    }

    @Override
    public Operation reverseOperation() {
        return new RestoreDeleteStringOp(targetIds);
    }
}

class RestoreDeleteStringOp implements Operation {
    public final List<NodeId> targetIds;

    public RestoreDeleteStringOp(List<NodeId> targetIds) {
        this.targetIds = targetIds;
    }

    @Override
    public void applyTo(RGAReplica replica) {
        replica.apply(this);
    }

    @Override
    public Operation reverseOperation() {
        return new DeleteStringOp(targetIds);
    }
}

class RestoreDeleteOP implements Operation{
    NodeId id;

    public RestoreDeleteOP(NodeId id) {
        this.id = id;
    }

    @Override
    public void applyTo(RGAReplica replica) {
        replica.apply(this);
    }

    @Override
    public Operation reverseOperation(){
        return new DeleteOp(id);
    }
}
