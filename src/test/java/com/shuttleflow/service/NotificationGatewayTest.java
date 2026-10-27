package com.shuttleflow.service;

import com.shuttleflow.dto.NotificationRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real HTTP against a JDK stub server, so timeouts and status codes behave as on the wire. */
class NotificationGatewayTest {
    private static final NotificationRequest REQUEST = new NotificationRequest(
            "alex@shuttleflow.com", NotificationRequest.BOOKING_CONFIRMED, "Your court is booked.", "BOOKED:1", 1L);

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Starts a stub that answers with the given statuses in order (the last one repeats). */
    private String stub(Integer... statuses) throws IOException {
        Deque<Integer> queue = new ArrayDeque<>(List.of(statuses));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mock-notifications", exchange -> {
            hits.incrementAndGet();
            int status = queue.size() > 1 ? queue.poll() : queue.peek();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void successIsSentInOneCall() throws IOException {
        assertEquals(SendResult.SENT, new NotificationGateway(stub(202)).send(REQUEST));
        assertEquals(1, hits.get());
    }

    @Test
    void clientErrorIsPermanent() throws IOException {
        assertEquals(SendResult.PERMANENT, new NotificationGateway(stub(400)).send(REQUEST));
        assertEquals(1, hits.get());
    }

    @Test
    void serverErrorIsRetryable() throws IOException {
        assertEquals(SendResult.RETRYABLE, new NotificationGateway(stub(503)).send(REQUEST));
    }

    @Test
    void refusedConnectionIsRetryable() {
        assertEquals(SendResult.RETRYABLE, new NotificationGateway("http://127.0.0.1:1").send(REQUEST));
    }
}
