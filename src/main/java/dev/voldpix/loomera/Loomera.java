package dev.voldpix.loomera;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

public class Loomera {

    private HttpServer server;
    private int port = 8080;
    private String host = "0.0.0.0";

    private final Map<String, List<Route>> routes = new HashMap<>();
    private final Set<String> registeredRoutes = new HashSet<>();

    private final Map<Class<? extends Exception>, ExceptionHandler<?>> exceptionHandlers = new HashMap<>();

    // server
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", this::handle);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void stop() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    // configs
    public Loomera port(int port) {
        this.port = port;
        return this;
    }

    public Loomera host(String host) {
        this.host = host;
        return this;
    }

    // exceptions
    public <T extends Exception> void exception(Class<T> exceptionClass, ExceptionHandler<T> handler) {
        exceptionHandlers.put(exceptionClass, handler);
    }

    // routes
    public void get(String path, Handler handler) {
        route("GET", path, handler);
    }

    public void post(String path, Handler handler) {
        route("POST", path, handler);
    }

    public void put(String path, Handler handler) {
        route("PUT", path, handler);
    }

    public void patch(String path, Handler handler) {
        route("PATCH", path, handler);
    }

    public void delete(String path, Handler handler) {
        route("DELETE", path, handler);
    }

    private void route(String method, String path, Handler handler) {
        if (!path.startsWith("/")) path = "/".concat(path);
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        var isWildcardPath = false;
        var containsWildcard = path.contains("*");
        if (containsWildcard) {
            if (path.endsWith("*")) isWildcardPath = true;
            else throw new IllegalArgumentException("wildcard path must end with '*'");
        }
        var routeKey = method + ":" + path;
        if (registeredRoutes.contains(routeKey)) {
            throw new IllegalStateException("Duplicate route: " + method + " " + path);
        }
        registeredRoutes.add(routeKey);
        routes.computeIfAbsent(method, k -> new ArrayList<>())
                .add(new Route(method, path, isWildcardPath, handler));
    }

    // request handler
    private void handle(HttpExchange exchange) {
        var method = exchange.getRequestMethod().toUpperCase();
        var path = exchange.getRequestURI().getPath();
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        RequestContext context = null;
        try (exchange) {
            var matchedRoute = findMatchingRoute(method, path);
            if (matchedRoute == null) {
                send404(exchange);
                return;
            }

            context = new RequestContext(exchange, matchedRoute.wildcardPath());
            matchedRoute.route().handler().handle(context);

            if (!context.isResponseSent()) {
                exchange.sendResponseHeaders(204, -1);
            }
        } catch (Exception e) {
            handleException(exchange, context, e);
        }
    }

    private RouteMatch findMatchingRoute(String method, String path) {
        var methodRoutes = routes.get(method);
        if (Objects.isNull(methodRoutes) || methodRoutes.isEmpty()) return null;

        var exactMatch = findExactMatch(methodRoutes, path);
        if (Objects.nonNull(exactMatch)) return exactMatch;
        return findWildcardMatch(methodRoutes, path);
    }

    private RouteMatch findExactMatch(List<Route> routes, String path) {
        for (var route : routes) {
            if (!route.isWildcard() && route.path().equals(path)) return new RouteMatch(route, null);
        }
        return null;
    }

    private RouteMatch findWildcardMatch(List<Route> routes, String path) {
        for (var route : routes) {
            if (route.isWildcard()) {
                var routePrefix = route.path().substring(0, route.path().length() - 1);
                if (path.startsWith(routePrefix)) {
                    var wildcardPath = extractWildcardPath(path, routePrefix);
                    return new RouteMatch(route, wildcardPath);
                }
            }
        }
        return null;
    }

    private String extractWildcardPath(String path, String prefix) {
        var wildcardPath = path.substring(prefix.length());
        if (!wildcardPath.isEmpty() && !wildcardPath.startsWith("/")) {
            return "/".concat(wildcardPath);
        }
        return wildcardPath;
    }

    // exceptions
    @SuppressWarnings("unchecked")
    private void handleException(HttpExchange exchange, RequestContext ctx, Exception exception) {
        var exceptionClass = exception.getClass();
        ExceptionHandler<Exception> handler = null;

        if (exceptionHandlers.containsKey(exceptionClass)) {
            handler = (ExceptionHandler<Exception>) exceptionHandlers.get(exceptionClass);
        } else {
            for (var entry : exceptionHandlers.entrySet()) {
                if (entry.getKey().isAssignableFrom(exceptionClass)) {
                    handler = (ExceptionHandler<Exception>) entry.getValue();
                    break;
                }
            }
        }

        if (Objects.nonNull(handler) && Objects.nonNull(ctx)) {
            try {
                handler.handle(ctx, exception);
                if (!ctx.isResponseSent()) sendError(exchange, exception);
            } catch (Exception handlerException) {
                handlerException.printStackTrace();
                sendError(exchange, exception);
            }
        } else sendError(exchange, exception);
    }

    private void send404(HttpExchange exchange) throws IOException {
        var response = "{\"error\":\"Not Found\"}";
        byte[] bytes = response.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(404, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void sendError(HttpExchange exchange, Exception e) {
        try {
            e.printStackTrace();
            var message = Objects.nonNull(e.getMessage()) ? e.getMessage() : "Internal Server Error";
            var response = "{\"error\":\"" + escapeJson(message) + "\"}";
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // noop
        }
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record Route(String method, String path, boolean isWildcard, Handler handler) {}
    private record RouteMatch(Route route, String wildcardPath) {}
}
