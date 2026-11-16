package com.shuttleflow.ai;

import java.io.Serializable;

/**
 * A write the model proposed but the user has not confirmed yet. Kept in the session with the
 * chat turn it was proposed in, so a confirm in that same turn can be refused.
 */
public record PendingAction(Kind kind, long targetId, int turn) implements Serializable {
    public static final String SESSION_KEY = "ai.pendingAction";

    public enum Kind { BOOK, CANCEL }
}
