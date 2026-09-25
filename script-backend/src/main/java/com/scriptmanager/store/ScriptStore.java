package com.scriptmanager.store;

import com.scriptmanager.http.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于本地 JSON 文件的剧本存储。
 * 每个剧本保存为 data/scripts/{id}.json，内容为完整聚合（剧本 + 场次 + 台词）。
 */
public class ScriptStore {

    private final Path dir;

    public ScriptStore(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建数据目录: " + dir, e);
        }
    }

    /** 剧本摘要列表（不含场次明细），按更新时间倒序。 */
    public synchronized List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(f -> f.toString().endsWith(".json")).collect(Collectors.toList())) {
                try {
                    Map<String, Object> script = read(p);
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("id", script.get("id"));
                    summary.put("title", script.get("title"));
                    summary.put("author", script.get("author"));
                    summary.put("status", script.get("status"));
                    summary.put("createdAt", script.get("createdAt"));
                    summary.put("updatedAt", script.get("updatedAt"));
                    Object scenes = script.get("scenes");
                    summary.put("sceneCount", scenes instanceof List ? ((List<?>) scenes).size() : 0);
                    result.add(summary);
                } catch (Exception e) {
                    System.err.println("跳过损坏的数据文件: " + p + " -> " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取数据目录失败: " + dir, e);
        }
        result.sort((a, b) -> String.valueOf(b.get("updatedAt")).compareTo(String.valueOf(a.get("updatedAt"))));
        return result;
    }

    /** 读取完整剧本（含场次与台词）。 */
    public synchronized Optional<Map<String, Object>> get(String id) {
        Path p = file(id);
        if (!Files.exists(p)) {
            return Optional.empty();
        }
        return Optional.of(read(p));
    }

    /** 新建剧本，初始状态为草稿（DRAFT）。 */
    public synchronized Map<String, Object> create(String title, String author, String synopsis) {
        String now = now();
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("id", newId());
        script.put("title", title);
        script.put("author", author);
        script.put("synopsis", synopsis);
        script.put("status", "DRAFT");
        script.put("createdAt", now);
        script.put("updatedAt", now);
        script.put("scenes", new ArrayList<>());
        save(script);
        return script;
    }

    /** 保存剧本（自动刷新 updatedAt），先写临时文件再原子替换。 */
    public synchronized void save(Map<String, Object> script) {
        script.put("updatedAt", now());
        Path p = file(String.valueOf(script.get("id")));
        try {
            Path tmp = dir.resolve(p.getFileName() + ".tmp");
            Files.write(tmp, Json.stringify(script).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("写入数据文件失败: " + p, e);
        }
    }

    public synchronized boolean delete(String id) {
        try {
            return Files.deleteIfExists(file(id));
        } catch (IOException e) {
            throw new UncheckedIOException("删除数据文件失败: " + id, e);
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private Path file(String id) {
        if (!id.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("非法 id: " + id);
        }
        return dir.resolve(id + ".json");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(Path p) {
        try {
            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            return (Map<String, Object>) Json.parse(text);
        } catch (IOException e) {
            throw new UncheckedIOException("读取数据文件失败: " + p, e);
        }
    }
}
