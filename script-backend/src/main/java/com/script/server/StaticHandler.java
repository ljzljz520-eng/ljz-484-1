package com.script.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** 托管 script-frontend 目录下的静态页面，并防止目录穿越。 */
public final class StaticHandler implements HttpHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "ico", "image/x-icon"
    );

    private final Path root;

    public StaticHandler(Path frontendDir) {
        this.root = frontendDir.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                    && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                writePlain(exchange, 405, "405 Method Not Allowed");
                return;
            }

            String requestPath = URLDecoder.decode(exchange.getRequestURI().getRawPath(),
                    StandardCharsets.UTF_8);
            if ("/".equals(requestPath)) {
                requestPath = "/index.html";
            }

            Path file = root.resolve(requestPath.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                writePlain(exchange, 404, "404 Not Found");
                return;
            }

            byte[] data = Files.readAllBytes(file);
            String fileName = file.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
            exchange.getResponseHeaders().set("Content-Type",
                    CONTENT_TYPES.getOrDefault(ext, "application/octet-stream"));
            exchange.sendResponseHeaders(200, data.length);
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            } else {
                exchange.getResponseBody().close();
            }
        } finally {
            exchange.close();
        }
    }

    private static void writePlain(HttpExchange exchange, int statusCode, String message)
            throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
}
