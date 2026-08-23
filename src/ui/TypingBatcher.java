package ui;

import crdt.RGAReplica;

/**
 * Does RGAReplica.localInsert(...) call - one InsertStringOp, one network
 * send, one undo entry - instead of one per keystroke (InsertOP).
 */
public class TypingBatcher {
    private final RGAReplica replica;
    private int batchStart = -1;
    private final StringBuilder buffer = new StringBuilder();

    public TypingBatcher(RGAReplica replica) {
        this.replica = replica;
    }

    public boolean isPending() {
        return batchStart != -1;
    }

    /** User typed one char (or '\n') at document index caret. */
    public void type(int caret, char c) {
        if (isPending() && caret != batchStart + buffer.length()) {
            flush(); // caret jumped elsewhere - commit the old run first
        }
        if (!isPending()) {
            batchStart = caret;
        }
        buffer.append(c);
    }

    /**
     * User pressed backspace at document index caret (pre-deletion caret).
     * Returns true if it was absorbed by shrinking the still-pending batch
     * (caller only needs to update the textArea visually). Returns false if
     * there was nothing to absorb into - caller should do a normal
     * replica.localDelete(caret); any older pending batch has already been
     * flushed by this call.
     */
    public boolean backspace(int caret) {
        if (isPending() && caret == batchStart + buffer.length()) {
            buffer.deleteCharAt(buffer.length() - 1);
            if (buffer.isEmpty()) batchStart = -1;
            return true;
        }
        flush();
        return false;
    }

    /** Commits the pending run as a single localInsert call. Safe to call anytime. */
    public void flush() {
        if (!isPending()) return;
        replica.localInsert(batchStart, buffer.toString());
        batchStart = -1;
        buffer.setLength(0);
    }
}