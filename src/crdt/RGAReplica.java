package crdt;

import network.CRDTNetworkNode;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public class RGAReplica implements Serializable {
    public static final NodeId head = new NodeId(0, 0);

    private final Map<NodeId, CharNode> nodes = new HashMap<>();
    private final Map<NodeId, List<CharNode>> children = new HashMap<>();
    private final ArrayDeque<Operation> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Operation> redoStack = new ArrayDeque<>();

    private long lamportClock = 0;
    // Unique site ID upon instantiation for every replica
    private final int siteId = ThreadLocalRandom.current().nextInt(1,Integer.MAX_VALUE);

    public CRDTNetworkNode networkNode;

    public RGAReplica(){
        CharNode headNode = new CharNode(head,'\0', null );
        headNode.deleted = true;
        nodes.put(head, headNode);
        children.put(head, new ArrayList<>());
    }

    synchronized NodeId nextId(){
        lamportClock++;
        return new NodeId(lamportClock, siteId);
    }

    private synchronized void updateClock(NodeId remoteId){
        if(remoteId != null){
            this.lamportClock = Math.max(this.lamportClock, remoteId.timestamp) + 1;
        }
    }
    // applying operations
    void apply(InsertOp op) {
        // Idempotent - ignore if already applied
        if (nodes.containsKey(op.id)) {
            return;
        }
        updateClock(op.id);
        CharNode node = new CharNode(op.id, op.value, op.prevId);
        nodes.put(op.id, node);

        // Add to children of prevId
        children.putIfAbsent(op.prevId, new ArrayList<>()); // if arrive out of order in network
        List<CharNode> siblings = children.get(op.prevId);
        siblings.add(node);

        // Deterministic ordering for concurrent inserts.
        siblings.sort(Comparator.comparing(n -> n.id));

        // Prepare child list for this node
        children.putIfAbsent(op.id, new ArrayList<>());
    }
    void apply(DeleteOp op){
        CharNode del = nodes.get(op.id);
        if(del != null){
            del.deleted = true;
        }

    }
    void apply(RestoreDeleteOP op) {
        CharNode del = nodes.get(op.id);
        if(del != null){
            del.deleted = false;
        }
    }
    void apply(InsertStringOp ops){
        NodeId prevId = ops.prevId;
        String st = ops.value;
        int n = st.length();
        NodeId id = ops.baseId;
        for(int i = 0; i < n; i++){
            NodeId nid = id.increment(i + 1);
            InsertOp op = new InsertOp(nid, prevId, st.charAt(i));
            apply(op);
            prevId = nid;
        }

    }

    void apply(DeleteStringOp op) {
        for (NodeId id : op.targetIds) {
            CharNode node = nodes.get(id);
            if (node != null) {
                node.deleted = true;
            }
        }
    }

    void apply(RestoreDeleteStringOp op) {
        for (NodeId id : op.targetIds) {
            CharNode node = nodes.get(id);
            if (node != null) {
                node.deleted = false;
            }
        }
    }

    // rendering document ---------
    public String getText() {
        StringBuilder sb = new StringBuilder();
        Deque<CharNode> stack = new ArrayDeque<>();
        stack.push(nodes.get(head));
        while (!stack.isEmpty()) {
            CharNode curr = stack.pop();
            if (!curr.deleted) {
                sb.append(curr.value);
            }
            List<CharNode> kids = children.get(curr.id);
            if (kids != null) {
                for (int i = kids.size() - 1; i >= 0; i--) {
                    stack.push(kids.get(i));
                }
            }
        }
        return sb.toString();
    }

    // local operations-------
    @Deprecated
    public void localInsert(int idx, char value) { // not in usage
        NodeId prevId = nodeByIndex(idx);
        NodeId id = nextId();
        InsertOp op = new InsertOp(id, prevId, value);
        apply(op);
        networkNode.send(op);
        pushUndo(op);
        redoStack.clear();
    }

    public void localInsert(int idx, String st){
        if (st.isEmpty()) return;
        NodeId prevId = nodeByIndex(idx);
        NodeId baseId = nextId();
        InsertStringOp op = new InsertStringOp(prevId, st, baseId);
        apply(op);
        networkNode.send(op);
        pushUndo(op);
        redoStack.clear();
    }

    public void localDelete(int idx){
        NodeId id = nodeByIndex(idx);
        DeleteOp op = new DeleteOp(id);
        apply(op);
        networkNode.send(op);
        pushUndo(op);
        redoStack.clear();
    }

    public void localDelete(int start, int end) {
        if (start >= end) return;

        List<NodeId> targets = new ArrayList<>();
        int liveIndex = 0; // Tracks 0-based visible index

        Deque<CharNode> stack = new ArrayDeque<>();
        CharNode headNode = nodes.get(head);
        if (headNode != null) stack.push(headNode);

        while (!stack.isEmpty()) {
            CharNode curr = stack.pop();

            if (!curr.id.equals(head) && !curr.deleted) {
                liveIndex++;
                 if (liveIndex > start && liveIndex <= end) {
                    targets.add(curr.id);
                    if (targets.size() == (end - start)) break; // Early exit once all needed nodes are collected
                }
            }

            List<CharNode> kids = children.get(curr.id);
            if (kids != null) {
                for (int i = kids.size() - 1; i >= 0; i--) {
                    stack.push(kids.get(i));
                }
            }
        }

        if (targets.isEmpty()) return;

        DeleteStringOp op = new DeleteStringOp(targets);
        apply(op);
        networkNode.send(op);
        pushUndo(op);
        redoStack.clear();
    }

    // finding node by index
    private NodeId nodeByIndex(int idx) {
        if (idx <= 0) return head;
        int currentIdx = 0;
        int targetIdx = idx-1;
        Deque<CharNode> stack = new ArrayDeque<>();
        stack.push(nodes.get(head));
        CharNode curr = null;
        while (!stack.isEmpty()) {
            curr = stack.pop();
            if (!curr.deleted) {
                if (currentIdx == targetIdx) {
                    return curr.id;
                }
                currentIdx++;
            }
            List<CharNode> kids = children.get(curr.id);
            if (kids != null) {
                for (int i = kids.size() - 1; i >= 0; i--) {
                    stack.push(kids.get(i));
                }
            }
        }

        assert curr != null;
        return curr.id;
    }

    // for sending & initialization
    public synchronized void replaceWith(RGAReplica other) {
        this.nodes.clear();
        this.children.clear();

        for (Map.Entry<NodeId, CharNode> e : other.nodes.entrySet()) {
            CharNode c = e.getValue();
            CharNode newNode = new CharNode(c.id, c.value, c.prevId);
            newNode.deleted = c.deleted;
            this.nodes.put(c.id, newNode);
        }

        for (Map.Entry<NodeId, List<CharNode>> e : other.children.entrySet()) {
            List<CharNode> list = new ArrayList<>();
            for (CharNode n : e.getValue()) {
                list.add(nodes.get(n.id));
            }
            this.children.put(e.getKey(), list);
        }

        undoStack.addAll(other.undoStack);
        redoStack.addAll(other.redoStack);
    }

    public synchronized RGAReplica deepCopy() {
        RGAReplica copy = new RGAReplica();
        copy.replaceWith(this);
        return copy;
    }

    public void undo(){
        Operation last = undoStack.removeLast().reverseOperation();
        last.applyTo(this);
        networkNode.send(last);
        redoStack.addLast(last);
    }
    public void redo(){
        Operation last = redoStack.removeLast().reverseOperation();
        last.applyTo(this);
        pushUndo(last);
        networkNode.send(last);
    }

    public void pushUndo(Operation op){
        if(undoStack.size() > 30) {
            undoStack.pop();
        }
        undoStack.addLast(op);
    }
    public boolean redoEmpty(){
        return redoStack.isEmpty();
    }
    public boolean undoEmpty(){
        return undoStack.isEmpty();
    }

}