package com.scriptmanager.api;

import com.scriptmanager.http.ApiError;
import com.scriptmanager.http.Json;
import com.scriptmanager.store.ScriptStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST 接口层：剧本 / 场次 / 台词的增删改查与排序。
 *
 * GET    /api/scripts                                  剧本摘要列表
 * POST   /api/scripts                                  新建剧本
 * GET    /api/scripts/{id}                             剧本完整详情
 * PUT    /api/scripts/{id}                             更新标题/作者/简介/状态
 * DELETE /api/scripts/{id}                             删除剧本
 * POST   /api/scripts/{id}/scenes                      添加场次
 * PUT    /api/scripts/{id}/scenes/{sceneId}            更新场次
 * DELETE /api/scripts/{id}/scenes/{sceneId}            删除场次
 * POST   /api/scripts/{id}/scenes/reorder              场次排序 {sceneIds:[...]}
 * POST   /api/scripts/{id}/scenes/{sceneId}/lines      添加台词
 * PUT    /api/scripts/{id}/scenes/{sceneId}/lines/{lineId}   更新台词
 * DELETE /api/scripts/{id}/scenes/{sceneId}/lines/{lineId}   删除台词
 * POST   /api/scripts/{id}/scenes/{sceneId}/lines/reorder    台词排序 {lineIds:[...]}
 */
public class ScriptApi implements HttpHandler {

    private static final Set<String> STATUSES = Set.of("DRAFT", "REVISING", "FINAL");

    private final ScriptStore store;

