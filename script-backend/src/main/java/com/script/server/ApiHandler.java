package com.script.server;

import com.script.model.Line;
import com.script.model.Scene;
import com.script.model.Script;
import com.script.store.ScriptStore;
import com.script.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧本 REST 接口处理器（路径前缀 /api）。
 *
 * 剧本：
 *   GET    /api/scripts
 *   POST   /api/scripts
 *   GET    /api/scripts/{id}
 *   PUT    /api/scripts/{id}
 *   PUT    /api/scripts/{id}/status
 *   DELETE /api/scripts/{id}
 *
 * 场次：
 *   POST   /api/scripts/{id}/scenes
 *   PUT    /api/scripts/{id}/scenes/{sceneId}
 *   DELETE /api/scripts/{id}/scenes/{sceneId}
 *   POST   /api/scripts/{id}/scenes/{sceneId}/move
 *
 * 台词：
 *   POST   /api/scripts/{id}/scenes/{sceneId}/lines
 *   PUT    /api/scripts/{id}/scenes/{sceneId}/lines/{lineId}
 *   DELETE /api/scripts/{id}/scenes/{sceneId}/lines/{lineId}
 *   POST   /api/scripts/{id}/scenes/{sceneId}/lines/{lineId}/move
 */
public final class ApiHandler implements HttpHandler {

    private final ScriptStore store;

