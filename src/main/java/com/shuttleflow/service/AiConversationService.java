package com.shuttleflow.service;

import com.shuttleflow.repository.AiConversationRepository;
import com.shuttleflow.repository.AiConversationRepository.PendingRecord;
import com.shuttleflow.repository.AiConversationRepository.TurnRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The chat agent's per-user state, kept in the database so login can be stateless (JWT).
 * Confirm-before-write lives in {@link #takeConfirmable}: a pending action is usable only on the
 * turn right after it was proposed, and only once.
 */
@Service
public class AiConversationService {
    public static final String BOOK = "BOOK";
    public static final String CANCEL = "CANCEL";

    private final AiConversationRepository repository;

    public AiConversationService(AiConversationRepository repository) {
        this.repository = repository;
    }

    /** Starts a new conversation: new memory id, turn 0, nothing pending. Called on login. */
    @Transactional
    public void reset(long userId) {
        String conversationId = UUID.randomUUID().toString();
        repository.insertIfMissing(userId, conversationId);
        repository.reset(userId, conversationId);
    }

    /** Advances the user's turn counter and returns it with the conversation (memory) id. */
    @Transactional
    public TurnRecord nextTurn(long userId) {
        repository.insertIfMissing(userId, UUID.randomUUID().toString());
        repository.incrementTurn(userId);
        return repository.findTurn(userId).orElseThrow(() -> new IllegalStateException("Conversation row missing."));
    }

    public void propose(long userId, String kind, long targetId, int turn) {
        repository.insertIfMissing(userId, UUID.randomUUID().toString());
        repository.setPending(userId, kind, targetId, turn);
    }

    /**
     * Returns the pending action's target id if it has this kind and was proposed on the turn just
     * before {@code turn}, claiming it so it can never be used again. An older proposal expires.
     */
    public Optional<Long> takeConfirmable(long userId, String kind, int turn) {
        Optional<PendingRecord> found = repository.findPending(userId);
        if (found.isEmpty() || !found.get().kind().equals(kind) || found.get().turn() >= turn) {
            return Optional.empty();
        }
        PendingRecord pending = found.get();
        if (pending.turn() != turn - 1) {
            // Stale: the user moved on after the proposal, so this message is not the answer to it.
            repository.clearPending(userId);
            return Optional.empty();
        }
        return repository.claimPending(userId, kind, pending.targetId(), pending.turn())
                ? Optional.of(pending.targetId()) : Optional.empty();
    }
}