    public ScriptApi(ScriptStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // 允许前端从任意本地源（静态服务器 / file://）跨域访问
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        try {
            route(ex);
        } catch (ApiError e) {
            send(ex, e.status, errorBody(e.getMessage()));
        } catch (IllegalArgumentException e) {
            send(ex, 400, errorBody(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, errorBody("服务器内部错误"));
        } finally {
            ex.close();
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();
        String path = ex.getRequestURI().getPath();
        String[] seg = path.substring("/api/".length()).split("/");

        if (seg.length >= 1 && seg[0].equals("scripts")) {
            if (seg.length == 1) {
                if ("GET".equals(method)) { send(ex, 200, store.list()); return; }
                if ("POST".equals(method)) { createScript(ex); return; }
            } else {
                String scriptId = seg[1];
                if (seg.length == 2) {
                    if ("GET".equals(method)) { send(ex, 200, mustGet(scriptId)); return; }
                    if ("PUT".equals(method)) { updateScript(ex, scriptId); return; }
                    if ("DELETE".equals(method)) { deleteScript(ex, scriptId); return; }
                } else if ("scenes".equals(seg[2])) {
                    if (seg.length == 3 && "POST".equals(method)) { addScene(ex, scriptId); return; }
                    if (seg.length == 4) {
                        if ("reorder".equals(seg[3]) && "POST".equals(method)) { reorderScenes(ex, scriptId); return; }
                        if ("PUT".equals(method)) { updateScene(ex, scriptId, seg[3]); return; }
                        if ("DELETE".equals(method)) { deleteScene(ex, scriptId, seg[3]); return; }
                    }
                    if (seg.length >= 5 && "lines".equals(seg[4])) {
                        String sceneId = seg[3];
                        if (seg.length == 5 && "POST".equals(method)) { addLine(ex, scriptId, sceneId); return; }
                        if (seg.length == 6) {
                            if ("reorder".equals(seg[5]) && "POST".equals(method)) { reorderLines(ex, scriptId, sceneId); return; }
                            if ("PUT".equals(method)) { updateLine(ex, scriptId, sceneId, seg[5]); return; }
                            if ("DELETE".equals(method)) { deleteLine(ex, scriptId, sceneId, seg[5]); return; }
                        }
                    }
                }
            }
        }
        throw ApiError.notFound("接口不存在: " + method + " " + path);
    }

    // ---------------- 剧本 ----------------

    private void createScript(HttpExchange ex) throws IOException {
        Map<String, Object> body = body(ex);
        String title = str(body, "title", "").trim();
        if (title.isEmpty()) {
            throw ApiError.badRequest("标题不能为空");
        }
        Map<String, Object> created = store.create(title, str(body, "author", ""), str(body, "synopsis", ""));
        send(ex, 201, created);
    }

    private void updateScript(HttpExchange ex, String scriptId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        Map<String, Object> body = body(ex);
        if (body.containsKey("title")) {
            String title = str(body, "title", "").trim();
            if (title.isEmpty()) {
                throw ApiError.badRequest("标题不能为空");
            }
            script.put("title", title);
        }
        if (body.containsKey("author")) {
            script.put("author", str(body, "author", ""));
        }
        if (body.containsKey("synopsis")) {
            script.put("synopsis", str(body, "synopsis", ""));
        }
        if (body.containsKey("status")) {
            String status = str(body, "status", "");
            if (!STATUSES.contains(status)) {
                throw ApiError.badRequest("非法状态: " + status + "（可选：DRAFT/REVISING/FINAL）");
            }
            script.put("status", status);
        }
        store.save(script);
        send(ex, 200, script);
    }

    private void deleteScript(HttpExchange ex, String scriptId) throws IOException {
        if (!store.delete(scriptId)) {
            throw ApiError.notFound("剧本不存在: " + scriptId);
        }
        send(ex, 200, ok());
    }

    // ---------------- 场次 ----------------

    private void addScene(HttpExchange ex, String scriptId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        Map<String, Object> body = body(ex);
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("id", ScriptStore.newId());
        scene.put("heading", str(body, "heading", "新场次"));
        scene.put("description", str(body, "description", ""));
        scene.put("lines", new ArrayList<>());
        scenes(script).add(scene);
        store.save(script);
        send(ex, 201, scene);
    }

    private void updateScene(HttpExchange ex, String scriptId, String sceneId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        Map<String, Object> scene = mustScene(script, sceneId);
        Map<String, Object> body = body(ex);
        if (body.containsKey("heading")) {
            scene.put("heading", str(body, "heading", ""));
        }
        if (body.containsKey("description")) {
            scene.put("description", str(body, "description", ""));
        }
        store.save(script);
        send(ex, 200, scene);
    }

    private void deleteScene(HttpExchange ex, String scriptId, String sceneId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        Map<String, Object> scene = mustScene(script, sceneId);
        scenes(script).remove(scene);
        store.save(script);
        send(ex, 200, ok());
    }

    private void reorderScenes(HttpExchange ex, String scriptId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        List<String> order = strList(body(ex).get("sceneIds"));
        List<Map<String, Object>> scenes = scenes(script);
        scenes.sort(Comparator.comparingInt(s -> orderIndex(order, s.get("id"))));
        store.save(script);
        send(ex, 200, script);
    }

    // ---------------- 台词 ----------------

    private void addLine(HttpExchange ex, String scriptId, String sceneId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        Map<String, Object> scene = mustScene(script, sceneId);
        Map<String, Object> body = body(ex);
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", ScriptStore.newId());
        line.put("character", str(body, "character", ""));
        line.put("parenthetical", str(body, "parenthetical", ""));
        line.put("text", str(body, "text", ""));
        lines(scene).add(line);
        store.save(script);
        send(ex, 201, line);
    }

    private void updateLine(HttpExchange ex, String scriptId, String sceneId, String lineId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        Map<String, Object> line = mustLine(mustScene(script, sceneId), lineId);
        Map<String, Object> body = body(ex);
        if (body.containsKey("character")) {
            line.put("character", str(body, "character", ""));
        }
        if (body.containsKey("parenthetical")) {
            line.put("parenthetical", str(body, "parenthetical", ""));
        }
        if (body.containsKey("text")) {
            line.put("text", str(body, "text", ""));
        }
        store.save(script);
        send(ex, 200, line);
    }

    private void deleteLine(HttpExchange ex, String scriptId, String sceneId, String lineId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        Map<String, Object> scene = mustScene(script, sceneId);
        lines(scene).remove(mustLine(scene, lineId));
        store.save(script);
        send(ex, 200, ok());
    }

    private void reorderLines(HttpExchange ex, String scriptId, String sceneId) throws IOException {
        Map<String, Object> script = mustGet(scriptId);
        Map<String, Object> scene = mustScene(script, sceneId);
        List<String> order = strList(body(ex).get("lineIds"));
        List<Map<String, Object>> lines = lines(scene);
        lines.sort(Comparator.comparingInt(l -> orderIndex(order, l.get("id"))));
        store.save(script);
        send(ex, 200, script);
    }

    // ---------------- 工具方法 ----------------

    private Map<String, Object> mustGet(String scriptId) {
        return store.get(scriptId).orElseThrow(() -> ApiError.notFound("剧本不存在: " + scriptId));
    }

    private Map<String, Object> mustScene(Map<String, Object> script, String sceneId) {
        for (Map<String, Object> s : scenes(script)) {
            if (sceneId.equals(s.get("id"))) {
                return s;
            }
        }
        throw ApiError.notFound("场次不存在: " + sceneId);
    }

    private Map<String, Object> mustLine(Map<String, Object> scene, String lineId) {
        for (Map<String, Object> l : lines(scene)) {
            if (lineId.equals(l.get("id"))) {
                return l;
            }
        }
        throw ApiError.notFound("台词不存在: " + lineId);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> scenes(Map<String, Object> script) {
        return (List<Map<String, Object>>) script.computeIfAbsent("scenes", k -> new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lines(Map<String, Object> scene) {
        return (List<Map<String, Object>>) scene.computeIfAbsent("lines", k -> new ArrayList<>());
    }

    private static int orderIndex(List<String> order, Object id) {
        int i = order.indexOf(String.valueOf(id));
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static String str(Map<String, Object> body, String key, String def) {
        Object v = body.get(key);
        return v == null ? def : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object o : (List<Object>) v) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private Map<String, Object> body(HttpExchange ex) throws IOException {
        String text;
        try (InputStream in = ex.getRequestBody()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (text.isBlank()) {
            return new LinkedHashMap<>();
        }
        return Json.parseObject(text);
    }

    private void send(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }
}
