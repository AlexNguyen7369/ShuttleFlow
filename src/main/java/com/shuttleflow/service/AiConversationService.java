package com.shuttleflow.service;

import com.shuttleflow.repository.AiConversationRepository;
import com.shuttleflow.repository.AiConversationRepository.PendingRecord;
import com.shuttleflow.repository.AiConversationRepository.TurnRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transaction;

    public AiConversationService(AiConversationRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // Not @Transactional: the row is created in its own auto-committed statement first (see insertIfMissing).

    /** Starts a new conversation: new memory id, turn 0, nothing pending. Called on login. */
    public void reset(long userId) {
        String conversationId = UUID.randomUUID().toString();
        repository.insertIfMissing(userId, conversationId);
        repository.reset(userId, conversationId);
    }

    /** Advances the user's turn counter and returns it with the conversation (memory) id. */
    public TurnRecord nextTurn(long userId) {
        repository.insertIfMissing(userId, UUID.randomUUID().toString());
        // The UPDATE row-locks until commit, so reading the turn back in the same transaction is exact.
        return transaction.execute(status -> {
            repository.incrementTurn(userId);
            return repository.findTurn(userId).orElseThrow(() -> new IllegalStateException("Conversation row missing."));
        });
    }

    public void propose(long userId, String kind, long targetId, int turn) {
        repository.insertIfMissing(userId, UUID.randomUUID().toString());
        Optional<PendingRecord> current = repository.findPending(userId);
        if (current.isPresent() && current.get().kind().equals(kind)
                && current.get().targetId() == targetId && current.get().turn() == turn - 1) {
            return; // Same proposal repeated on the "yes" turn: keep it confirmable.
        }
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
