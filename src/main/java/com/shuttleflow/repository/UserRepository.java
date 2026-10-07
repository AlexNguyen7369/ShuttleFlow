/*
 * M2 scaffold — no implementation yet.
 *
 * Function: parameterized JdbcTemplate queries for user identity, BCrypt hash,
 * role, and provider linkage. Connection to M1: follows SlotRepository as the
 * only layer allowed to access SQL.
 *
 * M2 requirements addressed: database-backed login, role loading, and no
 * password/session secrets in DTOs or logs.
 */
