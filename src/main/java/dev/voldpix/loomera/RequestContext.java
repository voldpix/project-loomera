package dev.voldpix.loomera;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RequestContext {

    private final HttpExchange exchange;
    private boolean responseSent = false;

    private Map<String, String> queryParams;
    private String body;
    private int statusCode = 200;
    private final String wildcardPath;

    public RequestContext(HttpExchange exchange, String wildcardPath) {
        this.exchange = exchange;
        this.queryParams = new HashMap<>();
        this.wildcardPath = wildcardPath;
    }

    public String getMethod() {
        return exchange.getRequestMethod();
    }

    public String getPath() {
        return exchange.getRequestURI().getPath();
    }

    public String getWildcardPath() {
        return wildcardPath;
    }

    public RequestContext setStatus(int code) {
        this.statusCode = code;
        return this;
    }

    public RequestContext setContentType(String type) {
        exchange.getResponseHeaders().set("Content-Type", type);
        return this;
    }

    // query params
    public Map<String, String> getQueryParams() {
        ensureQueryParams();
        return Collections.unmodifiableMap(queryParams);
    }

    public String getQueryParam(String name) {
        ensureQueryParams();
        return queryParams.get(name);
    }

    public String setQueryParam(String name, String defaultValue) {
        ensureQueryParams();
        return queryParams.getOrDefault(name, defaultValue);
    }

    private void ensureQueryParams() {
        if (queryParams.isEmpty()) {
            queryParams = new HashMap<>();
            var query = exchange.getRequestURI().getQuery();
            if (Objects.nonNull(query) && !query.isEmpty()) {
                parseQuery(query);
            }
        }
    }

    private void parseQuery(String query) {
        int start = 0;
        int len = query.length();

        while (start < len) {
            int ampIdx = query.indexOf('&', start);
            int end = ampIdx == -1 ? len : ampIdx;

            int eqIdx = query.indexOf('=', start);
            if (eqIdx != -1 && eqIdx < end) {
                String key = query.substring(start, eqIdx);
                String value = query.substring(eqIdx + 1, end);
                queryParams.put(urlDecode(key), urlDecode(value));
            }

            start = end + 1;
        }
    }

    // headers
    public String getHeader(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public RequestContext setHeader(String name, String value) {
        exchange.getResponseHeaders().set(name, value);
        return this;
    }

    // body
    public String getStringBody() {
        if (Objects.isNull(body)) {
            try {
                body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                body = "";
            }
        }
        return body;
    }

    public <T> T jsonBodyAsClass(Class<T> clazz) {
        return LoomeraContext.getJsonProvider().fromJson(getStringBody(), clazz);
    }

    // responses
    public void result(String body) {
        ensureNotSent();
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            responseSent = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to send response", e);
        }
    }

    public void text(String text) {
        ensureNotSent();
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            responseSent = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to send text response", e);
        }
    }

    public void json(Object obj) {
        ensureNotSent();
        try {
            String jsonString = LoomeraContext.getJsonProvider().toJson(obj);
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            responseSent = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to send JSON response", e);
        }
    }

    // helpers
    public boolean isResponseSent() {
        return responseSent;
    }

    private void ensureNotSent() {
        if (responseSent) {
            throw new IllegalStateException("Response already sent");
        }
    }

    private static String urlDecode(String str) {
        try {
            return URLDecoder.decode(str, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return str;
        }
    }
}
