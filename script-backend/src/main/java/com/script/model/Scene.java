package com.script.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一场戏：场景标题、场景描述（舞台提示）与若干句台词。 */
public final class Scene {
    public String id;
    public String heading = "";
    public String description = "";
    public List<Line> lines = new ArrayList<>();

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("heading", heading);
        map.put("description", description);
        List<Object> lineList = new ArrayList<>();
        for (Line line : lines) {
            lineList.add(line.toMap());
        }
        map.put("lines", lineList);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Scene fromMap(Map<String, Object> map) {
        Scene scene = new Scene();
        scene.id = Line.str(map.get("id"));
        scene.heading = Line.str(map.get("heading"));
        scene.description = Line.str(map.get("description"));
        Object lines = map.get("lines");
        if (lines instanceof List) {
            for (Object item : (List<Object>) lines) {
                if (item instanceof Map) {
                    scene.lines.add(Line.fromMap((Map<String, Object>) item));
                }
            }
        }
        return scene;
    }
}
