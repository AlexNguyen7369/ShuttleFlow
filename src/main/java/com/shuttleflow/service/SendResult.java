package com.shuttleflow.service;

/** Outcome of one gateway send; drives the outbox row's next status. */
public enum SendResult {
    SENT,
    /** Timeout, refused connection or 5xx: may succeed later. */
    RETRYABLE,
    /** 4xx: the request itself is wrong and will fail the same way every time. */
    PERMANENT
}
