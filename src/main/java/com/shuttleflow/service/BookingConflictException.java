/*
 * M2 scaffold — no implementation yet.
 *
 * Function: represent a safe domain-level booking race or already-booked
 * conflict. Connection to M1: follows InvalidRequestException's service-level
 * exception pattern while allowing ApiExceptionHandler to return HTTP 409.
 *
 * M2 requirements addressed: concurrent booking conflict translation and safe
 * error messages without leaking SQL constraint details.
 */
