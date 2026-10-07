/*
 * M2 scaffold — no implementation yet.
 *
 * Function: centralize session extraction and authenticated/customer/provider
 * access checks. Connection to M1: keeps controllers thin and lets the same
 * global ApiExceptionHandler map authorization failures consistently.
 *
 * M2 requirements addressed: 401 unauthenticated responses, 403 wrong-role
 * responses, and consistent server-side identity handling.
 */
