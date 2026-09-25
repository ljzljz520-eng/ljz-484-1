package com.script.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 一个剧本：基本信息、草稿状态以及按顺序排列的场次。 */
public final class Script {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_REVISING = "REVISING";
    public static final String STATUS_FINAL = "FINAL";
    public static final Set<String> STATUSES =
            Set.of(STATUS_DRAFT, STATUS_REVISING, STATUS_FINAL);

    public String id;
    public String title = "";
    public String author = "";
    public String synopsis = "";
    public String status = STATUS_DRAFT;
    public long createdAt;
    public long updatedAt;
    public List<Scene> scenes = new ArrayList<>();

    /** 完整数据（详情接口使用）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("title", title);
        map.put("author", author);
        map.put("synopsis", synopsis);
        map.put("status", status);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        List<Object> sceneList = new ArrayList<>();
        int lineCount = 0;
        for (Scene scene : scenes) {
            sceneList.add(scene.toMap());
            lineCount += scene.lines.size();
        }
        map.put("scenes", sceneList);
        map.put("sceneCount", scenes.size());
        map.put("lineCount", lineCount);
        return map;
    }

    /** 摘要数据（列表接口使用）。 */
    public Map<String, Object> toSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("title", title);
        map.put("author", author);
        map.put("synopsis", synopsis);
        map.put("status", status);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("sceneCount", scenes.size());
        int lineCount = 0;
        for (Scene scene : scenes) {
            lineCount += scene.lines.size();
        }
        map.put("lineCount", lineCount);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Script fromMap(Map<String, Object> map) {
        Script script = new Script();
        script.id = Line.str(map.get("id"));
        script.title = Line.str(map.get("title"));
        script.author = Line.str(map.get("author"));
        script.synopsis = Line.str(map.get("synopsis"));
        String status = Line.str(map.get("status"));
        if (!status.isEmpty()) {
            script.status = status;
        }
        Object createdAt = map.get("createdAt");
        if (createdAt instanceof Number) {
            script.createdAt = ((Number) createdAt).longValue();
        }
        Object updatedAt = map.get("updatedAt");
        if (updatedAt instanceof Number) {
            script.updatedAt = ((Number) updatedAt).longValue();
        }
        Object scenes = map.get("scenes");
        if (scenes instanceof List) {
            for (Object item : (List<Object>) scenes) {
                if (item instanceof Map) {
                    script.scenes.add(Scene.fromMap((Map<String, Object>) item));
                }
            }
        }
        return script;
    }
}