    public ApiHandler(ScriptStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NO_CONTENT, -1);
                return;
            }
            route(exchange);
        } catch (NotFoundException e) {
            sendJson(exchange, HttpURLConnection.HTTP_NOT_FOUND, error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, HttpURLConnection.HTTP_BAD_REQUEST, error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, error("服务器内部错误: " + e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    // ------------------------------------------------------------------
    // 路由
    // ------------------------------------------------------------------

    private void route(HttpExchange ex) throws Exception {
        String method = ex.getRequestMethod().toUpperCase();
        String path = ex.getRequestURI().getPath();
        String[] seg = path.replaceFirst("^/api/?", "").split("/");

        if (seg.length < 1 || !"scripts".equals(seg[0])) {
            throw new NotFoundException("未知接口: " + path);
        }

        if (seg.length == 1) {
            if ("GET".equals(method)) {
                listScripts(ex);
            } else if ("POST".equals(method)) {
                createScript(ex);
            } else {
                throw new NotFoundException(method + " /api/scripts 不支持");
            }
            return;
        }

        String scriptId = seg[1];

        if (seg.length == 2) {
            if ("GET".equals(method)) {
                getScript(ex, scriptId);
            } else if ("PUT".equals(method)) {
                updateScript(ex, scriptId);
            } else if ("DELETE".equals(method)) {
                deleteScript(ex, scriptId);
            } else {
                throw new NotFoundException("不支持的操作");
            }
            return;
        }

        if (seg.length == 3 && "status".equals(seg[2])) {
            if ("PUT".equals(method)) {
                updateStatus(ex, scriptId);
            } else {
                throw new NotFoundException("不支持的操作");
            }
            return;
        }

        if ("scenes".equals(seg[2])) {
            routeScenes(ex, method, scriptId, seg);
            return;
        }

        throw new NotFoundException("未知接口: " + path);
    }

    private void routeScenes(HttpExchange ex, String method, String scriptId, String[] seg)
            throws Exception {
        if (seg.length == 3) {
            if ("POST".equals(method)) {
                addScene(ex, scriptId);
                return;
            }
            throw new NotFoundException("不支持的操作");
        }

        String sceneId = seg[3];

        if (seg.length == 4) {
            if ("PUT".equals(method)) {
                updateScene(ex, scriptId, sceneId);
            } else if ("DELETE".equals(method)) {
                deleteScene(ex, scriptId, sceneId);
            } else {
                throw new NotFoundException("不支持的操作");
            }
            return;
        }

        if (seg.length == 5 && "move".equals(seg[4])) {
            if ("POST".equals(method)) {
                moveScene(ex, scriptId, sceneId);
            } else {
                throw new NotFoundException("不支持的操作");
            }
            return;
        }

        if (seg.length >= 5 && "lines".equals(seg[4])) {
            routeLines(ex, method, scriptId, sceneId, seg);
            return;
        }

        throw new NotFoundException("未知接口");
    }

    private void routeLines(HttpExchange ex, String method, String scriptId, String sceneId,
                            String[] seg) throws Exception {
        if (seg.length == 5) {
            if ("POST".equals(method)) {
                addLine(ex, scriptId, sceneId);
                return;
            }
            throw new NotFoundException("不支持的操作");
        }

        String lineId = seg[5];

        if (seg.length == 6) {
            if ("PUT".equals(method)) {
                updateLine(ex, scriptId, sceneId, lineId);
            } else if ("DELETE".equals(method)) {
                deleteLine(ex, scriptId, sceneId, lineId);
            } else {
                throw new NotFoundException("不支持的操作");
            }
            return;
        }

        if (seg.length == 7 && "move".equals(seg[6]) && "POST".equals(method)) {
            moveLine(ex, scriptId, sceneId, lineId);
            return;
        }

        throw new NotFoundException("未知接口");
    }

    // ------------------------------------------------------------------
    // 剧本接口
    // ------------------------------------------------------------------

    private void listScripts(HttpExchange ex) throws IOException {
        List<Object> result = new ArrayList<>();
        for (Script script : store.list()) {
            result.add(script.toSummary());
        }
        sendJson(ex, 200, result);
    }

    private void createScript(HttpExchange ex) throws IOException {
        Map<String, Object> body = readBody(ex);
        Script script = new Script();
        script.id = ScriptStore.newId();
        script.title = requiredText(body.get("title"), "剧名不能为空");
        script.author = text(body.get("author"), "");
        script.synopsis = text(body.get("synopsis"), "");
        script.status = validStatus(text(body.get("status"), Script.STATUS_DRAFT));
        store.save(script);
        sendJson(ex, 201, script.toMap());
    }

    private void getScript(HttpExchange ex, String id) throws IOException {
        sendJson(ex, 200, mustGet(id).toMap());
    }

    private void updateScript(HttpExchange ex, String id) throws IOException {
        Script script = mustGet(id);
        Map<String, Object> body = readBody(ex);
        if (body.containsKey("title")) {
            script.title = requiredText(body.get("title"), "剧名不能为空");
        }
        if (body.containsKey("author")) {
            script.author = text(body.get("author"), "");
        }
        if (body.containsKey("synopsis")) {
            script.synopsis = text(body.get("synopsis"), "");
        }
        if (body.containsKey("status")) {
            script.status = validStatus(text(body.get("status"), script.status));
        }
        store.save(script);
        sendJson(ex, 200, script.toMap());
    }

    private void updateStatus(HttpExchange ex, String id) throws IOException {
        Script script = mustGet(id);
        Map<String, Object> body = readBody(ex);
        script.status = validStatus(requiredText(body.get("status"), "status 不能为空"));
        store.save(script);
        sendJson(ex, 200, script.toSummary());
    }

    private void deleteScript(HttpExchange ex, String id) throws IOException {
        if (!store.delete(id)) {
            throw new NotFoundException("剧本不存在: " + id);
        }
        sendJson(ex, 200, ok());
    }

    // ------------------------------------------------------------------
    // 场次接口
    // ------------------------------------------------------------------

    private void addScene(HttpExchange ex, String scriptId) throws IOException {
        Script script = mustGet(scriptId);
        Map<String, Object> body = readBody(ex);
        Scene scene = new Scene();
        scene.id = ScriptStore.newId();
        scene.heading = text(body.get("heading"), "新场景");
        scene.description = text(body.get("description"), "");
        script.scenes.add(scene);
        store.save(script);
        sendJson(ex, 201, scene.toMap());
    }

    private void updateScene(HttpExchange ex, String scriptId, String sceneId) throws IOException {
        Script script = mustGet(scriptId);
        Scene scene = mustFindScene(script, sceneId);
        Map<String, Object> body = readBody(ex);
        if (body.containsKey("heading")) {
            scene.heading = text(body.get("heading"), "");
        }
        if (body.containsKey("description")) {
            scene.description = text(body.get("description"), "");
        }
        store.save(script);
        sendJson(ex, 200, scene.toMap());
    }

    private void deleteScene(HttpExchange ex, String scriptId, String sceneId) throws IOException {
        Script script = mustGet(scriptId);
        if (!script.scenes.removeIf(s -> sceneId.equals(s.id))) {
            throw new NotFoundException("场次不存在: " + sceneId);
        }
        store.save(script);
        sendJson(ex, 200, ok());
    }

    private void moveScene(HttpExchange ex, String scriptId, String sceneId) throws IOException {
        Script script = mustGet(scriptId);
        int index = indexOfScene(script, sceneId);
        if (index < 0) {
            throw new NotFoundException("场次不存在: " + sceneId);
        }
        int target = targetIndex(readBody(ex), index, script.scenes.size());
        if (target != index) {
            Collections.swap(script.scenes, index, target);
            store.save(script);
        }
        sendJson(ex, 200, ok());
    }

    // ------------------------------------------------------------------
    // 台词接口
    // ------------------------------------------------------------------

    private void addLine(HttpExchange ex, String scriptId, String sceneId) throws IOException {
        Script script = mustGet(scriptId);
        Scene scene = mustFindScene(script, sceneId);
        Map<String, Object> body = readBody(ex);
        Line line = new Line();
        line.id = ScriptStore.newId();
        line.character = requiredText(body.get("character"), "角色名不能为空");
        line.text = requiredText(body.get("text"), "台词内容不能为空");
        line.note = text(body.get("note"), "");
        scene.lines.add(line);
        store.save(script);
        sendJson(ex, 201, line.toMap());
    }

    private void updateLine(HttpExchange ex, String scriptId, String sceneId, String lineId)
            throws IOException {
        Script script = mustGet(scriptId);
        Scene scene = mustFindScene(script, sceneId);
        Line line = mustFindLine(scene, lineId);
        Map<String, Object> body = readBody(ex);
        if (body.containsKey("character")) {
            line.character = text(body.get("character"), "");
        }
        if (body.containsKey("text")) {
            line.text = text(body.get("text"), "");
        }
        if (body.containsKey("note")) {
            line.note = text(body.get("note"), "");
        }
        store.save(script);
        sendJson(ex, 200, line.toMap());
    }

    private void deleteLine(HttpExchange ex, String scriptId, String sceneId, String lineId)
            throws IOException {
        Script script = mustGet(scriptId);
        Scene scene = mustFindScene(script, sceneId);
        if (!scene.lines.removeIf(l -> lineId.equals(l.id))) {
            throw new NotFoundException("台词不存在: " + lineId);
        }
        store.save(script);
        sendJson(ex, 200, ok());
    }

    private void moveLine(HttpExchange ex, String scriptId, String sceneId, String lineId)
            throws IOException {
        Script script = mustGet(scriptId);
        Scene scene = mustFindScene(script, sceneId);
        int index = indexOfLine(scene, lineId);
        if (index < 0) {
            throw new NotFoundException("台词不存在: " + lineId);
        }
        int target = targetIndex(readBody(ex), index, scene.lines.size());
        if (target != index) {
            Collections.swap(scene.lines, index, target);
            store.save(script);
        }
        sendJson(ex, 200, ok());
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private Script mustGet(String id) throws IOException {
        Script script = store.get(id);
        if (script == null) {
            throw new NotFoundException("剧本不存在: " + id);
        }
        return script;
    }

    private static Scene mustFindScene(Script script, String sceneId) {
        for (Scene scene : script.scenes) {
            if (sceneId.equals(scene.id)) {
                return scene;
            }
        }
        throw new NotFoundException("场次不存在: " + sceneId);
    }

    private static Line mustFindLine(Scene scene, String lineId) {
        for (Line line : scene.lines) {
            if (lineId.equals(line.id)) {
                return line;
            }
        }
        throw new NotFoundException("台词不存在: " + lineId);
    }

    private static int indexOfScene(Script script, String sceneId) {
        for (int i = 0; i < script.scenes.size(); i++) {
            if (sceneId.equals(script.scenes.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfLine(Scene scene, String lineId) {
        for (int i = 0; i < scene.lines.size(); i++) {
            if (lineId.equals(scene.lines.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    /** 根据请求体 { "direction": "up" | "down" } 计算交换目标下标。 */
    private static int targetIndex(Map<String, Object> body, int current, int size) {
        String direction = text(body.get("direction"), "");
        int target;
        if ("up".equals(direction)) {
            target = current - 1;
        } else if ("down".equals(direction)) {
            target = current + 1;
        } else {
            throw new IllegalArgumentException("direction 只能是 up 或 down");
        }
        if (target < 0 || target >= size) {
            return current; // 已经在边界，视为成功但不移动
        }
        return target;
    }

    private static String validStatus(String status) {
        if (!Script.STATUSES.contains(status)) {
            throw new IllegalArgumentException("无效的草稿状态: " + status
                    + "（可选 DRAFT / REVISING / FINAL）");
        }
        return status;
    }

    private Map<String, Object> readBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return new LinkedHashMap<>();
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return Json.parseObject(text);
    }

    private void sendJson(HttpExchange ex, int statusCode, Object body) throws IOException {
        byte[] data = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("error", message);
        return map;
    }

    private static Map<String, Object> ok() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        return map;
    }

    private static String text(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? defaultValue : s;
    }

    private static String requiredText(Object value, String message) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return String.valueOf(value).trim();
    }

    /** 404 异常，由统一异常处理转为 HTTP 404。 */
    private static final class NotFoundException extends RuntimeException {
        NotFoundException(String message) {
            super(message);
        }
    }
}
