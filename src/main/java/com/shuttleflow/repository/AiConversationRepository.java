package com.shuttleflow.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** One row of chat-agent state per user: turn counter, memory id and the pending propose/confirm action. */
@Repository
public class AiConversationRepository {

    private static final String INSERT_IF_MISSING_SQL = """
            INSERT INTO ai_conversations (user_id, conversation_id)
            SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM ai_conversations WHERE user_id = ?)
            """;

    private static final String RESET_SQL = """
            UPDATE ai_conversations
            SET conversation_id = ?, turn = 0, pending_kind = NULL, pending_target_id = NULL, pending_turn = NULL
            WHERE user_id = ?
            """;

    // The UPDATE row-locks until commit, so concurrent messages from one user get distinct turns.
    private static final String INCREMENT_TURN_SQL = "UPDATE ai_conversations SET turn = turn + 1 WHERE user_id = ?";

    private static final String FIND_TURN_SQL =
            "SELECT turn, conversation_id FROM ai_conversations WHERE user_id = ?";

    private static final String SET_PENDING_SQL = """
            UPDATE ai_conversations SET pending_kind = ?, pending_target_id = ?, pending_turn = ?
            WHERE user_id = ?
            """;

    private static final String FIND_PENDING_SQL = """
            SELECT pending_kind, pending_target_id, pending_turn FROM ai_conversations
            WHERE user_id = ? AND pending_kind IS NOT NULL
            """;

    // Conditional on the exact action, so only one caller can claim it.
    private static final String CLAIM_PENDING_SQL = """
            UPDATE ai_conversations SET pending_kind = NULL, pending_target_id = NULL, pending_turn = NULL
            WHERE user_id = ? AND pending_kind = ? AND pending_target_id = ? AND pending_turn = ?
            """;

    private static final String CLEAR_PENDING_SQL = """
            UPDATE ai_conversations SET pending_kind = NULL, pending_target_id = NULL, pending_turn = NULL
            WHERE user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public AiConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Must run outside a transaction: on PostgreSQL a unique violation aborts the surrounding
     * transaction even when caught, so the losing racer would fail its next statement.
     */
    public void insertIfMissing(long userId, String conversationId) {
        try {
            jdbcTemplate.update(INSERT_IF_MISSING_SQL, userId, conversationId, userId);
        } catch (DuplicateKeyException e) {
            // Another request created the row first; that row is just as good.
        }
    }

    public void reset(long userId, String conversationId) {
        jdbcTemplate.update(RESET_SQL, conversationId, userId);
    }

    public void incrementTurn(long userId) {
        jdbcTemplate.update(INCREMENT_TURN_SQL, userId);
    }

    public Optional<TurnRecord> findTurn(long userId) {
        return jdbcTemplate.query(FIND_TURN_SQL,
                (rs, rowNum) -> new TurnRecord(rs.getInt("turn"), rs.getString("conversation_id")), userId)
                .stream().findFirst();
    }

    public void setPending(long userId, String kind, long targetId, int turn) {
        jdbcTemplate.update(SET_PENDING_SQL, kind, targetId, turn, userId);
    }

    public Optional<PendingRecord> findPending(long userId) {
        return jdbcTemplate.query(FIND_PENDING_SQL, (rs, rowNum) -> new PendingRecord(
                rs.getString("pending_kind"), rs.getLong("pending_target_id"), rs.getInt("pending_turn")), userId)
                .stream().findFirst();
    }

    /** True for exactly one caller: the one whose UPDATE cleared this pending action. */
    public boolean claimPending(long userId, String kind, long targetId, int turn) {
        return jdbcTemplate.update(CLAIM_PENDING_SQL, userId, kind, targetId, turn) == 1;
    }

    public void clearPending(long userId) {
        jdbcTemplate.update(CLEAR_PENDING_SQL, userId);
    }

    public record TurnRecord(int turn, String conversationId) { }

    public record PendingRecord(String kind, long targetId, int turn) { }
}
