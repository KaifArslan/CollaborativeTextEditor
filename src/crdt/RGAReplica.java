package crdt;

import java.io.Serializable;
import java.util.*;

public class RGAReplica implements Serializable {
    final static UUID head = new UUID(0,0);
    final Map<UUID, CharNode> nodes = new HashMap<>();
    final Map<UUID, List<CharNode>> children = new HashMap<>();

    public RGAReplica(){
        CharNode headNode = new CharNode(head, '\0', null);
        nodes.put(head, headNode);
        children.put(head, new ArrayList<>());
    }

    // applying operations
    void apply(InsertOp op) {
        // Idempotent - ignore if already applied
        if (nodes.containsKey(op.id)) {
            return;
        }

        CharNode node = new CharNode(op.id, op.value, op.prevId);
        nodes.put(op.id, node);

        // Add to children of prevId
        children.putIfAbsent(op.prevId, new ArrayList<>()); // if arrive out of order in network
        List<CharNode> siblings = children.get(op.prevId);
        siblings.add(node);

        // Deterministic ordering for concurrent inserts, might need to change later
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

    // rendering document ---------
    public String getText(){
        StringBuilder sb = new StringBuilder();
        traverse(head, sb);
        return sb.toString();
    }

    void traverse(UUID id, StringBuilder sb){
        List<CharNode> current = children.get(id);
        if (current == null) return;
        for(CharNode c : current){
            if(!c.deleted)
                sb.append(c.value);
            traverse(c.id, sb); // going in depth
        }
    }

    // local operations-------
    public Operation localInsert(int idx, char value) {
        UUID prevId = nodeByIndex(idx);
//        System.out.println(nodes.get(prevId).value + " <--- last value" );
        UUID id = UUID.randomUUID();
        InsertOp op = new InsertOp(id, prevId, value);
        apply(op);
        return op;
    }

    public List<Operation> localInsert(int idx, String st){
        int n = st.length();
        UUID prevId = nodeByIndex(idx);
        ArrayList<Operation> list = new ArrayList<>(n);
        for(int i = 0; i < n; i++){
            UUID id = UUID.randomUUID();
            InsertOp op = new InsertOp(id, prevId, st.charAt(i));
            apply(op);
            prevId = id;
            list.add(op);
        }
        return list;
    }

    public Operation localDelete(int idx){
        UUID id = nodeByIndex(idx);
        DeleteOp op = new DeleteOp(id);
        apply(op);
        return op;
    }


    // finding node by index
    UUID nodeByIndex(int idx){
        if (idx == 0) return head;
        IndexCounter indexCounter = new IndexCounter(idx-1);
        traverseForNode(head, indexCounter);
        if(indexCounter.nodeFound == null){
            throw new IllegalArgumentException("index out of bounds");
        }
        return indexCounter.nodeFound;

    }
    void traverseForNode(UUID node, IndexCounter indexCounter){
        List<CharNode> list = children.get(node);
        if (list == null) return;
        for(CharNode current: list){
            if (!current.deleted) {
                if (indexCounter.target == indexCounter.counter) {
                    indexCounter.nodeFound = current.id;
                    return;
                }
                indexCounter.counter++;
            }
            traverseForNode(current.id, indexCounter);
            if(indexCounter.nodeFound != null) return;
        }
    }

    // for sending & initialization
    public synchronized void replaceWith(RGAReplica other) {
        this.nodes.clear();
        this.children.clear();

        for (Map.Entry<UUID, CharNode> e : other.nodes.entrySet()) {
            CharNode c = e.getValue();
            CharNode newNode = new CharNode(c.id, c.value, c.prevId);
            newNode.deleted = c.deleted;
            this.nodes.put(c.id, newNode);
        }

        for (Map.Entry<UUID, List<CharNode>> e : other.children.entrySet()) {
            List<CharNode> list = new ArrayList<>();
            for (CharNode n : e.getValue()) {
                list.add(nodes.get(n.id));
            }
            this.children.put(e.getKey(), list);
        }
    }

    public synchronized RGAReplica deepCopy() {
        RGAReplica copy = new RGAReplica();
        copy.replaceWith(this);
        return copy;
    }


}

