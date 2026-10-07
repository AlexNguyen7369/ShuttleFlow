/*
 * M2 scaffold — no implementation yet.
 *
 * Function: expose provider availability and provider-appointment endpoints.
 * Connection to M1: remains a thin HTTP boundary and delegates provider
 * ownership rules to ProviderService rather than accessing JdbcTemplate.
 *
 * M2 requirements addressed: POST/DELETE /provider/slots and
 * GET /provider/appointments with 401/403/404/409 behavior.
 */
