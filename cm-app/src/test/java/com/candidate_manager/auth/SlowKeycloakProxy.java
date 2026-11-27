package com.candidate_manager.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SlowKeycloakProxy implements AutoCloseable {

    private static final Set<String> HEADERS_SET_BY_HTTP_CLIENT =
            Set.of("host", "content-length", "connection", "upgrade", "expect", "transfer-encoding");

    private final String keycloakUrl;
    private final Duration delay;
    private final HttpServer server;
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final CountDownLatch userCreationAnswered = new CountDownLatch(1);
    private volatile SlowRequest slowRequest = SlowRequest.NONE;
    private volatile int userCreationStatus;

    public SlowKeycloakProxy(String keycloakUrl, Duration delay) throws IOException {
        this.keycloakUrl = keycloakUrl;
        this.delay = delay;
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.server.createContext("/", this::forward);
        this.server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void answerUserCreationLate() {
        slowRequest = SlowRequest.ANSWER_USER_CREATION_LATE;
    }

    public void forwardUserCreationLate() {
        slowRequest = SlowRequest.FORWARD_USER_CREATION_LATE;
    }

    public int awaitUserCreationStatus() throws InterruptedException {
        if (!userCreationAnswered.await(delay.multipliedBy(5).toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Keycloak did not answer the user creation in time");
        }
        return userCreationStatus;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void forward(HttpExchange exchange) throws IOException {
        try (exchange) {
            boolean userCreation = isUserCreation(exchange);
            if (userCreation && slowRequest == SlowRequest.FORWARD_USER_CREATION_LATE) {
                pause();
            }
            HttpResponse<byte[]> response = httpClient.send(keycloakRequestFor(exchange), HttpResponse.BodyHandlers.ofByteArray());
            if (userCreation) {
                userCreationStatus = response.statusCode();
                userCreationAnswered.countDown();
            }
            if (userCreation && slowRequest == SlowRequest.ANSWER_USER_CREATION_LATE) {
                pause();
            }
            response.headers().map().forEach((name, values) -> {
                if (!HEADERS_SET_BY_HTTP_CLIENT.contains(name.toLowerCase())) {
                    exchange.getResponseHeaders().put(name, values);
                }
            });
            byte[] body = response.body();
            exchange.sendResponseHeaders(response.statusCode(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException clientAlreadyGaveUp) {
            return;
        }
    }

    private HttpRequest keycloakRequestFor(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream input = exchange.getRequestBody()) {
            body = input.readAllBytes();
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(keycloakUrl + exchange.getRequestURI()))
                .method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(body));
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!HEADERS_SET_BY_HTTP_CLIENT.contains(name.toLowerCase())) {
                values.forEach(value -> request.header(name, value));
            }
        });
        return request.build();
    }

    private void pause() throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    private static boolean isUserCreation(HttpExchange exchange) {
        return exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().endsWith("/users");
    }

    private enum SlowRequest {
        NONE,
        ANSWER_USER_CREATION_LATE,
        FORWARD_USER_CREATION_LATE
    }
}
