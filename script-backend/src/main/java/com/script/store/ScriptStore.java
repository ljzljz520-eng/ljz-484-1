package com.script.store;

import com.script.model.Script;
import com.script.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 剧本数据存储：每个剧本保存为 data 目录下的一个 JSON 文件。
 * 写入时先写临时文件再原子替换，避免中途崩溃导致文件损坏。
 * 所有方法均加锁，保证本地多线程访问安全。
 */
public final class ScriptStore {
    private static final Pattern VALID_ID = Pattern.compile("[a-zA-Z0-9-]+");

    private final Path dataDir;

    public ScriptStore(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
    }

    /** 生成一个短 ID。 */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** 读取全部剧本，按更新时间倒序排列。 */
    public synchronized List<Script> list() throws IOException {
        List<Script> scripts = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*.json")) {
            for (Path file : stream) {
                try {
                    scripts.add(read(file));
                } catch (Exception e) {
                    System.err.println("跳过无法解析的数据文件: " + file + " (" + e.getMessage() + ")");
                }
            }
        }
        scripts.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return scripts;
    }

    /** 按 ID 读取单个剧本，不存在时返回 null。 */
    public synchronized Script get(String id) throws IOException {
        Path file = fileOf(id);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return read(file);
    }

    /** 保存剧本（新建或覆盖），同时刷新 updatedAt。 */
    public synchronized void save(Script script) throws IOException {
        if (script.id == null || script.id.isBlank()) {
            throw new IllegalArgumentException("剧本 ID 不能为空");
        }
        if (script.createdAt == 0L) {
            script.createdAt = System.currentTimeMillis();
        }
        script.updatedAt = System.currentTimeMillis();
        Path target = fileOf(script.id);
        Path tmp = dataDir.resolve(script.id + ".tmp");
        Files.writeString(tmp, Json.stringify(script.toMap()), StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** 删除剧本，文件不存在时返回 false。 */
    public synchronized boolean delete(String id) throws IOException {
        return Files.deleteIfExists(fileOf(id));
    }

    private Script read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return Script.fromMap(Json.parseObject(text));
    }

    private Path fileOf(String id) {
        if (id == null || !VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("非法的剧本 ID: " + id);
        }
        return dataDir.resolve(id + ".json");
    }
}
